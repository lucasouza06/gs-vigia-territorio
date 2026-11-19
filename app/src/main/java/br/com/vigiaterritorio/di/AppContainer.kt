package br.com.vigiaterritorio.di

import android.content.Context
import androidx.room.Room
import br.com.vigiaterritorio.data.local.VigiaDatabase
import br.com.vigiaterritorio.data.repository.DataStorePreferencesRepository
import br.com.vigiaterritorio.data.repository.RoomDecisionRepository
import br.com.vigiaterritorio.data.repository.RoomReadingRepository
import br.com.vigiaterritorio.data.seed.CsvSeeder
import br.com.vigiaterritorio.domain.engine.AlertEngine
import br.com.vigiaterritorio.domain.repository.AlertNotifier
import br.com.vigiaterritorio.domain.repository.DecisionRepository
import br.com.vigiaterritorio.domain.repository.PreferencesRepository
import br.com.vigiaterritorio.domain.repository.ReadingRepository
import br.com.vigiaterritorio.domain.repository.TimeProvider
import br.com.vigiaterritorio.domain.usecase.CheckCriticalAlerts
import br.com.vigiaterritorio.domain.usecase.ObserveDashboard
import br.com.vigiaterritorio.domain.usecase.ObserveIndicatorSeries
import br.com.vigiaterritorio.domain.usecase.ObserveTerritoryEvaluation
import br.com.vigiaterritorio.domain.usecase.RegisterDecision
import br.com.vigiaterritorio.platform.AndroidAlertNotifier
import java.time.Instant

/**
 * Injeção de dependência manual (ADR: sem Hilt, pelo tamanho do app).
 * É o único lugar que conhece as implementações concretas.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val time = TimeProvider { Instant.now() }

    private val database: VigiaDatabase =
        Room.databaseBuilder(appContext, VigiaDatabase::class.java, "vigia.db").build()

    val preferences: PreferencesRepository = DataStorePreferencesRepository(appContext)
    val readings: ReadingRepository =
        RoomReadingRepository(database.readingDao(), CsvSeeder(appContext.assets), preferences, time)
    val decisions: DecisionRepository = RoomDecisionRepository(database.decisionDao())
    val notifier: AlertNotifier = AndroidAlertNotifier(appContext)

    private val engine = AlertEngine()

    val observeDashboard = ObserveDashboard(readings, preferences, engine, time)
    val observeTerritoryEvaluation = ObserveTerritoryEvaluation(readings, preferences, engine, time)
    val observeIndicatorSeries = ObserveIndicatorSeries(readings, preferences, time)
    val registerDecision = RegisterDecision(decisions, time)
    val checkCriticalAlerts = CheckCriticalAlerts(readings, preferences, engine, notifier, time)
}
