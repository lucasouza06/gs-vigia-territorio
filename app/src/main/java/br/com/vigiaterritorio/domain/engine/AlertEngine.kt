package br.com.vigiaterritorio.domain.engine

import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.AlertEvaluation
import br.com.vigiaterritorio.domain.model.AlertFactor
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Reading
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.Thresholds
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Motor de alertas preditivos com regras EXPLICÁVEIS.
 *
 * R1 – Limiar de chuva prevista nas próximas 24 h (total e pico horário), faixas do INMET.
 * R2 – Saturação do solo: acumulado de 72 h acima do limiar sobe o nível de R1 em um grau.
 * R3 – Tendência do córrego: regressão linear das últimas 6 leituras projetada 6 h à frente.
 *
 * O nível final é o maior entre (R1 + R2) e R3. Cada regra disparada vira uma linha da justificativa.
 * O motor apenas RECOMENDA; nenhuma ação de alto impacto é executada pelo app.
 */
class AlertEngine(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
    private val periodFormat = DateTimeFormatter.ofPattern("dd/MM HH'h'", ptBr)

    fun evaluate(
        territory: Territory,
        readings: List<Reading>,
        thresholds: Thresholds,
        now: Instant,
    ): AlertEvaluation {
        val rain = IndicatorCalculator.rain(readings, now)
        val stream = IndicatorCalculator.stream(readings, now)
        val lastObservation = listOfNotNull(rain.lastObservationAt, stream?.lastAt).maxOrNull()
        val status = DataFreshnessPolicy.evaluate(lastObservation, rain.coverage24h, now, thresholds)

        // Situações em que o app NÃO deve emitir alerta.
        if (status.isStale) {
            val age = status.ageHours?.let { "há ${it.roundToInt()} h" } ?: "nunca recebida"
            return AlertEvaluation.Suppressed(
                reason = "Dados desatualizados: última leitura $age. O app não emite alertas com dados " +
                    "de mais de ${thresholds.staleAfterHours.roundToInt()} h.",
                dataStatus = status,
            )
        }
        if (status.isInsufficient) {
            val hours = (rain.coverage24h * 24).roundToInt()
            return AlertEvaluation.Suppressed(
                reason = "Dados insuficientes para prever: só $hours de 24 horas têm leitura de chuva " +
                    "(mínimo de ${(thresholds.minCoverage * 100).roundToInt()}%).",
                dataStatus = status,
            )
        }

        val factors = mutableListOf<AlertFactor>()
        val next24 = "${fmt(now)} → ${fmt(now.plus(Duration.ofHours(24)))}"

        // R1 – chuva prevista
        val f24 = rain.forecast24h ?: 0.0
        val peak = rain.forecastPeak?.value ?: 0.0
        var rainLevel: AlertLevel? = when {
            f24 > thresholds.forecastCriticalMm || peak > thresholds.peakCriticalMmH -> AlertLevel.CRITICO
            f24 >= thresholds.forecastAttentionMm || peak >= thresholds.peakAttentionMmH -> AlertLevel.ATENCAO
            f24 >= thresholds.forecastInfoMm || peak >= thresholds.peakInfoMmH -> AlertLevel.INFORMATIVO
            else -> null
        }
        if (rainLevel != null) {
            val peakAt = rain.forecastPeak?.timestamp?.let { " às ${hourOf(it)}" } ?: ""
            factors += AlertFactor(
                rule = "R1",
                description = "Chuva prevista nas próximas 24 h",
                measured = "${mm(f24)} mm no total; pico de ${mm(peak)} mm/h$peakAt",
                threshold = "Informativo ≥ ${mm(thresholds.forecastInfoMm)} mm · Atenção ≥ " +
                    "${mm(thresholds.forecastAttentionMm)} mm ou ≥ ${mm(thresholds.peakAttentionMmH)} mm/h · " +
                    "Crítico > ${mm(thresholds.forecastCriticalMm)} mm ou > ${mm(thresholds.peakCriticalMmH)} mm/h",
                period = next24,
                level = rainLevel,
            )
        }

        // R2 – solo saturado eleva o nível de R1
        val acc72 = rain.accumulated72h ?: 0.0
        if (rainLevel != null && rainLevel != AlertLevel.CRITICO && acc72 >= thresholds.saturation72hMm) {
            val raised = rainLevel.raise()
            factors += AlertFactor(
                rule = "R2",
                description = "Solo saturado: chuva acumulada nas últimas 72 h",
                measured = "${mm(acc72)} mm",
                threshold = "≥ ${mm(thresholds.saturation72hMm)} mm eleva o nível de " +
                    "${rainLevel.label} para ${raised.label}",
                period = "${fmt(now.minus(Duration.ofHours(72)))} → ${fmt(now)}",
                level = raised,
            )
            rainLevel = raised
        }

        // R3 – tendência do córrego
        var streamLevel: AlertLevel? = null
        if (stream != null) {
            streamLevel = when {
                stream.projected6h >= thresholds.streamCriticalPct -> AlertLevel.CRITICO
                stream.projected6h >= thresholds.streamAttentionPct -> AlertLevel.ATENCAO
                else -> null
            }
            if (streamLevel != null) {
                val sign = if (stream.slopePerHour >= 0) "+" else "−"
                factors += AlertFactor(
                    rule = "R3",
                    description = "Tendência do córrego (regressão linear das últimas 6 leituras)",
                    measured = "${pct(stream.current)}% agora, $sign${dec(kotlin.math.abs(stream.slopePerHour))} " +
                        "ponto(s) por hora → ${pct(stream.projected6h)}% em 6 h",
                    threshold = "Atenção ≥ ${pct(thresholds.streamAttentionPct)}% · " +
                        "Crítico ≥ ${pct(thresholds.streamCriticalPct)}% da cota",
                    period = "${fmt(now)} → ${fmt(now.plus(Duration.ofHours(IndicatorCalculator.PROJECTION_HOURS)))}",
                    level = streamLevel,
                )
            }
        }

        val finalLevel = listOfNotNull(rainLevel, streamLevel).maxByOrNull { it.rank }
            ?: return AlertEvaluation.NoRisk(status)

        val origin = IndicatorCalculator.originOf(readings) ?: DataOrigin.SINTETICO
        val horizonHours = if (rainLevel != null) 24L else IndicatorCalculator.PROJECTION_HOURS
        val updatedAt = status.lastObservationAt ?: now

        val alert = Alert(
            id = "${territory.id}-${finalLevel.name}-${updatedAt.epochSecond / 3600}",
            territoryId = territory.id,
            territoryName = territory.name,
            level = finalLevel,
            confidence = confidence(origin, rain.coverage24h, status.ageHours ?: 0.0),
            title = "${finalLevel.label}: risco de alagamento em ${territory.name} nas próximas $horizonHours horas",
            recommendation = recommendationFor(finalLevel),
            factors = factors.sortedBy { it.rule },
            periodStart = now,
            periodEnd = now.plus(Duration.ofHours(horizonHours)),
            validUntil = updatedAt.plus(Duration.ofMinutes((thresholds.staleAfterHours * 60).toLong())),
            origin = origin,
            dataUpdatedAt = updatedAt,
            coverage24h = rain.coverage24h,
        )
        return AlertEvaluation.Active(alert, status)
    }

    /** 0,90 − 0,10 (sintético) − 0,5 × (1 − cobertura) − 0,10 (dados com mais de 3 h), entre 30% e 95%. */
    fun confidence(origin: DataOrigin, coverage24h: Double, ageHours: Double): Double {
        var c = 0.90
        if (origin == DataOrigin.SINTETICO) c -= 0.10
        c -= 0.5 * (1.0 - coverage24h)
        if (ageHours > 3.0) c -= 0.10
        return c.coerceIn(0.30, 0.95)
    }

    fun recommendationFor(level: AlertLevel): String = when (level) {
        AlertLevel.INFORMATIVO -> "Acompanhar a previsão nas próximas horas."
        AlertLevel.ATENCAO -> "Vistoriar bueiros e pontos de alagamento conhecidos e manter equipe de prontidão."
        AlertLevel.CRITICO -> "Avaliar o acionamento da Defesa Civil e o aviso aos moradores. " +
            "A decisão é do gestor: o app não executa nenhuma ação."
    }

    private fun fmt(i: Instant): String = periodFormat.format(i.atZone(zone))
    private fun hourOf(i: Instant): String = "${i.atZone(zone).hour}h"
    private fun mm(v: Double): String {
        val r = (v * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else String.format(ptBr, "%.1f", r)
    }
    private fun pct(v: Double): String = v.roundToInt().toString()
    private fun dec(v: Double): String = String.format(ptBr, "%.1f", v)
}
