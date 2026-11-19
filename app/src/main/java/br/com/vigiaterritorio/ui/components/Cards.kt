package br.com.vigiaterritorio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.KpiStatus
import br.com.vigiaterritorio.domain.model.Trend
import br.com.vigiaterritorio.ui.theme.VigiaTheme

private fun Trend.icon(): ImageVector = when (this) {
    Trend.SUBINDO -> Icons.AutoMirrored.Outlined.TrendingUp
    Trend.DESCENDO -> Icons.AutoMirrored.Outlined.TrendingDown
    Trend.ESTAVEL -> Icons.AutoMirrored.Outlined.TrendingFlat
    Trend.DESCONHECIDA -> Icons.AutoMirrored.Outlined.HelpOutline
}

/** Exibe um indicador. Descreve, não decide: status e tendência chegam prontos do domínio. */
@Composable
fun KpiCard(
    title: String,
    value: Double?,
    unit: String,
    trend: Trend,
    status: KpiStatus,
    origin: DataOrigin?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val valueText = value?.formatOne() ?: "—"
    val spoken = "$title: ${if (value == null) "sem dados" else "$valueText $unit"}, situação ${status.label}, " +
        "tendência ${trend.label}" + if (onClick != null) ". Toque para ver o gráfico" else ""
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(VigiaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs)) {
                Text(valueText, style = MaterialTheme.typography.headlineMedium)
                Text(unit, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs)) {
                Icon(trend.icon(), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(trend.label, style = MaterialTheme.typography.bodyMedium)
            }
            StatusBadge(status)
            OriginTag(origin)
        }
    }
    // clearAndSetSemantics apaga a ação de clique dos filhos: ela é recolocada aqui para o TalkBack.
    val semanticsModifier = modifier.clearAndSetSemantics {
        contentDescription = spoken
        if (onClick != null) {
            role = Role.Button
            this.onClick(label = "Ver gráfico") { onClick(); true }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = semanticsModifier) { content() }
    } else {
        Card(modifier = semanticsModifier) { content() }
    }
}

/** Resumo do alerta com nível (ícone + rótulo), confiança e acesso à justificativa. */
@Composable
fun AlertCard(alert: Alert, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val color = VigiaTheme.status.of(alert.level)
    val spoken = "${alert.title}. Motivo: ${alert.factors.joinToString("; ") { "${it.description}, ${it.measured}" }}. " +
        "Confiança ${alert.confidenceLabel.lowercase()}, ${alert.confidencePercent} por cento. ${alert.origin.label}." +
        if (onClick != null) " Toque para ver a justificativa e registrar a decisão." else ""
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(VigiaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm)) {
                LevelBadge(alert.level)
                OriginTag(alert.origin)
            }
            Text(alert.title, style = MaterialTheme.typography.titleMedium)
            alert.factors.firstOrNull()?.let {
                Text("${it.description}: ${it.measured}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Confiança ${alert.confidenceLabel.lowercase()} (${alert.confidencePercent}%) · ${alert.factors.size} regra(s) disparada(s)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onClick != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs)) {
                    Text("Ver justificativa e decidir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    val m = modifier
        .fillMaxWidth()
        .clearAndSetSemantics {
            contentDescription = spoken
            if (onClick != null) {
                role = Role.Button
                this.onClick(label = "Ver justificativa") { onClick(); true }
            }
        }
    val border = BorderStroke(2.dp, color)
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = m, border = border) { body() }
    } else {
        OutlinedCard(modifier = m, border = border) { body() }
    }
}
