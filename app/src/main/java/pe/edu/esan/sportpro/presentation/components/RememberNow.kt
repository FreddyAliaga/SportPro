package pe.edu.esan.sportpro.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

// Hora actual que se renueva cada intervalMs (30 segundos por defecto), para que los plazos ("Responde hasta…")
// y el inicio del partido se vean al día sin salir de la pantalla. El cronómetro del partido (US-008 y US-010)
// usa 1 segundo. Es solo un reloj de la interfaz: no lee datos.
@Composable
fun rememberNow(intervalMs: Long = 30_000): State<Long> = produceState(System.currentTimeMillis(), intervalMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(intervalMs)
    }
}
