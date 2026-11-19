package br.com.vigiaterritorio.domain.engine

import br.com.vigiaterritorio.domain.model.DataStatus
import br.com.vigiaterritorio.domain.model.Thresholds
import java.time.Duration
import java.time.Instant

/** Define quando os dados são válidos e quando o app NÃO deve emitir alerta. */
object DataFreshnessPolicy {

    fun evaluate(lastObservationAt: Instant?, coverage24h: Double, now: Instant, t: Thresholds): DataStatus {
        val ageHours = lastObservationAt?.let { Duration.between(it, now).toMinutes() / 60.0 }
        return DataStatus(
            lastObservationAt = lastObservationAt,
            ageHours = ageHours,
            coverage24h = coverage24h,
            isStale = ageHours == null || ageHours > t.staleAfterHours,
            isInsufficient = coverage24h < t.minCoverage,
        )
    }
}
