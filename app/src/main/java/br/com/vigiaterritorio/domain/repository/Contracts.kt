package br.com.vigiaterritorio.domain.repository

import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.Decision
import br.com.vigiaterritorio.domain.model.DecisionRecord
import br.com.vigiaterritorio.domain.model.Reading
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.Thresholds
import br.com.vigiaterritorio.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Contratos que o domínio exige. As implementações vivem na camada de dados/nativo.

interface ReadingRepository {
    fun observeTerritories(): Flow<List<Territory>>
    fun observeReadings(territoryId: String): Flow<List<Reading>>
    suspend fun readingsOnce(territoryId: String): List<Reading>

    /** Carrega a base local se ainda não foi carregada (ou se a versão mudou). Idempotente. */
    suspend fun ensureSeeded()

    /** Refaz a carga da base sintética (ancorada no momento atual). */
    suspend fun reseed()
}

interface DecisionRepository {
    fun observeHistory(): Flow<List<DecisionRecord>>
    fun observeDecisionFor(alertId: String): Flow<Decision?>

    /** Grava o retrato do alerta e a decisão numa única transação. */
    suspend fun save(alert: Alert, decision: Decision)
    suspend fun clearAll()
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun selectTerritory(id: String)
    suspend fun setResponsibleName(name: String)
    suspend fun setCriticalNotificationsEnabled(enabled: Boolean)
    suspend fun setThresholds(thresholds: Thresholds)
    suspend fun setDataLoadedAt(instant: Instant, seedVersion: Int)
    suspend fun seedVersion(): Int
    suspend fun wasNotified(alertId: String): Boolean
    suspend fun markNotified(alertId: String)
    suspend fun clearPersonalData()
}

/** Adaptador do recurso nativo de notificação. A tela nunca chama a API do Android. */
interface AlertNotifier {
    fun canNotify(): Boolean
    fun notifyCritical(alert: Alert)
}

/** Relógio injetável para testes. */
fun interface TimeProvider {
    fun now(): Instant
}
