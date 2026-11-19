package br.com.vigiaterritorio.data.local

import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.AlertFactor
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Decision
import br.com.vigiaterritorio.domain.model.DecisionAction
import br.com.vigiaterritorio.domain.model.Indicator
import br.com.vigiaterritorio.domain.model.Reading
import br.com.vigiaterritorio.domain.model.ReadingKind
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.TerritoryType
import java.time.Instant

fun TerritoryEntity.toDomain() = Territory(id, name, TerritoryType.valueOf(type), latitude, longitude)

fun ReadingEntity.toDomain() = Reading(
    territoryId = territoryId,
    indicator = Indicator.valueOf(indicator),
    kind = ReadingKind.valueOf(kind),
    timestamp = Instant.ofEpochMilli(timestamp),
    value = value,
    origin = DataOrigin.valueOf(origin),
)

fun Alert.toEntity() = AlertEntity(
    id = id,
    territoryId = territoryId,
    territoryName = territoryName,
    level = level.name,
    confidence = confidence,
    title = title,
    recommendation = recommendation,
    justification = AlertFactorCodec.encode(factors),
    periodStart = periodStart.toEpochMilli(),
    periodEnd = periodEnd.toEpochMilli(),
    validUntil = validUntil.toEpochMilli(),
    origin = origin.name,
    dataUpdatedAt = dataUpdatedAt.toEpochMilli(),
    coverage24h = coverage24h,
)

fun AlertEntity.toDomain() = Alert(
    id = id,
    territoryId = territoryId,
    territoryName = territoryName,
    level = AlertLevel.valueOf(level),
    confidence = confidence,
    title = title,
    recommendation = recommendation,
    factors = AlertFactorCodec.decode(justification),
    periodStart = Instant.ofEpochMilli(periodStart),
    periodEnd = Instant.ofEpochMilli(periodEnd),
    validUntil = Instant.ofEpochMilli(validUntil),
    origin = DataOrigin.valueOf(origin),
    dataUpdatedAt = Instant.ofEpochMilli(dataUpdatedAt),
    coverage24h = coverage24h,
)

fun Decision.toEntity() = DecisionEntity(
    id = id,
    alertId = alertId,
    territoryId = territoryId,
    action = action.name,
    responsible = responsible,
    note = note,
    decidedAt = decidedAt.toEpochMilli(),
)

fun DecisionEntity.toDomain() = Decision(
    id = id,
    alertId = alertId,
    territoryId = territoryId,
    action = DecisionAction.valueOf(action),
    responsible = responsible,
    note = note,
    decidedAt = Instant.ofEpochMilli(decidedAt),
)

/**
 * Serialização simples das linhas da justificativa: um fator por linha, campos separados por TAB.
 * Os textos são gerados pelo próprio app, sem TAB nem quebra de linha.
 */
object AlertFactorCodec {
    private const val FIELD = '\t'
    private const val LINE = '\n'

    fun encode(factors: List<AlertFactor>): String = factors.joinToString(LINE.toString()) {
        listOf(it.rule, it.description, it.measured, it.threshold, it.period, it.level.name)
            .joinToString(FIELD.toString()) { field -> field.replace(FIELD, ' ').replace(LINE, ' ') }
    }

    fun decode(text: String): List<AlertFactor> = text.split(LINE).filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split(FIELD)
        if (f.size < 6) null else AlertFactor(f[0], f[1], f[2], f[3], f[4], AlertLevel.valueOf(f[5]))
    }
}
