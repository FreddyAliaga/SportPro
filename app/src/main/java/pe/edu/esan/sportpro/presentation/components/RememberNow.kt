package pe.edu.esan.sportpro.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

// Hora actual que se renueva cada 30 segundos, para que los plazos ("Responde hasta…") y el inicio
// del partido se vean al día sin salir de la pantalla. Es solo un reloj de la interfaz: no lee datos.
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(30_000)
        value = System.currentTimeMillis()
    }
}
