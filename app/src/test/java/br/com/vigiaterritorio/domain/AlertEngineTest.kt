package br.com.vigiaterritorio.domain

import br.com.vigiaterritorio.domain.engine.AlertEngine
import br.com.vigiaterritorio.domain.engine.IndicatorCalculator
import br.com.vigiaterritorio.domain.model.AlertEvaluation
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Indicator
import br.com.vigiaterritorio.domain.model.KpiStatus
import br.com.vigiaterritorio.domain.model.KpiType
import br.com.vigiaterritorio.domain.model.Reading
import br.com.vigiaterritorio.domain.model.ReadingKind
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.TerritoryType
import br.com.vigiaterritorio.domain.model.Thresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class AlertEngineTest {

    private val anchor = Instant.parse("2026-11-18T20:00:00Z")
    private val now = anchor.plus(Duration.ofMinutes(15))
    private val territory = Territory("t1", "Jardim das Águas", TerritoryType.BAIRRO, 0.0, 0.0)
    private val engine = AlertEngine(ZoneOffset.UTC)
    private val t = Thresholds()

    /** Chuva observada de -71 a 0 h: as 48 h mais antigas somam [old], as últimas 24 h somam [last24]. */
    private fun observedRain(old: Double, last24: Double, hoursPresentInLast24: Int = 24): List<Reading> {
        val olds = (-71..-24).map { rain(it, old / 48, ReadingKind.OBSERVADO) }
        val recent = (-23..0).toList().takeLast(hoursPresentInLast24).map { rain(it, last24 / hoursPresentInLast24, ReadingKind.OBSERVADO) }
        return olds + recent
    }

    /** Previsão de +1 a +24 h somando [total], com [peak] mm/h na hora +18. */
    private fun forecastRain(total: Double, peak: Double): List<Reading> {
        val rest = (total - peak) / 23
        return (1..24).map { rain(it, if (it == 18) peak else rest, ReadingKind.PREVISTO) }
    }

    private fun stream(lastSix: List<Double>): List<Reading> =
        lastSix.mapIndexed { i, v -> reading(Indicator.NIVEL_CORREGO_PCT, i - 5, v, ReadingKind.OBSERVADO) }

    private fun rain(offset: Int, v: Double, kind: ReadingKind) = reading(Indicator.CHUVA_MM_H, offset, v, kind)

    private fun reading(ind: Indicator, offset: Int, v: Double, kind: ReadingKind) =
        Reading("t1", ind, kind, anchor.plus(Duration.ofHours(offset.toLong())), v, DataOrigin.SINTETICO)

    @Test
    fun `cenario Jardim das Aguas - atencao elevada a critico pelo solo saturado`() {
        val readings = observedRain(old = 56.0, last24 = 18.0) + forecastRain(92.0, 38.0) +
            stream(listOf(55.5, 58.0, 60.5, 63.0, 65.5, 68.0))

        val result = engine.evaluate(territory, readings, t, now)

        assertTrue(result is AlertEvaluation.Active)
        val alert = (result as AlertEvaluation.Active).alert
        assertEquals(AlertLevel.CRITICO, alert.level)
        assertEquals(listOf("R1", "R2", "R3"), alert.factors.map { it.rule })
        assertEquals(AlertLevel.ATENCAO, alert.factors.first { it.rule == "R1" }.level)
        assertEquals(AlertLevel.CRITICO, alert.factors.first { it.rule == "R2" }.level)
        assertEquals(AlertLevel.ATENCAO, alert.factors.first { it.rule == "R3" }.level)
        assertEquals(80, alert.confidencePercent) // 0,90 − 0,10 (sintético)
        assertTrue(alert.factors.first { it.rule == "R3" }.measured.contains("83%"))
    }

    @Test
    fun `chuva prevista de 58 mm sem saturacao gera atencao`() {
        val readings = observedRain(old = 14.0, last24 = 8.0) + forecastRain(58.0, 24.0)
        val alert = (engine.evaluate(territory, readings, t, now) as AlertEvaluation.Active).alert
        assertEquals(AlertLevel.ATENCAO, alert.level)
        assertEquals(listOf("R1"), alert.factors.map { it.rule })
    }

    @Test
    fun `chuva prevista de 34 mm gera informativo`() {
        val readings = observedRain(old = 26.0, last24 = 9.0) + forecastRain(34.0, 12.0)
        val alert = (engine.evaluate(territory, readings, t, now) as AlertEvaluation.Active).alert
        assertEquals(AlertLevel.INFORMATIVO, alert.level)
    }

    @Test
    fun `pico horario acima de 60 mm_h e critico mesmo com total moderado`() {
        val readings = observedRain(old = 5.0, last24 = 2.0) + forecastRain(70.0, 65.0)
        val alert = (engine.evaluate(territory, readings, t, now) as AlertEvaluation.Active).alert
        assertEquals(AlertLevel.CRITICO, alert.level)
    }

    @Test
    fun `tempo bom nao gera alerta`() {
        val readings = observedRain(old = 9.0, last24 = 3.0) + forecastRain(8.0, 4.0) +
            stream(listOf(30.4, 30.1, 30.3, 30.0, 30.2, 30.1))
        assertTrue(engine.evaluate(territory, readings, t, now) is AlertEvaluation.NoRisk)
    }

    @Test
    fun `corrego projetado acima da cota gera critico sozinho (R3)`() {
        val readings = observedRain(old = 5.0, last24 = 2.0) + forecastRain(8.0, 4.0) +
            stream(listOf(70.0, 74.0, 78.0, 82.0, 86.0, 90.0)) // +4/h → 114% em 6 h
        val alert = (engine.evaluate(territory, readings, t, now) as AlertEvaluation.Active).alert
        assertEquals(AlertLevel.CRITICO, alert.level)
        assertEquals(listOf("R3"), alert.factors.map { it.rule })
        assertTrue(alert.title.contains("6 horas"))
    }

    @Test
    fun `cobertura abaixo de 70 por cento suprime o alerta`() {
        val readings = observedRain(old = 12.0, last24 = 6.0, hoursPresentInLast24 = 10) + forecastRain(46.0, 18.0)
        val result = engine.evaluate(territory, readings, t, now)
        assertTrue(result is AlertEvaluation.Suppressed)
        assertTrue((result as AlertEvaluation.Suppressed).reason.startsWith("Dados insuficientes"))
        assertTrue(result.dataStatus.isInsufficient)
    }

    @Test
    fun `dados com mais de 6 horas suprimem o alerta`() {
        val readings = observedRain(old = 56.0, last24 = 18.0) + forecastRain(92.0, 38.0)
        val later = anchor.plus(Duration.ofHours(7))
        val result = engine.evaluate(territory, readings, t, later)
        assertTrue(result is AlertEvaluation.Suppressed)
        assertTrue((result as AlertEvaluation.Suppressed).reason.startsWith("Dados desatualizados"))
    }

    @Test
    fun `sem nenhuma leitura o alerta e suprimido`() {
        assertTrue(engine.evaluate(territory, emptyList(), t, now) is AlertEvaluation.Suppressed)
    }

    @Test
    fun `limiar configuravel muda o resultado`() {
        val readings = observedRain(old = 14.0, last24 = 8.0) + forecastRain(58.0, 24.0)
        val stricter = t.copy(forecastCriticalMm = 55.0)
        val alert = (engine.evaluate(territory, readings, stricter, now) as AlertEvaluation.Active).alert
        assertEquals(AlertLevel.CRITICO, alert.level)
    }

    @Test
    fun `formula de confianca`() {
        assertEquals(0.80, engine.confidence(DataOrigin.SINTETICO, 1.0, 0.25), 1e-9)
        assertEquals(0.90, engine.confidence(DataOrigin.PUBLICO, 1.0, 0.25), 1e-9)
        assertEquals(0.70, engine.confidence(DataOrigin.SINTETICO, 1.0, 4.0), 1e-9)
        assertEquals(0.65, engine.confidence(DataOrigin.SINTETICO, 0.7, 0.25), 1e-9)
        assertEquals(0.30, engine.confidence(DataOrigin.SINTETICO, 0.0, 5.0), 1e-9)
    }

    @Test
    fun `regressao linear da inclinacao correta`() {
        val slope = IndicatorCalculator.linearSlope(listOf(0.0, 1.0, 2.0, 3.0), listOf(10.0, 12.5, 15.0, 17.5))
        assertEquals(2.5, slope, 1e-9)
    }

    @Test
    fun `KPIs do cenario critico`() {
        val readings = observedRain(old = 56.0, last24 = 18.0) + forecastRain(92.0, 38.0) +
            stream(listOf(55.5, 58.0, 60.5, 63.0, 65.5, 68.0))
        val kpis = IndicatorCalculator.kpis(readings, now, t).associateBy { it.type }
        assertEquals(18.0, kpis.getValue(KpiType.CHUVA_24H).value!!, 1e-6)
        assertEquals(74.0, kpis.getValue(KpiType.ACUMULADO_72H).value!!, 1e-6)
        assertEquals(92.0, kpis.getValue(KpiType.PREVISAO_24H).value!!, 1e-6)
        assertEquals(KpiStatus.ATENCAO, kpis.getValue(KpiType.PREVISAO_24H).status)
        assertEquals(KpiStatus.ATENCAO, kpis.getValue(KpiType.ACUMULADO_72H).status)
        assertEquals(KpiStatus.ATENCAO, kpis.getValue(KpiType.NIVEL_CORREGO).status)
    }

    @Test
    fun `territorio sem sensor de corrego mostra sem dados`() {
        val readings = observedRain(old = 9.0, last24 = 3.0) + forecastRain(8.0, 4.0)
        val level = IndicatorCalculator.kpis(readings, now, t).first { it.type == KpiType.NIVEL_CORREGO }
        assertEquals(KpiStatus.SEM_DADOS, level.status)
        assertEquals(null, level.value)
    }
}
