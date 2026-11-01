package pe.edu.esan.sportpro.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Revisa si el teléfono tiene internet ANTES de hablar con el servidor.
// ¿Por qué? Sin internet, Firebase espera unos 10 segundos antes de rendirse; con esta revisión
// el mensaje "No hay conexión…" aparece de inmediato.
// Solo se usa en operaciones que necesitan al servidor (transacciones, Source.SERVER, registro e
// inicio de sesión). Los listeners en tiempo real y la caché offline no la usan.
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
}
