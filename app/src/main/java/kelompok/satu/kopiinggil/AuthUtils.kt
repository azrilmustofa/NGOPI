package kelompok.satu.kopiinggil

import android.content.Context
import android.content.Intent
import android.util.Patterns
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun AppCompatActivity.applyEdgeToEdge(rootId: Int) {
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(rootId)) { v, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        insets
    }
}

fun AppCompatActivity.openMainAndClearStack() {
    CartManager.unload()
    OrderManager.clear()
    startActivity(
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}

fun AppCompatActivity.openStaffDashboardAndClearStack() {
    CartManager.unload()
    OrderManager.clear()
    startActivity(
        Intent(this, StaffHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
}

fun String.isValidEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun String.isValidPhone(): Boolean = replace(Regex("[\\s-]"), "").matches(Regex("\\d{9,13}"))

private const val AUTH_PREFS = "auth_prefs"
private const val KEY_REMEMBER_ME = "remember_me"

fun Context.isRememberMe(): Boolean =
    getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REMEMBER_ME, false)

fun Context.setRememberMe(value: Boolean) {
    getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REMEMBER_ME, value).apply()
}
