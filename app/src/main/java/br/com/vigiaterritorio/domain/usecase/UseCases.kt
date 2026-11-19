package br.com.vigiaterritorio.domain.usecase

import br.com.vigiaterritorio.domain.engine.AlertEngine
import br.com.vigiaterritorio.domain.engine.IndicatorCalculator
import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.AlertEvaluation
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Decision
import br.com.vigiaterritorio.domain.model.DecisionAction
import br.com.vigiaterritorio.domain.model.Indicator
import br.com.vigiaterritorio.domain.model.Kpi
import br.com.vigiaterritorio.domain.model.KpiType
import br.com.vigiaterritorio.domain.model.ReadingKind
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.UserPreferences
import br.com.vigiaterritorio.domain.repository.AlertNotifier
import br.com.vigiaterritorio.domain.repository.DecisionRepository
import br.com.vigiaterritorio.domain.repository.PreferencesRepository
import br.com.vigiaterritorio.domain.repository.ReadingRepository
import br.com.vigiaterritorio.domain.repository.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

// ---------- Painel ----------

data class DashboardData(
    val territories: List<Territory>,
    val selected: Territory?,
    val kpis: List<Kpi>,
    val evaluation: AlertEvaluation?,
    val preferences: UserPreferences,
)

class ObserveDashboard(
    private val readings: ReadingRepository,
    private val prefs: PreferencesRepository,
    private val engine: AlertEngine,
    private val time: TimeProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<DashboardData> =
        combine(readings.observeTerritories(), prefs.preferences) { territories, p -> territories to p }
            .flatMapLatest { (territories, p) ->
                val selected = territories.firstOrNull { it.id == p.selectedTerritoryId } ?: territories.firstOrNull()
                if (selected == null) {
                    flowOf(DashboardData(territories, null, emptyList(), null, p))
                } else {
                    readings.observeReadings(selected.id).map { list ->
                        val now = time.now()
                        DashboardData(
                            territories = territories,
                            selected = selected,
                            kpis = IndicatorCalculator.kpis(list, now, p.thresholds),
                            evaluation = engine.evaluate(selected, list, p.thresholds, now),
                            preferences = p,
                        )
                    }
                }
            }
            .flowOn(Dispatchers.Default)
}

// ---------- Detalhe do alerta ----------

data class TerritoryEvaluation(
    val territory: Territory,
    val evaluation: AlertEvaluation,
    val preferences: UserPreferences,
)

class ObserveTerritoryEvaluation(
    private val readings: ReadingRepository,
    private val prefs: PreferencesRepository,
    private val engine: AlertEngine,
    private val time: TimeProvider,
) {
    operator fun invoke(territoryId: String): Flow<TerritoryEvaluation?> =
        combine(
            readings.observeTerritories(),
            prefs.preferences,
            readings.observeReadings(territoryId),
        ) { territories, p, list ->
            val territory = territories.firstOrNull { it.id == territoryId } ?: return@combine null
            TerritoryEvaluation(territory, engine.evaluate(territory, list, p.thresholds, time.now()), p)
        }.flowOn(Dispatchers.Default)
}

// ---------- Detalhe do indicador ----------

/** Ponto do gráfico: horas em relação a agora (negativo = passado). */
data class SeriesPoint(val hoursFromNow: Double, val value: Double)

data class IndicatorSeries(
    val territory: Territory,
    val kpi: Kpi,
    val indicator: Indicator,
    val observed: List<SeriesPoint>,
    val forecast: List<SeriesPoint>,
    val threshold: Double?,
    val thresholdLabel: String,
    val keyValues: List<Pair<String, String>>,
    val origin: DataOrigin?,
    val lastObservationAt: Instant?,
)

class ObserveIndicatorSeries(
    private val readings: ReadingRepository,
    private val prefs: PreferencesRepository,
    private val time: TimeProvider,
) {
    operator fun invoke(territoryId: String, type: KpiType): Flow<IndicatorSeries?> =
        combine(
            readings.observeTerritories(),
            prefs.preferences,
            readings.observeReadings(territoryId),
        ) { territories, p, list ->
            val territory = territories.firstOrNull { it.id == territoryId } ?: return@combine null
            val now = time.now()
            val t = p.thresholds
            val kpi = IndicatorCalculator.kpis(list, now, t).first { it.type == type }
            val ofIndicator = list.filter { it.indicator == type.source }

            fun hours(i: Instant) = Duration.between(now, i).toMinutes() / 60.0
            val observed = ofIndicator
                .filter { it.kind == ReadingKind.OBSERVADO && it.timestamp <= now && hours(it.timestamp) >= -72 }
                .sortedBy { it.timestamp }
                .map { SeriesPoint(hours(it.timestamp), it.value) }

            when (type.source) {
                Indicator.CHUVA_MM_H -> {
                    val rain = IndicatorCalculator.rain(list, now)
                    val forecast = ofIndicator
                        .filter { it.kind == ReadingKind.PREVISTO && it.timestamp > now && hours(it.timestamp) <= 72 }
                        .sortedBy { it.timestamp }
                        .map { SeriesPoint(hours(it.timestamp), it.value) }
                    IndicatorSeries(
                        territory = territory,
                        kpi = kpi,
                        indicator = type.source,
                        observed = observed,
                        forecast = forecast,
                        threshold = t.peakAttentionMmH,
                        thresholdLabel = "Limiar de atenção: ${t.peakAttentionMmH.toInt()} mm/h",
                        keyValues = listOf(
                            "Chuva nas últimas 24 h" to rain.rain24h.mmOrDash(),
                            "Acumulado em 72 h" to rain.accumulated72h.mmOrDash(),
                            "Prevista nas próximas 24 h" to rain.forecast24h.mmOrDash(),
                            "Pico previsto" to (rain.forecastPeak?.value?.let { "${oneDecimal(it)} mm/h" } ?: "—"),
                            "Cobertura das últimas 24 h" to "${(rain.coverage24h * 100).toInt()}%",
                        ),
                        origin = IndicatorCalculator.originOf(ofIndicator),
                        lastObservationAt = rain.lastObservationAt,
                    )
                }
                Indicator.NIVEL_CORREGO_PCT -> {
                    val stream = IndicatorCalculator.stream(list, now)
                    val projection = stream?.let {
                        val start = hours(it.lastAt)
                        listOf(
                            SeriesPoint(start, it.current),
                            SeriesPoint(start + IndicatorCalculator.PROJECTION_HOURS, it.projected6h),
                        )
                    } ?: emptyList()
                    IndicatorSeries(
                        territory = territory,
                        kpi = kpi,
                        indicator = type.source,
                        observed = observed,
                        forecast = projection,
                        threshold = t.streamAttentionPct,
                        thresholdLabel = "Limiar de atenção: ${t.streamAttentionPct.toInt()}% da cota",
                        keyValues = listOf(
                            "Nível atual" to (stream?.let { "${oneDecimal(it.current)}%" } ?: "—"),
                            "Variação por hora" to (stream?.let { "${oneDecimal(it.slopePerHour)} ponto(s)" } ?: "—"),
                            "Projeção em 6 h" to (stream?.let { "${oneDecimal(it.projected6h)}%" } ?: "—"),
                        ),
                        origin = IndicatorCalculator.originOf(ofIndicator),
                        lastObservationAt = stream?.lastAt,
                    )
                }
            }
        }.flowOn(Dispatchers.Default)

    private fun Double?.mmOrDash(): String = this?.let { "${oneDecimal(it)} mm" } ?: "—"
}

internal fun oneDecimal(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString().replace('.', ',')
}

// ---------- Decisão humana ----------

class DecisionValidationException(message: String) : IllegalArgumentException(message)

class RegisterDecision(
    private val decisions: DecisionRepository,
    private val time: TimeProvider,
) {
    /**
     * Valida e grava a decisão. Regras:
     * - o responsável é obrigatório (rastreabilidade);
     * - adiar ou descartar um alerta CRÍTICO exige observação.
     */
    suspend operator fun invoke(
        alert: Alert,
        action: DecisionAction,
        responsible: String,
        note: String,
    ): Result<Decision> {
        val who = responsible.trim()
        val obs = note.trim()
        if (who.isEmpty()) {
            return Result.failure(DecisionValidationException("Informe o nome do responsável pela decisão."))
        }
        if (alert.level == AlertLevel.CRITICO && action != DecisionAction.ACATAR && obs.isEmpty()) {
            return Result.failure(
                DecisionValidationException("Para ${action.label.lowercase()} um alerta crítico, registre o motivo na observação."),
            )
        }
        val decision = Decision(
            alertId = alert.id,
            territoryId = alert.territoryId,
            action = action,
            responsible = who,
            note = obs,
            decidedAt = time.now(),
        )
        return runCatching { decisions.save(alert, decision) }.map { decision }
    }
}

// ---------- Notificação de alerta crítico ----------

/**
 * Decide QUANDO notificar: só nível crítico, só se o usuário ativou, só se o sistema permite
 * e nunca duas vezes o mesmo alerta. O COMO fica no adaptador [AlertNotifier].
 */
class CheckCriticalAlerts(
    private val readings: ReadingRepository,
    private val prefs: PreferencesRepository,
    private val engine: AlertEngine,
    private val notifier: AlertNotifier,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): Int {
        val p = prefs.preferences.first()
        if (!p.criticalNotificationsEnabled || !notifier.canNotify()) return 0
        var sent = 0
        for (territory in readings.observeTerritories().first()) {
            val evaluation = engine.evaluate(territory, readings.readingsOnce(territory.id), p.thresholds, time.now())
            val alert = (evaluation as? AlertEvaluation.Active)?.alert ?: continue
            if (alert.level == AlertLevel.CRITICO && !prefs.wasNotified(alert.id)) {
                notifier.notifyCritical(alert)
                prefs.markNotified(alert.id)
                sent++
            }
        }
        return sent
    }
}
