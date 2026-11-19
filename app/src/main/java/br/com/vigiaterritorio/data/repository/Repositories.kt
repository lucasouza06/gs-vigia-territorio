package br.com.vigiaterritorio.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.vigiaterritorio.data.local.DecisionDao
import br.com.vigiaterritorio.data.local.ReadingDao
import br.com.vigiaterritorio.data.local.toDomain
import br.com.vigiaterritorio.data.local.toEntity
import br.com.vigiaterritorio.data.seed.CsvSeeder
import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.Decision
import br.com.vigiaterritorio.domain.model.DecisionRecord
import br.com.vigiaterritorio.domain.model.Reading
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.Thresholds
import br.com.vigiaterritorio.domain.model.UserPreferences
import br.com.vigiaterritorio.domain.repository.DecisionRepository
import br.com.vigiaterritorio.domain.repository.PreferencesRepository
import br.com.vigiaterritorio.domain.repository.ReadingRepository
import br.com.vigiaterritorio.domain.repository.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

class RoomReadingRepository(
    private val dao: ReadingDao,
    private val seeder: CsvSeeder,
    private val prefs: PreferencesRepository,
    private val time: TimeProvider,
) : ReadingRepository {

    private val seedLock = Mutex()

    override fun observeTerritories(): Flow<List<Territory>> =
        dao.observeTerritories().map { list -> list.map { it.toDomain() } }

    override fun observeReadings(territoryId: String): Flow<List<Reading>> =
        dao.observeReadings(territoryId).map { list -> list.map { it.toDomain() } }

    override suspend fun readingsOnce(territoryId: String): List<Reading> =
        dao.readings(territoryId).map { it.toDomain() }

    override suspend fun ensureSeeded() {
        seedLock.withLock {
            val upToDate = prefs.seedVersion() == CsvSeeder.SEED_VERSION && dao.countReadings() > 0
            if (!upToDate) load()
        }
    }

    override suspend fun reseed() {
        seedLock.withLock { load() }
    }

    private suspend fun load(): Unit = withContext(Dispatchers.IO) {
        val anchor = CsvSeeder.anchorFor(time.now())
        val data = seeder.read(anchor)
        dao.replaceAll(data.territories, data.readings)
        prefs.setDataLoadedAt(anchor, CsvSeeder.SEED_VERSION)
    }
}

class RoomDecisionRepository(private val dao: DecisionDao) : DecisionRepository {

    override fun observeHistory(): Flow<List<DecisionRecord>> =
        dao.observeHistory().map { list -> list.map { DecisionRecord(it.decision.toDomain(), it.alert.toDomain()) } }

    override fun observeDecisionFor(alertId: String): Flow<Decision?> =
        dao.observeLatestFor(alertId).map { it?.toDomain() }

    override suspend fun save(alert: Alert, decision: Decision) =
        dao.saveDecision(alert.toEntity(), decision.toEntity())

    override suspend fun clearAll() = dao.clearAll()
}

private val Context.dataStore by preferencesDataStore(name = "vigia_prefs")

class DataStorePreferencesRepository(context: Context) : PreferencesRepository {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val territory = stringPreferencesKey("selected_territory")
        val responsible = stringPreferencesKey("responsible_name")
        val notifications = booleanPreferencesKey("critical_notifications")
        val forecastAttention = doublePreferencesKey("forecast_attention_mm")
        val forecastCritical = doublePreferencesKey("forecast_critical_mm")
        val saturation = doublePreferencesKey("saturation_72h_mm")
        val loadedAt = longPreferencesKey("data_loaded_at")
        val seedVersion = intPreferencesKey("seed_version")
        val notified = stringSetPreferencesKey("notified_alert_ids")
    }

    override val preferences: Flow<UserPreferences> = store.data.map { it.toUserPreferences() }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val defaults = Thresholds()
        return UserPreferences(
            selectedTerritoryId = this[Keys.territory],
            responsibleName = this[Keys.responsible].orEmpty(),
            criticalNotificationsEnabled = this[Keys.notifications] ?: false,
            thresholds = defaults.copy(
                forecastAttentionMm = this[Keys.forecastAttention] ?: defaults.forecastAttentionMm,
                forecastCriticalMm = this[Keys.forecastCritical] ?: defaults.forecastCriticalMm,
                saturation72hMm = this[Keys.saturation] ?: defaults.saturation72hMm,
            ),
            dataLoadedAt = this[Keys.loadedAt]?.let { Instant.ofEpochMilli(it) },
        )
    }

    override suspend fun selectTerritory(id: String) {
        store.edit { it[Keys.territory] = id }
    }

    override suspend fun setResponsibleName(name: String) {
        store.edit { it[Keys.responsible] = name.trim() }
    }

    override suspend fun setCriticalNotificationsEnabled(enabled: Boolean) {
        store.edit { it[Keys.notifications] = enabled }
    }

    override suspend fun setThresholds(thresholds: Thresholds) {
        store.edit {
            it[Keys.forecastAttention] = thresholds.forecastAttentionMm
            it[Keys.forecastCritical] = thresholds.forecastCriticalMm
            it[Keys.saturation] = thresholds.saturation72hMm
        }
    }

    override suspend fun setDataLoadedAt(instant: Instant, seedVersion: Int) {
        store.edit {
            it[Keys.loadedAt] = instant.toEpochMilli()
            it[Keys.seedVersion] = seedVersion
            it.remove(Keys.notified) // nova carga = novos alertas podem ser notificados
        }
    }

    override suspend fun seedVersion(): Int = store.data.first()[Keys.seedVersion] ?: 0

    override suspend fun wasNotified(alertId: String): Boolean =
        alertId in (store.data.first()[Keys.notified] ?: emptySet())

    override suspend fun markNotified(alertId: String) {
        store.edit { it[Keys.notified] = (it[Keys.notified] ?: emptySet()) + alertId }
    }

    /** LGPD: remove o nome do responsável. As decisões são apagadas pelo DecisionRepository. */
    override suspend fun clearPersonalData() {
        store.edit { it.remove(Keys.responsible) }
    }
}
