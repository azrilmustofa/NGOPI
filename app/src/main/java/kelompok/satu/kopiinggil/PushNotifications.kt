package kelompok.satu.kopiinggil

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PUSH_PREFS = "push_notifications"
private const val PUSH_TOKEN = "fcm_token"
private const val CHANNEL_ORDERS = "orders"
private const val NOTIFICATION_PERMISSION_REQUEST = 20

fun AppCompatActivity.initializePushNotifications() {
    PushNotifications.createChannel(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST,
        )
    }
    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
        PushNotifications.remember(this, token)
        lifecycleScope.launch {
            runCatching { PushNotifications.register(token) }
                .onFailure { android.util.Log.w("PushNotifications", "Gagal mendaftarkan token", it) }
        }
    }
}

object PushNotifications {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ORDERS,
            context.getString(R.string.push_channel_orders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.push_channel_orders_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun remember(context: Context, token: String) {
        context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
            .edit().putString(PUSH_TOKEN, token).apply()
    }

    fun tokenChanged(context: Context, token: String) {
        remember(context, token)
        backgroundScope.launch {
            runCatching {
                supabase.auth.awaitInitialization()
                register(token)
            }.onFailure { android.util.Log.w("PushNotifications", "Gagal memperbarui token", it) }
        }
    }

    suspend fun register(token: String) {
        if (supabase.auth.currentUserOrNull() == null) return
        supabase.postgrest.rpc("register_push_token", buildJsonObject { put("p_token", token) })
    }

    suspend fun unregister(context: Context) {
        val prefs = context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(PUSH_TOKEN, null)
        if (token != null) {
            runCatching {
                supabase.postgrest.rpc("unregister_push_token", buildJsonObject { put("p_token", token) })
            }
        }
        prefs.edit().remove(PUSH_TOKEN).apply()
        FirebaseMessaging.getInstance().deleteToken()
    }

    fun show(context: Context, data: Map<String, String>) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val order = data["order_public_id"].orEmpty()
        val intent = Intent(context, LoginActivity::class.java).apply {
            putExtra(LoginActivity.EXTRA_PUSH_AUDIENCE, data["audience"])
            putExtra(LoginActivity.EXTRA_PUSH_ORDER, order)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            order.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ORDERS)
            .setSmallIcon(R.drawable.ic_coffee)
            .setContentTitle(data["title"] ?: context.getString(R.string.app_name))
            .setContentText(data["body"].orEmpty())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(order.hashCode(), notification)
    }
}

class KopiInggilMessagingService : FirebaseMessagingService() {
    // shortcut: HTTP v1 masih menargetkan registration token; pindah ke FID saat alur server AndroidConfigV2 stabil.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        PushNotifications.tokenChanged(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.show(applicationContext, message.data)
    }
}
