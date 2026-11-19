package br.com.vigiaterritorio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vigiaterritorio.di.AppContainer
import br.com.vigiaterritorio.domain.model.DecisionRecord
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.model.Thresholds
import br.com.vigiaterritorio.domain.model.UserPreferences
import br.com.vigiaterritorio.platform.PermissionStatus
import br.com.vigiaterritorio.platform.rememberNotificationPermissionController
import br.com.vigiaterritorio.ui.components.LabeledLine
import br.com.vigiaterritorio.ui.components.LevelBadge
import br.com.vigiaterritorio.ui.components.StateKind
import br.com.vigiaterritorio.ui.components.StateView
import br.com.vigiaterritorio.ui.components.TerritorySelector
import br.com.vigiaterritorio.ui.components.formatDateTime
import br.com.vigiaterritorio.ui.components.formatOne
import br.com.vigiaterritorio.ui.theme.VigiaTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// =====================================================================
// Histórico de decisões
// =====================================================================

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Error(val message: String) : HistoryUiState
    data class Content(val records: List<DecisionRecord>) : HistoryUiState
}

class HistoryViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<HistoryUiState> = c.decisions.observeHistory()
        .map<List<DecisionRecord>, HistoryUiState> { HistoryUiState.Content(it) }
        .catch { emit(HistoryUiState.Error("Não foi possível ler o histórico.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState.Loading)

    companion object {
        val Factory = vigiaFactory { c, _ -> HistoryViewModel(c) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column {
        TopAppBar(title = { ScreenTitle("Histórico de decisões") })
        when (val s = state) {
            HistoryUiState.Loading -> StateView(StateKind.LOADING, "Carregando histórico…")
            is HistoryUiState.Error -> StateView(StateKind.ERROR, s.message)
            is HistoryUiState.Content ->
                if (s.records.isEmpty()) {
                    StateView(StateKind.EMPTY, "Nenhuma decisão registrada ainda. Abra um alerta no painel para decidir.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(VigiaTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.md),
                    ) {
                        items(s.records, key = { it.decision.id }) { DecisionItem(it) }
                    }
                }
        }
    }
}

@Composable
private fun DecisionItem(record: DecisionRecord) {
    val d = record.decision
    val a = record.alert
    // Um único item por decisão, com descrição completa e única para o leitor de tela
    // (achado do Accessibility Scanner: cards com textos repetidos).
    val spoken = "Decisão ${d.action.label} sobre alerta ${a.level.label} em ${a.territoryName}, " +
        "${d.decidedAt.formatDateTime()}, por ${d.responsible}." +
        (if (d.note.isNotBlank()) " Observação: ${d.note}." else " Sem observação.") +
        " Confiança do alerta ${a.confidencePercent} por cento."
    Card(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Column(Modifier.padding(VigiaTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VigiaTheme.spacing.sm)) {
                LevelBadge(a.level)
                Text(d.action.label, style = MaterialTheme.typography.titleMedium)
            }
            Text(a.territoryName, style = MaterialTheme.typography.bodyLarge)
            Text("${d.decidedAt.formatDateTime()} · ${d.responsible}", style = MaterialTheme.typography.labelMedium)
            if (d.note.isNotBlank()) Text("Observação: ${d.note}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Alerta visto: confiança ${a.confidencePercent}% · ${a.factors.joinToString(", ") { it.rule }} · ${a.origin.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// =====================================================================
// Configurações
// =====================================================================

data class SettingsUiState(
    val territories: List<Territory> = emptyList(),
    val preferences: UserPreferences? = null,
)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(c.readings.observeTerritories(), c.preferences.preferences) { t, p -> SettingsUiState(t, p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun selectTerritory(id: String) = launch { c.preferences.selectTerritory(id) }
    fun saveResponsible(name: String) = launch {
        c.preferences.setResponsibleName(name)
        _message.value = "Responsável salvo."
    }

    fun setNotifications(enabled: Boolean) = launch {
        c.preferences.setCriticalNotificationsEnabled(enabled)
        if (enabled) c.checkCriticalAlerts()
    }

    fun changeAttention(delta: Double) = updateThresholds { t ->
        t.copy(forecastAttentionMm = (t.forecastAttentionMm + delta).coerceIn(20.0, t.forecastCriticalMm - 5))
    }

    fun changeCritical(delta: Double) = updateThresholds { t ->
        t.copy(forecastCriticalMm = (t.forecastCriticalMm + delta).coerceIn(t.forecastAttentionMm + 5, 200.0))
    }

    fun changeSaturation(delta: Double) = updateThresholds { t ->
        t.copy(saturation72hMm = (t.saturation72hMm + delta).coerceIn(20.0, 150.0))
    }

    fun resetThresholds() = launch { c.preferences.setThresholds(Thresholds()) }

    fun reloadData() = launch {
        runCatching { c.readings.reseed() }
            .onSuccess { _message.value = "Base sintética recarregada." }
            .onFailure { _message.value = "Falha ao recarregar a base." }
    }

    /** LGPD: apaga decisões, retratos de alertas e o nome do responsável guardados no aparelho. */
    fun clearPersonalData() = launch {
        c.decisions.clearAll()
        c.preferences.clearPersonalData()
        _message.value = "Histórico e dados pessoais apagados."
    }

    fun messageShown() {
        _message.value = null
    }

    private fun updateThresholds(change: (Thresholds) -> Thresholds) =
        launch {
            val current = state.value.preferences?.thresholds ?: return@launch
            c.preferences.setThresholds(change(current))
        }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = vigiaFactory { c, _ -> SettingsViewModel(c) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermissionController { granted -> if (granted) vm.setNotifications(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permission.refresh() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val spacing = VigiaTheme.spacing
    val prefs = state.preferences

    Column {
        TopAppBar(title = { ScreenTitle("Configurações") })
        if (prefs == null) {
            StateView(StateKind.LOADING, "Carregando preferências…")
            return@Column
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            message?.let {
                StateView(StateKind.STALE, it, actionLabel = "OK", onAction = vm::messageShown)
            }

            SectionTitle("Território")
            TerritorySelector(state.territories, prefs.selectedTerritoryId ?: state.territories.firstOrNull()?.id, vm::selectTerritory)

            SectionTitle("Responsável pelas decisões")
            var name by rememberSaveable(prefs.responsibleName) { mutableStateOf(prefs.responsibleName) }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.saveResponsible(name) }) { Text("Salvar nome") }

            SectionTitle("Alertas críticos")
            val enabled = prefs.criticalNotificationsEnabled && permission.status == PermissionStatus.GRANTED
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Notificar alertas críticos", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Usado só para alertas de nível crítico, uma vez por alerta.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        when {
                            !on -> vm.setNotifications(false)
                            permission.status == PermissionStatus.GRANTED -> vm.setNotifications(true)
                            else -> permission.request()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Notificar alertas críticos" },
                )
            }
            when (permission.status) {
                PermissionStatus.DENIED -> Text(
                    "Permissão negada. Sem ela, os alertas críticos aparecem só dentro do app. Toque no botão para pedir de novo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PermissionStatus.PERMANENTLY_DENIED -> Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        "Permissão negada permanentemente. Ative as notificações do Vigia Território nas configurações do sistema.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = permission::openAppNotificationSettings) { Text("Abrir configurações do sistema") }
                }
                else -> Unit
            }

            SectionTitle("Limiares de alerta")
            Text(
                "Faixas padrão baseadas nos avisos de chuvas intensas do INMET. Alterações valem na hora para todos os territórios.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Stepper("Atenção: chuva prevista em 24 h", "${prefs.thresholds.forecastAttentionMm.formatOne()} mm", { vm.changeAttention(-5.0) }, { vm.changeAttention(5.0) })
            Stepper("Crítico: chuva prevista em 24 h", "${prefs.thresholds.forecastCriticalMm.formatOne()} mm", { vm.changeCritical(-10.0) }, { vm.changeCritical(10.0) })
            Stepper("Solo saturado: acumulado em 72 h", "${prefs.thresholds.saturation72hMm.formatOne()} mm", { vm.changeSaturation(-5.0) }, { vm.changeSaturation(5.0) })
            TextButton(onClick = vm::resetThresholds) { Text("Restaurar padrões") }

            SectionTitle("Fontes de dados")
            Card {
                Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    LabeledLine("Base", "Sintética, gerada pelo script tools/generate_synthetic_data.py (seed 2026)")
                    LabeledLine("Licença", "CC0 (domínio público)")
                    LabeledLine("Formato", "Chuva em mm por hora, no padrão das estações automáticas do INMET; nível de córrego em % da cota")
                    LabeledLine("Territórios", "Fictícios (município de Nova Aurora)")
                    LabeledLine("Privacidade", "O app não usa internet. Nenhum dado sai do aparelho.")
                    LabeledLine("Carga atual", prefs.dataLoadedAt?.formatDateTime() ?: "—")
                }
            }
            OutlinedButton(onClick = vm::reloadData, modifier = Modifier.fillMaxWidth()) { Text("Recarregar base sintética") }

            HorizontalDivider()
            SectionTitle("Seus dados (LGPD)")
            Text(
                "Ficam só neste aparelho: o nome do responsável e as decisões com observações. Nada é enviado a terceiros.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { confirmClear = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apagar histórico e dados pessoais") }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Apagar dados?") },
            text = { Text("Todas as decisões registradas e o nome do responsável serão apagados deste aparelho. Não é possível desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearPersonalData()
                }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }

    LaunchedEffect(Unit) { permission.refresh() }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { stateDescription = value })
        }
        IconButton(onClick = onMinus) { Icon(Icons.Outlined.Remove, contentDescription = "Diminuir $label") }
        IconButton(onClick = onPlus) { Icon(Icons.Outlined.Add, contentDescription = "Aumentar $label") }
    }
}
