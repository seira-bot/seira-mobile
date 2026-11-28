package bot.seira.companion.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import bot.seira.companion.core.NetworkMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun vpnActive(): Boolean? {
        val manager = connectivity ?: return null
        val capabilities = runCatching {
            manager.activeNetwork?.let(manager::getNetworkCapabilities)
        }.getOrNull() ?: return null
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    override val changes: Flow<Unit> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            close()
            return@callbackFlow
        }
        var current: Network? = null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = current
                current = network
                if (previous != null && previous != network) trySend(Unit)
            }

            override fun onLost(network: Network) {
                if (current == network) {
                    current = null
                    trySend(Unit)
                }
            }
        }
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        awaitClose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
}
