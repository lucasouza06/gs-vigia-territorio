package br.com.vigiaterritorio.domain.model

import java.time.Instant

// Camada de DOMÍNIO: Kotlin puro. Nenhum import de android.*, Room ou Compose aqui.

enum class TerritoryType(val label: String) {
    CIDADE("Cidade"),
    BAIRRO("Bairro"),
    RURAL("Área rural"),
}

data class Territory(
    val id: String,
    val name: String,
    val type: TerritoryType,
    val latitude: Double,
    val longitude: Double,
)

/** Grandezas lidas dos sensores/previsões. */
enum class Indicator(val label: String, val unit: String) {
    CHUVA_MM_H("Chuva por hora", "mm/h"),
    NIVEL_CORREGO_PCT("Nível do córrego", "% da cota"),
}

enum class ReadingKind { OBSERVADO, PREVISTO }

enum class DataOrigin(val label: String) {
    SINTETICO("Dado sintético"),
    PUBLICO("Dado público"),
    AUTORIZADO("Dado autorizado"),
}

data class Reading(
    val territoryId: String,
    val indicator: Indicator,
    val kind: ReadingKind,
    val timestamp: Instant,
    val value: Double,
    val origin: DataOrigin,
)

/** Indicadores derivados mostrados como KPI. Cada um sabe de qual grandeza vem. */
enum class KpiType(val label: String, val unit: String, val source: Indicator) {
    CHUVA_24H("Chuva nas últimas 24 h", "mm", Indicator.CHUVA_MM_H),
    ACUMULADO_72H("Acumulado em 72 h", "mm", Indicator.CHUVA_MM_H),
    PREVISAO_24H("Chuva prevista em 24 h", "mm", Indicator.CHUVA_MM_H),
    NIVEL_CORREGO("Nível do córrego", "% da cota", Indicator.NIVEL_CORREGO_PCT),
}

enum class KpiStatus(val label: String) {
    NORMAL("Normal"),
    ATENCAO("Atenção"),
    CRITICO("Crítico"),
    SEM_DADOS("Sem dados"),
}

enum class Trend(val label: String) {
    SUBINDO("subindo"),
    ESTAVEL("estável"),
    DESCENDO("descendo"),
    DESCONHECIDA("sem tendência"),
}

data class Kpi(
    val type: KpiType,
    val value: Double?,
    val status: KpiStatus,
    val trend: Trend,
    val origin: DataOrigin?,
)

enum class AlertLevel(val label: String, val rank: Int) {
    INFORMATIVO("Informativo", 1),
    ATENCAO("Atenção", 2),
    CRITICO("Crítico", 3);

    fun raise(): AlertLevel = entries.firstOrNull { it.rank == rank + 1 } ?: this
}

/** Uma linha da justificativa: qual regra disparou, com que valor, contra qual limiar e em que período. */
data class AlertFactor(
    val rule: String,
    val description: String,
    val measured: String,
    val threshold: String,
    val period: String,
    val level: AlertLevel,
)

data class Alert(
    val id: String,
    val territoryId: String,
    val territoryName: String,
    val level: AlertLevel,
    val confidence: Double,
    val title: String,
    val recommendation: String,
    val factors: List<AlertFactor>,
    val periodStart: Instant,
    val periodEnd: Instant,
    val validUntil: Instant,
    val origin: DataOrigin,
    val dataUpdatedAt: Instant,
    val coverage24h: Double,
) {
    val confidencePercent: Int get() = (confidence * 100).toInt()
    val confidenceLabel: String
        get() = when {
            confidence >= 0.75 -> "Alta"
            confidence >= 0.50 -> "Média"
            else -> "Baixa"
        }
}

/** Situação dos dados de um território: base da decisão de emitir ou não um alerta. */
data class DataStatus(
    val lastObservationAt: Instant?,
    val ageHours: Double?,
    val coverage24h: Double,
    val isStale: Boolean,
    val isInsufficient: Boolean,
)

sealed interface AlertEvaluation {
    val dataStatus: DataStatus

    data class Active(val alert: Alert, override val dataStatus: DataStatus) : AlertEvaluation
    data class NoRisk(override val dataStatus: DataStatus) : AlertEvaluation

    /** O app NÃO emite alerta: dados velhos ou cobertura baixa. */
    data class Suppressed(val reason: String, override val dataStatus: DataStatus) : AlertEvaluation
}

enum class DecisionAction(val label: String) {
    ACATAR("Acatar"),
    ADIAR("Adiar"),
    DESCARTAR("Descartar"),
}

data class Decision(
    val id: Long = 0,
    val alertId: String,
    val territoryId: String,
    val action: DecisionAction,
    val responsible: String,
    val note: String,
    val decidedAt: Instant,
)

/** Decisão com o retrato do alerta que o gestor viu (rastreabilidade). */
data class DecisionRecord(
    val decision: Decision,
    val alert: Alert,
)

/**
 * Limiares configuráveis. Faixas de chuva baseadas nos avisos de chuvas intensas do INMET
 * (até 50 mm/dia; 50 a 100 mm/dia; acima de 100 mm/dia). Saturação e cota são premissas da equipe.
 */
data class Thresholds(
    val forecastInfoMm: Double = 30.0,
    val forecastAttentionMm: Double = 50.0,
    val forecastCriticalMm: Double = 100.0,
    val peakInfoMmH: Double = 20.0,
    val peakAttentionMmH: Double = 30.0,
    val peakCriticalMmH: Double = 60.0,
    val saturation72hMm: Double = 60.0,
    val streamAttentionPct: Double = 80.0,
    val streamCriticalPct: Double = 100.0,
    val staleAfterHours: Double = 6.0,
    val minCoverage: Double = 0.70,
)

data class UserPreferences(
    val selectedTerritoryId: String?,
    val responsibleName: String,
    val criticalNotificationsEnabled: Boolean,
    val thresholds: Thresholds,
    val dataLoadedAt: Instant?,
)
