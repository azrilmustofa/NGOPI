package kelompok.satu.kopiinggil

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Sinyal setiap kali internet tersambung (termasuk sekali di awal kalau sedang online).
fun Context.onlineSignals(): Flow<Unit> = callbackFlow {
    val cm = getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(Unit)
        }
    }
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}

// Jalankan [block] berulang tiap [intervalMs], atau lebih cepat begitu koneksi tersambung lagi
// (mis. mode pesawat dimatikan). Berhenti kalau [block] mengembalikan false.
suspend fun Context.pollEvery(intervalMs: Long, block: suspend () -> Boolean) = coroutineScope {
    val wake = Channel<Unit>(Channel.CONFLATED)
    val watcher = launch { onlineSignals().collect { wake.trySend(Unit) } }
    try {
        while (block()) {
            withTimeoutOrNull(intervalMs) { wake.receive() }
        }
    } finally {
        watcher.cancel()
    }
}
