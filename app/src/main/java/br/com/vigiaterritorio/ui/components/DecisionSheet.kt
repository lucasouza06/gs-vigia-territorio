package br.com.vigiaterritorio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.DecisionAction
import br.com.vigiaterritorio.ui.theme.VigiaTheme

/**
 * Registro da decisão humana. O componente só coleta a escolha; quem valida é o caso de uso
 * RegisterDecision (o erro volta por [errorMessage]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionSheet(
    alert: Alert,
    initialResponsible: String,
    errorMessage: String?,
    saving: Boolean,
    onConfirm: (action: DecisionAction, responsible: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // rememberSaveable: o que foi digitado sobrevive à rotação e à morte do processo.
    var action by rememberSaveable { mutableStateOf(DecisionAction.ACATAR) }
    var responsible by rememberSaveable { mutableStateOf(initialResponsible) }
    var note by rememberSaveable { mutableStateOf("") }
    val noteRequired = alert.level == AlertLevel.CRITICO && action != DecisionAction.ACATAR

    ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VigiaTheme.spacing.xl)
                .padding(bottom = VigiaTheme.spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.md),
        ) {
            Text("Registrar decisão", style = MaterialTheme.typography.titleLarge)
            LevelBadge(alert.level)
            Text(alert.title, style = MaterialTheme.typography.bodyMedium)
            Text("Recomendação do app: ${alert.recommendation}", style = MaterialTheme.typography.bodyMedium)

            Column(Modifier.selectableGroup()) {
                DecisionAction.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .selectable(selected = action == option, onClick = { action = option }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = action == option, onClick = null)
                        Column(Modifier.padding(start = VigiaTheme.spacing.sm)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(option.hint(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = responsible,
                onValueChange = { responsible = it },
                label = { Text("Responsável") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(if (noteRequired) "Observação (obrigatória)" else "Observação") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { error(errorMessage) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Button(onClick = { onConfirm(action, responsible, note) }, enabled = !saving) {
                    Text("Confirmar decisão")
                }
            }
        }
    }
}

private fun DecisionAction.hint(): String = when (this) {
    DecisionAction.ACATAR -> "Vou agir conforme a recomendação"
    DecisionAction.ADIAR -> "Vou reavaliar mais tarde"
    DecisionAction.DESCARTAR -> "Entendo que o alerta não procede"
}
