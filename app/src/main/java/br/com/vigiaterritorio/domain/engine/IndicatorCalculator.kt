package br.com.vigiaterritorio.domain.engine

import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Indicator
import br.com.vigiaterritorio.domain.model.Kpi
import br.com.vigiaterritorio.domain.model.KpiStatus
import br.com.vigiaterritorio.domain.model.KpiType
import br.com.vigiaterritorio.domain.model.Reading
import br.com.vigiaterritorio.domain.model.ReadingKind
import br.com.vigiaterritorio.domain.model.Thresholds
import br.com.vigiaterritorio.domain.model.Trend
import java.time.Duration
import java.time.Instant

data class RainStats(
    val rain24h: Double?,
    val accumulated72h: Double?,
    val forecast24h: Double?,
    val forecastPeak: Reading?,
    val coverage24h: Double,
    val lastObservationAt: Instant?,
    val last12h: Double,
    val previous12h: Double,
)

data class StreamStats(
    val current: Double,
    val slopePerHour: Double,
    val projected6h: Double,
    val lastAt: Instant,
)

/** Estado derivado: KPIs calculados a partir das leituras brutas, nunca guardados duplicados. */
object IndicatorCalculator {

    const val PROJECTION_HOURS = 6L
    private const val TREND_POINTS = 6

    fun rain(readings: List<Reading>, now: Instant): RainStats {
        val observed = readings.filter { it.indicator == Indicator.CHUVA_MM_H && it.kind == ReadingKind.OBSERVADO }
        val forecast = readings.filter { it.indicator == Indicator.CHUVA_MM_H && it.kind == ReadingKind.PREVISTO }

        fun observedWithin(hours: Long) = observed.filter { it.timestamp > now.minusHours(hours) && it.timestamp <= now }

        val last24 = observedWithin(24)
        val last72 = observedWithin(72)
        val next24 = forecast.filter { it.timestamp > now && it.timestamp <= now.plusHours(24) }
        val coveredHours = last24.map { it.timestamp.epochSecond / 3600 }.distinct().size

        return RainStats(
            rain24h = last24.takeIf { it.isNotEmpty() }?.sumOf { it.value },
            accumulated72h = last72.takeIf { it.isNotEmpty() }?.sumOf { it.value },
            forecast24h = next24.takeIf { it.isNotEmpty() }?.sumOf { it.value },
            forecastPeak = next24.maxByOrNull { it.value },
            coverage24h = (coveredHours / 24.0).coerceAtMost(1.0),
            lastObservationAt = observed.maxOfOrNull { it.timestamp },
            last12h = observedWithin(12).sumOf { it.value },
            previous12h = last24.filter { it.timestamp <= now.minusHours(12) }.sumOf { it.value },
        )
    }

    fun stream(readings: List<Reading>, now: Instant): StreamStats? {
        val points = readings
            .filter { it.indicator == Indicator.NIVEL_CORREGO_PCT && it.kind == ReadingKind.OBSERVADO && it.timestamp <= now }
            .sortedBy { it.timestamp }
            .takeLast(TREND_POINTS)
        if (points.size < 2) return null
        val origin = points.first().timestamp
        val xs = points.map { Duration.between(origin, it.timestamp).toMinutes() / 60.0 }
        val slope = linearSlope(xs, points.map { it.value })
        val last = points.last()
        return StreamStats(
            current = last.value,
            slopePerHour = slope,
            projected6h = last.value + slope * PROJECTION_HOURS,
            lastAt = last.timestamp,
        )
    }

    /** Inclinação da reta de mínimos quadrados (regressão linear simples). */
    fun linearSlope(xs: List<Double>, ys: List<Double>): Double {
        require(xs.size == ys.size && xs.size >= 2)
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        return if (den == 0.0) 0.0 else num / den
    }

    fun kpis(readings: List<Reading>, now: Instant, t: Thresholds): List<Kpi> {
        val rain = rain(readings, now)
        val stream = stream(readings, now)
        val origin = originOf(readings)

        val rain24 = Kpi(
            type = KpiType.CHUVA_24H,
            value = rain.rain24h,
            status = when {
                rain.rain24h == null -> KpiStatus.SEM_DADOS
                rain.rain24h > t.forecastCriticalMm -> KpiStatus.CRITICO
                rain.rain24h >= t.forecastAttentionMm -> KpiStatus.ATENCAO
                else -> KpiStatus.NORMAL
            },
            trend = if (rain.rain24h == null) Trend.DESCONHECIDA else compare(rain.last12h, rain.previous12h),
            origin = origin,
        )
        val accumulated = Kpi(
            type = KpiType.ACUMULADO_72H,
            value = rain.accumulated72h,
            status = when {
                rain.accumulated72h == null -> KpiStatus.SEM_DADOS
                rain.accumulated72h >= t.saturation72hMm -> KpiStatus.ATENCAO
                else -> KpiStatus.NORMAL
            },
            trend = if (rain.accumulated72h == null || rain.rain24h == null) Trend.DESCONHECIDA
            else compare(rain.rain24h, (rain.accumulated72h - rain.rain24h) / 2),
            origin = origin,
        )
        val forecast = Kpi(
            type = KpiType.PREVISAO_24H,
            value = rain.forecast24h,
            status = when {
                rain.forecast24h == null -> KpiStatus.SEM_DADOS
                rain.forecast24h > t.forecastCriticalMm -> KpiStatus.CRITICO
                rain.forecast24h >= t.forecastAttentionMm -> KpiStatus.ATENCAO
                else -> KpiStatus.NORMAL
            },
            trend = if (rain.forecast24h == null) Trend.DESCONHECIDA else compare(rain.forecast24h, rain.rain24h ?: 0.0),
            origin = origin,
        )
        val level = Kpi(
            type = KpiType.NIVEL_CORREGO,
            value = stream?.current,
            status = when {
                stream == null -> KpiStatus.SEM_DADOS
                stream.current >= t.streamCriticalPct || stream.projected6h >= t.streamCriticalPct -> KpiStatus.CRITICO
                stream.current >= t.streamAttentionPct || stream.projected6h >= t.streamAttentionPct -> KpiStatus.ATENCAO
                else -> KpiStatus.NORMAL
            },
            trend = when {
                stream == null -> Trend.DESCONHECIDA
                stream.slopePerHour > 0.5 -> Trend.SUBINDO
                stream.slopePerHour < -0.5 -> Trend.DESCENDO
                else -> Trend.ESTAVEL
            },
            origin = origin,
        )
        return listOf(forecast, rain24, accumulated, level)
    }

    /** Se qualquer leitura for sintética, o conjunto é marcado como sintético (regra conservadora). */
    fun originOf(readings: List<Reading>): DataOrigin? = when {
        readings.isEmpty() -> null
        readings.any { it.origin == DataOrigin.SINTETICO } -> DataOrigin.SINTETICO
        readings.any { it.origin == DataOrigin.AUTORIZADO } -> DataOrigin.AUTORIZADO
        else -> DataOrigin.PUBLICO
    }

    private fun compare(recent: Double, reference: Double): Trend = when {
        recent - reference > 1.0 -> Trend.SUBINDO
        reference - recent > 1.0 -> Trend.DESCENDO
        else -> Trend.ESTAVEL
    }

    private fun Instant.minusHours(h: Long): Instant = minus(Duration.ofHours(h))
    private fun Instant.plusHours(h: Long): Instant = plus(Duration.ofHours(h))
}
