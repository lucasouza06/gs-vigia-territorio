package br.com.vigiaterritorio.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.vigiaterritorio.MainActivity
import br.com.vigiaterritorio.R
import br.com.vigiaterritorio.domain.model.Alert
import br.com.vigiaterritorio.domain.repository.AlertNotifier

/**
 * Adaptador do recurso nativo. Isola NotificationManager e a permissão POST_NOTIFICATIONS.
 * Três estados: disponível (notifica), negado (canNotify = false → aviso na tela) e
 * instável (canal desligado pelo usuário → também canNotify = false).
 */
class AndroidAlertNotifier(private val context: Context) : AlertNotifier {

    init {
        createChannel()
    }

    override fun canNotify(): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        val channelOk = manager.getNotificationChannelCompat(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        return permissionOk && manager.areNotificationsEnabled() && channelOk
    }

    override fun notifyCritical(alert: Alert) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TERRITORY_ID, alert.territoryId)
        }
        val pending = PendingIntent.getActivity(
            context,
            alert.territoryId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reason = alert.factors.firstOrNull()?.let { "${it.description}: ${it.measured}" } ?: alert.recommendation
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(alert.title)
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\nConfiança ${alert.confidencePercent}% · ${alert.origin.label}. Toque para ver a justificativa e decidir."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permissão revogada entre a checagem e o envio: o alerta continua visível no painel.
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_critical_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_critical_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "alertas_criticos"
    }
}
