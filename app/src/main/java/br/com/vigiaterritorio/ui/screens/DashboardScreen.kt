package br.com.vigiaterritorio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.vigiaterritorio.di.AppContainer
import br.com.vigiaterritorio.domain.model.AlertEvaluation
import br.com.vigiaterritorio.domain.model.Kpi
import br.com.vigiaterritorio.domain.model.KpiType
import br.com.vigiaterritorio.domain.model.Territory
import br.com.vigiaterritorio.domain.usecase.DashboardData
import br.com.vigiaterritorio.platform.PermissionStatus
import br.com.vigiaterritorio.platform.rememberNotificationPermissionController
import br.com.vigiaterritorio.ui.components.AlertCard
import br.com.vigiaterritorio.ui.components.KpiCard
import br.com.vigiaterritorio.ui.components.StateKind
import br.com.vigiaterritorio.ui.components.StateView
import br.com.vigiaterritorio.ui.components.TerritorySelector
import br.com.vigiaterritorio.ui.components.formatDateTime
import br.com.vigiaterritorio.ui.components.formatRelative
import br.com.vigiaterritorio.ui.theme.VigiaTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

// ---------------- Estado ----------------

enum class NotificationBanner { NONE, NOT_ENABLED, BLOCKED }

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState
    data class Error(val message: String) : DashboardUiState
    data class Content(
        val territories: List<Territory>,
        val selected: Territory,
        val kpis: List<Kpi>,
        val evaluation: AlertEvaluation,
        val dataLoadedAt: Instant?,
        val notificationBanner: NotificationBanner,
    ) : DashboardUiState
}

private sealed interface SeedState {
    data object Loading : SeedState
    data object Done : SeedState
    data class Failed(val message: String) : SeedState
}

// ---------------- ViewModel ----------------

class DashboardViewModel(private val c: AppContainer) : ViewModel() {

    private val seedState = MutableStateFlow<SeedState>(SeedState.Loading)
    private val canNotify = MutableStateFlow(c.notifier.canNotify())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DashboardUiState> = seedState
        .flatMapLatest { seed ->
            when (seed) {
                SeedState.Loading -> flowOf(DashboardUiState.Loading)
                is SeedState.Failed -> flowOf(DashboardUiState.Error(seed.message))
                SeedState.Done -> combine(c.observeDashboard(), canNotify) { data, allowed -> data.toUiState(allowed) }
                    .catch { emit(DashboardUiState.Error("Não foi possível ler os dados locais.")) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState.Loading)

    init {
        seed()
    }

    fun retry() = seed()

    private fun seed() {
        viewModelScope.launch {
            seedState.value = SeedState.Loading
            runCatching { c.readings.ensureSeeded() }
                .onSuccess {
                    seedState.value = SeedState.Done
                    c.checkCriticalAlerts()
                }
                .onFailure { seedState.value = SeedState.Failed("Falha ao carregar a base local. Verifique o espaço do aparelho e tente de novo.") }
        }
    }

    fun onTerritorySelected(id: String) {
        viewModelScope.launch { c.preferences.selectTerritory(id) }
    }

    /** Ao voltar para a tela (ex.: depois das configurações do sistema) reavalia a permissão. */
    fun onResume() {
        canNotify.value = c.notifier.canNotify()
        if (seedState.value == SeedState.Done) viewModelScope.launch { c.checkCriticalAlerts() }
    }

    fun enableNotifications() {
        viewModelScope.launch {
            c.preferences.setCriticalNotificationsEnabled(true)
            canNotify.value = c.notifier.canNotify()
            c.checkCriticalAlerts()
        }
    }

    private fun DashboardData.toUiState(allowed: Boolean): DashboardUiState {
        val territory = selected ?: return DashboardUiState.Empty
        val eval = evaluation ?: return DashboardUiState.Empty
        return DashboardUiState.Content(
            territories = territories,
            selected = territory,
            kpis = kpis,
            evaluation = eval,
            dataLoadedAt = preferences.dataLoadedAt,
            notificationBanner = when {
                !preferences.criticalNotificationsEnabled -> NotificationBanner.NOT_ENABLED
                !allowed -> NotificationBanner.BLOCKED
                else -> NotificationBanner.NONE
            },
        )
    }

    companion object {
        val Factory = vigiaFactory { c, _ -> DashboardViewModel(c) }
    }
}

// ---------------- Tela ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenIndicator: (territoryId: String, kpi: KpiType) -> Unit,
    onOpenAlert: (territoryId: String) -> Unit,
    vm: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val permission = rememberNotificationPermissionController { granted -> if (granted) vm.enableNotifications() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permission.refresh()
        vm.onResume()
    }

    Column {
        TopAppBar(title = { Text("Vigia Território") })
        when (val s = state) {
            DashboardUiState.Loading -> StateView(StateKind.LOADING, "Carregando dados do território…")
            DashboardUiState.Empty -> StateView(StateKind.EMPTY, "Nenhum território cadastrado na base local.", actionLabel = "Recarregar", onAction = vm::retry)
            is DashboardUiState.Error -> StateView(StateKind.ERROR, s.message, actionLabel = "Tentar novamente", onAction = vm::retry)
            is DashboardUiState.Content -> DashboardContent(
                state = s,
                onSelectTerritory = vm::onTerritorySelected,
                onOpenIndicator = onOpenIndicator,
                onOpenAlert = onOpenAlert,
                onNotificationAction = {
                    when {
                        s.notificationBanner == NotificationBanner.BLOCKED && permission.status == PermissionStatus.GRANTED ->
                            permission.openAppNotificationSettings()
                        permission.status == PermissionStatus.GRANTED -> vm.enableNotifications()
                        else -> permission.request()
                    }
                },
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Content,
    onSelectTerritory: (String) -> Unit,
    onOpenIndicator: (String, KpiType) -> Unit,
    onOpenAlert: (String) -> Unit,
    onNotificationAction: () -> Unit,
) {
    val spacing = VigiaTheme.spacing
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        fullWidth("selector") {
            TerritorySelector(state.territories, state.selected.id, onSelectTerritory)
        }
        fullWidth("updated") {
            val loaded = state.dataLoadedAt
            Text(
                if (loaded == null) "Base ainda não carregada" else "Base carregada em ${loaded.formatDateTime()} (${loaded.formatRelative()})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        (state.evaluation as? AlertEvaluation.Suppressed)?.let { suppressed ->
            fullWidth("suppressed") { StateView(StateKind.STALE, suppressed.reason) }
        }
        when (state.notificationBanner) {
            NotificationBanner.NOT_ENABLED -> fullWidth("notif") {
                StateView(
                    StateKind.NOTIFICATIONS_OFF,
                    "Ative os alertas críticos para ser avisado mesmo com o app fechado.",
                    actionLabel = "Ativar alertas críticos",
                    onAction = onNotificationAction,
                )
            }
            NotificationBanner.BLOCKED -> fullWidth("notif") {
                StateView(
                    StateKind.NOTIFICATIONS_OFF,
                    "Notificações bloqueadas: os alertas críticos só aparecem dentro do app.",
                    actionLabel = "Permitir notificações",
                    onAction = onNotificationAction,
                )
            }
            NotificationBanner.NONE -> Unit
        }

        fullWidth("alert-title") { SectionTitle("Alerta") }
        fullWidth("alert") {
            when (val e = state.evaluation) {
                is AlertEvaluation.Active -> AlertCard(e.alert, onClick = { onOpenAlert(state.selected.id) })
                is AlertEvaluation.NoRisk -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = VigiaTheme.status.normal)
                    Text("Nenhum risco de alagamento previsto para as próximas 24 horas.", style = MaterialTheme.typography.bodyLarge)
                }
                is AlertEvaluation.Suppressed -> Text(
                    "Sem alerta: o app não prevê com dados insuficientes ou desatualizados.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        fullWidth("kpi-title") { SectionTitle("Indicadores") }
        items(state.kpis, key = { it.type.name }) { kpi ->
            KpiCard(
                title = kpi.type.label,
                value = kpi.value,
                unit = kpi.type.unit,
                trend = kpi.trend,
                status = kpi.status,
                origin = kpi.origin,
                onClick = { onOpenIndicator(state.selected.id, kpi.type) },
            )
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = VigiaTheme.spacing.sm).semantics { heading() })
}
