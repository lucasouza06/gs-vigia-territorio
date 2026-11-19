package br.com.vigiaterritorio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.KpiStatus
import br.com.vigiaterritorio.ui.theme.VigiaTheme
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- Selos ----------

fun AlertLevel.icon(): ImageVector = when (this) {
    AlertLevel.INFORMATIVO -> Icons.Outlined.Info
    AlertLevel.ATENCAO -> Icons.Outlined.WarningAmber
    AlertLevel.CRITICO -> Icons.Outlined.ErrorOutline
}

fun KpiStatus.icon(): ImageVector = when (this) {
    KpiStatus.NORMAL -> Icons.Outlined.CheckCircle
    KpiStatus.ATENCAO -> Icons.Outlined.WarningAmber
    KpiStatus.CRITICO -> Icons.Outlined.ErrorOutline
    KpiStatus.SEM_DADOS -> Icons.AutoMirrored.Outlined.HelpOutline
}

/** Nível sempre com ícone + rótulo: a cor nunca comunica o alerta sozinha. */
@Composable
fun LevelBadge(level: AlertLevel, modifier: Modifier = Modifier) {
    StatusChip(
        text = level.label,
        icon = level.icon(),
        color = VigiaTheme.status.of(level),
        description = "Nível ${level.label}",
        modifier = modifier,
    )
}

@Composable
fun StatusBadge(status: KpiStatus, modifier: Modifier = Modifier) {
    StatusChip(
        text = status.label,
        icon = status.icon(),
        color = VigiaTheme.status.of(status),
        description = "Situação ${status.label}",
        modifier = modifier,
    )
}

@Composable
private fun StatusChip(
    text: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(color, MaterialTheme.shapes.small)
            .padding(horizontal = VigiaTheme.spacing.sm, vertical = VigiaTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = VigiaTheme.status.onStatus, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = VigiaTheme.status.onStatus)
    }
}

/** Selo de origem do dado: identifica na tela o que é fictício. */
@Composable
fun OriginTag(origin: DataOrigin?, modifier: Modifier = Modifier) {
    if (origin == null) return
    val icon = if (origin == DataOrigin.SINTETICO) Icons.Outlined.Science else Icons.Outlined.Verified
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = VigiaTheme.spacing.sm, vertical = VigiaTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = origin.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Text(origin.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Formatação ----------

private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", ptBr)

fun Instant.formatDateTime(): String = dateTime.format(atZone(ZoneId.systemDefault()))

fun Instant.formatRelative(now: Instant = Instant.now()): String {
    val minutes = Duration.between(this, now).toMinutes()
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        minutes < 48 * 60 -> "há ${minutes / 60} h"
        else -> "há ${minutes / (24 * 60)} dias"
    }
}

fun Double.formatOne(): String {
    val r = Math.round(this * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else String.format(ptBr, "%.1f", r)
}
