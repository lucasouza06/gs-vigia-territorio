package br.com.vigiaterritorio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.vigiaterritorio.domain.model.AlertFactor
import br.com.vigiaterritorio.domain.model.DataOrigin
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.ui.theme.VigiaTheme
import java.time.Instant

/** Mostra POR QUE o alerta existe: cada regra disparada, com valor, limiar e período, mais fonte e atualização. */
@Composable
fun JustificationPanel(
    factors: List<AlertFactor>,
    origin: DataOrigin,
    updatedAt: Instant,
    coverage24h: Double,
    confidenceText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.md)) {
        Text("Por que este alerta?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        factors.forEach { f ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(VigiaTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm)) {
                        Text("Regra ${f.rule}", style = MaterialTheme.typography.labelMedium)
                        LevelBadge(f.level)
                    }
                    Text(f.description, style = MaterialTheme.typography.titleMedium)
                    LabeledLine("Valor", f.measured)
                    LabeledLine("Limiar", f.threshold)
                    LabeledLine("Período", f.period)
                }
            }
        }
        HorizontalDivider()
        LabeledLine("Confiança", confidenceText)
        LabeledLine("Cobertura das últimas 24 h", "${(coverage24h * 100).toInt()}% das horas com leitura")
        LabeledLine("Última atualização", "${updatedAt.formatDateTime()} (${updatedAt.formatRelative()})")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm)) {
            Text("Fonte:", style = MaterialTheme.typography.bodyMedium)
            OriginTag(origin)
        }
    }
}

@Composable
fun LabeledLine(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Seleção de cidade, bairro ou área rural. */
@Composable
fun TerritorySelector(
    territories: List<Territory>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = territories.firstOrNull { it.id == selectedId }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = VigiaTheme.spacing.sm)) {
                Text(selected?.name ?: "Escolha o território", style = MaterialTheme.typography.titleMedium)
                selected?.let { Text(it.type.label, style = MaterialTheme.typography.labelMedium) }
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Trocar território")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            territories.forEach { t ->
                DropdownMenuItem(
                    text = { Text("${t.name} · ${t.type.label}") },
                    onClick = {
                        expanded = false
                        onSelect(t.id)
                    },
                )
            }
        }
    }
}

enum class StateKind { LOADING, EMPTY, ERROR, STALE, NOTIFICATIONS_OFF }

/**
 * Padroniza os estados de tela. LOADING, EMPTY e ERROR ocupam a área;
 * STALE e NOTIFICATIONS_OFF são faixas de aviso que não escondem os dados.
 */
@Composable
fun StateView(
    kind: StateKind,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    when (kind) {
        StateKind.LOADING -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.md)) {
                CircularProgressIndicator()
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
        StateKind.EMPTY, StateKind.ERROR -> Box(modifier.fillMaxSize().padding(VigiaTheme.spacing.xl), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.md)) {
                Icon(
                    if (kind == StateKind.ERROR) Icons.Outlined.ErrorOutline else Icons.Outlined.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(message, style = MaterialTheme.typography.bodyLarge)
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(onClick = onAction) { Text(actionLabel) }
                }
            }
        }
        StateKind.STALE, StateKind.NOTIFICATIONS_OFF -> Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(
                Modifier.padding(VigiaTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.md),
            ) {
                Icon(
                    when (kind) {
                        StateKind.NOTIFICATIONS_OFF -> Icons.Outlined.NotificationsOff
                        else -> if (message.startsWith("Dados insuficientes")) Icons.Outlined.CloudOff else Icons.Outlined.Schedule
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(Modifier.weight(1f)) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) { Text(actionLabel) }
                    }
                }
            }
        }
    }
}
