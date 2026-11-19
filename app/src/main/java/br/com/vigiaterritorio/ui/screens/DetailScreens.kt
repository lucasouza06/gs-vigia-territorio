package br.com.vigiaterritorio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vigiaterritorio.di.AppContainer
import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.model.AlertEvaluation
import br.com.vigiaterritorio.domain.model.Decision
import br.com.vigiaterritorio.domain.model.DecisionAction
import br.com.vigiaterritorio.domain.model.Indicator
import br.com.vigiaterritorio.domain.model.KpiType
import br.com.vigiaterritorio.domain.usecase.IndicatorSeries
import br.com.vigiaterritorio.domain.usecase.TerritoryEvaluation
import br.com.vigiaterritorio.ui.components.AlertCard
import br.com.vigiaterritorio.ui.components.ChartStyle
import br.com.vigiaterritorio.ui.components.DecisionSheet
import br.com.vigiaterritorio.ui.components.JustificationPanel
import br.com.vigiaterritorio.ui.components.KpiCard
import br.com.vigiaterritorio.ui.components.LabeledLine
import br.com.vigiaterritorio.ui.components.OriginTag
import br.com.vigiaterritorio.ui.components.StateKind
import br.com.vigiaterritorio.ui.components.StateView
import br.com.vigiaterritorio.ui.components.TrendChart
import br.com.vigiaterritorio.ui.components.formatDateTime
import br.com.vigiaterritorio.ui.components.formatOne
import br.com.vigiaterritorio.ui.theme.VigiaTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// =====================================================================
// Detalhe do indicador
// =====================================================================

sealed interface IndicatorUiState {
    data object Loading : IndicatorUiState
    data class Error(val message: String) : IndicatorUiState
    data class Content(val series: IndicatorSeries) : IndicatorUiState
}

class IndicatorViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    // Só identificadores vêm pela rota; o objeto é relido do banco.
    private val territoryId: String = checkNotNull(handle["territoryId"])
    private val kpi: KpiType = KpiType.valueOf(checkNotNull(handle["kpi"]))

    val state: StateFlow<IndicatorUiState> = c.observeIndicatorSeries(territoryId, kpi)
        .map { series -> series?.let { IndicatorUiState.Content(it) } ?: IndicatorUiState.Error("Território não encontrado.") }
        .catch { emit(IndicatorUiState.Error("Não foi possível ler a série deste indicador.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IndicatorUiState.Loading)

    companion object {
        val Factory = vigiaFactory { c, h -> IndicatorViewModel(c, h) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorScreen(onBack: () -> Unit, vm: IndicatorViewModel = viewModel(factory = IndicatorViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column {
        TopAppBar(
            title = { ScreenTitle("Detalhe do indicador") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
        )
        when (val s = state) {
            IndicatorUiState.Loading -> StateView(StateKind.LOADING, "Montando o gráfico…")
            is IndicatorUiState.Error -> StateView(StateKind.ERROR, s.message, actionLabel = "Voltar", onAction = onBack)
            is IndicatorUiState.Content -> IndicatorContent(s.series)
        }
    }
}

@Composable
private fun IndicatorContent(series: IndicatorSeries) {
    val spacing = VigiaTheme.spacing
    val kpi = series.kpi
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text("${series.territory.name} · ${series.territory.type.label}", style = MaterialTheme.typography.titleLarge)
        KpiCard(
            title = kpi.type.label,
            value = kpi.value,
            unit = kpi.type.unit,
            trend = kpi.trend,
            status = kpi.status,
            origin = kpi.origin,
            onClick = null,
            modifier = Modifier.fillMaxWidth(),
        )
        val isRain = series.indicator == Indicator.CHUVA_MM_H
        if (series.observed.isEmpty() && series.forecast.isEmpty()) {
            StateView(StateKind.STALE, "Sem leituras deste indicador para o território. Não há sensor ou os dados não chegaram.")
        } else {
            val peakForecast = series.forecast.maxOfOrNull { it.value }
            TrendChart(
                observed = series.observed,
                forecast = series.forecast,
                threshold = series.threshold,
                thresholdLabel = series.thresholdLabel,
                unit = series.indicator.unit,
                style = if (isRain) ChartStyle.BARS else ChartStyle.LINE,
                summary = "Gráfico de ${series.indicator.label.lowercase()} das últimas 72 horas" +
                    (peakForecast?.let { " e ${if (isRain) "previsão" else "projeção"} com máximo de ${it.formatOne()} ${series.indicator.unit}" } ?: "") +
                    ". ${series.thresholdLabel}.",
            )
        }
        Card {
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                series.keyValues.forEach { (label, value) -> LabeledLine(label, value) }
                LabeledLine("Última leitura", series.lastObservationAt?.formatDateTime() ?: "—")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text("Fonte:", style = MaterialTheme.typography.bodyMedium)
                    OriginTag(series.origin)
                }
            }
        }
    }
}

// =====================================================================
// Detalhe do alerta + decisão humana
// =====================================================================

data class AlertUiState(
    val loading: Boolean = true,
    val evaluation: TerritoryEvaluation? = null,
    val existingDecision: Decision? = null,
    val sheetOpen: Boolean = false,
    val saving: Boolean = false,
    val formError: String? = null,
    val message: String? = null,
)

class AlertViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val territoryId: String = checkNotNull(handle["territoryId"])
    private val ui = MutableStateFlow(AlertUiState())
    val state: StateFlow<AlertUiState> = ui.asStateFlow()

    init {
        viewModelScope.launch {
            c.observeTerritoryEvaluation(territoryId).collect { evaluation ->
                ui.update { it.copy(loading = false, evaluation = evaluation) }
            }
        }
        observeDecision()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDecision() {
        viewModelScope.launch {
            state.map { (it.evaluation?.evaluation as? AlertEvaluation.Active)?.alert?.id }
                .distinctUntilChanged()
                .flatMapLatest { id -> if (id == null) flowOf(null) else c.decisions.observeDecisionFor(id) }
                .collect { decision -> ui.update { it.copy(existingDecision = decision) } }
        }
    }

    fun openSheet() = ui.update { it.copy(sheetOpen = true, formError = null) }
    fun closeSheet() = ui.update { it.copy(sheetOpen = false, formError = null) }
    fun messageShown() = ui.update { it.copy(message = null) }

    fun confirm(alert: Alert, action: DecisionAction, responsible: String, note: String) {
        viewModelScope.launch {
            ui.update { it.copy(saving = true, formError = null) }
            c.registerDecision(alert, action, responsible, note)
                .onSuccess {
                    c.preferences.setResponsibleName(responsible)
                    ui.update { it.copy(saving = false, sheetOpen = false, message = "Decisão registrada: ${action.label}.") }
                }
                .onFailure { e ->
                    ui.update { it.copy(saving = false, formError = e.message ?: "Não foi possível salvar a decisão.") }
                }
        }
    }

    companion object {
        val Factory = vigiaFactory { c, h -> AlertViewModel(c, h) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(onBack: () -> Unit, vm: AlertViewModel = viewModel(factory = AlertViewModel.Factory)) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column {
            TopAppBar(
                title = { ScreenTitle("Detalhe do alerta") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar") } },
            )
            val evaluation = state.evaluation
            when {
                state.loading -> StateView(StateKind.LOADING, "Avaliando o território…")
                evaluation == null -> StateView(StateKind.ERROR, "Território não encontrado.", actionLabel = "Voltar", onAction = onBack)
                else -> AlertContent(evaluation, state, vm)
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun AlertContent(te: TerritoryEvaluation, state: AlertUiState, vm: AlertViewModel) {
    val spacing = VigiaTheme.spacing
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        Text("${te.territory.name} · ${te.territory.type.label}", style = MaterialTheme.typography.titleLarge)
        when (val e = te.evaluation) {
            is AlertEvaluation.Suppressed -> StateView(StateKind.STALE, e.reason)
            is AlertEvaluation.NoRisk -> Text("O alerta não está mais ativo: as regras não indicam risco agora.", style = MaterialTheme.typography.bodyLarge)
            is AlertEvaluation.Active -> {
                val alert = e.alert
                AlertCard(alert, onClick = null)
                Card {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text("Recomendação (o app não executa ações)", style = MaterialTheme.typography.titleMedium)
                        Text(alert.recommendation, style = MaterialTheme.typography.bodyLarge)
                        LabeledLine("Válido até", alert.validUntil.formatDateTime())
                    }
                }
                JustificationPanel(
                    factors = alert.factors,
                    origin = alert.origin,
                    updatedAt = alert.dataUpdatedAt,
                    coverage24h = alert.coverage24h,
                    confidenceText = "${alert.confidenceLabel} (${alert.confidencePercent}%)",
                )
                state.existingDecision?.let { d ->
                    Card {
                        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text("Decisão já registrada", style = MaterialTheme.typography.titleMedium)
                            Text("${d.action.label} por ${d.responsible} em ${d.decidedAt.formatDateTime()}", style = MaterialTheme.typography.bodyMedium)
                            if (d.note.isNotBlank()) Text("Observação: ${d.note}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Button(onClick = vm::openSheet, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.existingDecision == null) "Registrar decisão" else "Registrar nova decisão")
                }
                if (state.sheetOpen) {
                    DecisionSheet(
                        alert = alert,
                        initialResponsible = te.preferences.responsibleName,
                        errorMessage = state.formError,
                        saving = state.saving,
                        onConfirm = { action, who, note -> vm.confirm(alert, action, who, note) },
                        onDismiss = vm::closeSheet,
                    )
                }
            }
        }
    }
}
