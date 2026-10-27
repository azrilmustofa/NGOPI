package kelompok.satu.kopiinggil

import android.content.Context
import androidx.annotation.StringRes
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// Mengubah error (Supabase, jaringan, server) jadi pesan bahasa Indonesia yang jelas untuk pengguna.
// Pesan asli tetap dicatat di logcat (tag "ErrorMessages") untuk keperluan debugging.
fun Throwable.toUserMessage(context: Context, @StringRes fallback: Int): String {
    android.util.Log.w("ErrorMessages", "Error asli", this)

    // Pesan dari create-payment sudah ditulis untuk pengguna.
    if (this is PaymentException) return message ?: context.getString(fallback)

    val chain = generateSequence(this) { it.cause }.toList()
    val isNetwork = chain.any {
        it is UnknownHostException || it is ConnectException || it is SocketTimeoutException ||
            it is InterruptedIOException || it::class.simpleName in NETWORK_ERRORS
    }
    if (isNetwork) return context.getString(R.string.err_network)

    val msg = chain.joinToString(" ") { it.message.orEmpty() }.lowercase()
    val res = when {
        "invalid login credentials" in msg -> R.string.err_login_invalid
        "already registered" in msg || "already been registered" in msg || "user_already_exists" in msg -> R.string.err_email_taken
        "email not confirmed" in msg -> R.string.err_email_unconfirmed
        "rate limit" in msg || "only request this after" in msg || "too many" in msg -> R.string.err_rate_limit
        "should be different" in msg || "same_password" in msg -> R.string.err_same_password
        "weak" in msg || "password should be at least" in msg -> R.string.err_weak_password
        "jwt expired" in msg || "refresh token" in msg || "session_not_found" in msg || "auth session missing" in msg -> R.string.err_session
        "otp" in msg || ("token" in msg && "expired" in msg) -> R.string.err_otp
        else -> fallback
    }
    return context.getString(res)
}

private val NETWORK_ERRORS = setOf("HttpRequestException", "HttpRequestTimeoutException", "ConnectTimeoutException")
