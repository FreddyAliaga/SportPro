package pe.edu.esan.sportpro.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Revisa si el teléfono tiene internet ANTES de hablar con el servidor.
// ¿Por qué? Sin internet, Firebase espera unos 10 segundos antes de rendirse; con esta revisión
// el mensaje "No hay conexión…" aparece de inmediato.
// isOnline() solo se usa en operaciones que necesitan al servidor (transacciones, Source.SERVER, registro e
// inicio de sesión) y en el cronómetro del partido (Iniciar, Pausar, Reanudar y Finalizar, US-008), porque la regla
// lo compara con la hora del servidor. Los listeners en tiempo real, la caché offline y los EVENTOS del partido
// no la usan: los eventos se guardan sin conexión y se envían solos al reconectar.
object ConnectionChecker {

    private var connectivityManager: ConnectivityManager? = null

    // Se llama una vez en MainActivity.onCreate (se guarda el contexto de la app, no el de la Activity)
    fun init(context: Context) {
        connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    }

    // true = hay una red con internet que Android comprobó que funciona (VALIDATED).
    // Si aún no se inicializó, devuelve true y Firebase decide (como antes).
    fun isOnline(): Boolean {
        val manager = connectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // El mismo mensaje de siempre, listo para devolver desde cualquier repositorio
    fun <T> noConnection(): Result<T> = Result.failure(Exception(FirebaseAuthManager.NO_CONNECTION_MESSAGE))

    // US-010 · Avisa cada vez que se pierde o se recupera la conexión (true = hay internet).
    // Solo sirve para MOSTRAR "Sin conexión": nunca bloquea la vista ni el registro, que siguen
    // funcionando con la caché de Firestore.
    fun onlineFlow(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline()) }
            // Se perdió la red por defecto: sin conexión (si entra otra red, onAvailable avisa de nuevo)
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }
        trySend(isOnline())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
