package br.com.vigiaterritorio.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.vigiaterritorio.ui.screens.AlertScreen
import br.com.vigiaterritorio.ui.screens.DashboardScreen
import br.com.vigiaterritorio.ui.screens.HistoryScreen
import br.com.vigiaterritorio.ui.screens.IndicatorScreen
import br.com.vigiaterritorio.ui.screens.SettingsScreen

/** Rotas: passamos só identificadores, nunca o objeto inteiro. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val INDICATOR = "indicator/{territoryId}/{kpi}"
    const val ALERT = "alert/{territoryId}"

    fun indicator(territoryId: String, kpi: String) = "indicator/$territoryId/$kpi"
    fun alert(territoryId: String) = "alert/$territoryId"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevels = listOf(
    TopLevel(Routes.DASHBOARD, "Painel", Icons.Outlined.Dashboard),
    TopLevel(Routes.HISTORY, "Histórico", Icons.Outlined.History),
    TopLevel(Routes.SETTINGS, "Configurações", Icons.Outlined.Settings),
)

@Composable
fun VigiaNavHost(openAlertFor: String?, onAlertOpened: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    // Toque na notificação: abre o detalhe do alerta do território.
    LaunchedEffect(openAlertFor) {
        if (openAlertFor != null) {
            nav.navigate(Routes.alert(openAlertFor)) { launchSingleTop = true }
            onAlertOpened()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevels.forEach { item ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { inner ->
        // O topo fica com a TopAppBar de cada tela (que já trata a barra de status).
        NavHost(
            navController = nav,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(bottom = inner.calculateBottomPadding()),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenIndicator = { territory, kpi -> nav.navigate(Routes.indicator(territory, kpi.name)) },
                    onOpenAlert = { territory -> nav.navigate(Routes.alert(territory)) },
                )
            }
            composable(
                Routes.INDICATOR,
                arguments = listOf(
                    navArgument("territoryId") { type = NavType.StringType },
                    navArgument("kpi") { type = NavType.StringType },
                ),
            ) { IndicatorScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.ALERT,
                arguments = listOf(navArgument("territoryId") { type = NavType.StringType }),
            ) { AlertScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}
