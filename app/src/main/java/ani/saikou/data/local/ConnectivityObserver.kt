package ani.saikou.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Emits true whenever ANY network has internet capability; false only when
     * no networks are available. Tracks all registered networks — avoids the
     * race where onLost for a transient network incorrectly reports offline
     * while another network (e.g. cellular) is still connected.
     */
    val isConnected: Flow<Boolean> = callbackFlow {
        val activeNetworks = mutableSetOf<Network>()

        fun emitCurrentState() {
            trySend(activeNetworks.isNotEmpty())
        }

        fun hasInternet(network: Network): Boolean {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (hasInternet(network)) {
                    activeNetworks.add(network)
                    emitCurrentState()
                }
            }

            override fun onLost(network: Network) {
                activeNetworks.remove(network)
                emitCurrentState()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                if (hasInternet) {
                    activeNetworks.add(network)
                } else {
                    activeNetworks.remove(network)
                }
                emitCurrentState()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Seed the set with any networks already online at subscription time
        try {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.forEach { network ->
                if (hasInternet(network)) activeNetworks.add(network)
            }
        } catch (_: Exception) { /* API-level variance */ }
        emitCurrentState()

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
