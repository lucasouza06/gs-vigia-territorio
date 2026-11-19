package br.com.vigiaterritorio.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Os caminhos exigidos: concedida, negada, negada para sempre (e ainda não perguntada). */
enum class PermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_ASKED }

/**
 * Encapsula a API de permissões do Android. A tela só lê [status] e chama [request].
 * Regra prática da disciplina: pedir no toque que precisa da permissão, com o motivo na tela.
 */
@Stable
class NotificationPermissionController internal constructor(private val context: Context) {

    var status by mutableStateOf(PermissionStatus.NOT_ASKED)
        private set

    internal var asked = false
    internal var launchRequest: () -> Unit = {}

    fun request() {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> refresh()
            status == PermissionStatus.PERMANENTLY_DENIED -> openAppNotificationSettings()
            else -> launchRequest()
        }
    }

    /** Recalcula o estado (chamar também ao voltar das configurações do sistema). */
    fun refresh() {
        status = currentStatus()
    }

    fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun currentStatus(): PermissionStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionStatus.GRANTED
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return PermissionStatus.GRANTED
        val activity = context.findActivity() ?: return PermissionStatus.DENIED
        val showRationale = activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        return when {
            showRationale -> PermissionStatus.DENIED
            asked -> PermissionStatus.PERMANENTLY_DENIED // recusou e o sistema não deixa perguntar de novo
            else -> PermissionStatus.NOT_ASKED
        }
    }
}

@Composable
fun rememberNotificationPermissionController(
    onResult: (granted: Boolean) -> Unit = {},
): NotificationPermissionController {
    val context = LocalContext.current
    val controller = remember(context) { NotificationPermissionController(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        controller.asked = true
        controller.refresh()
        onResult(granted)
    }
    SideEffect {
        controller.launchRequest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    LaunchedEffect(controller) { controller.refresh() }
    return controller
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
