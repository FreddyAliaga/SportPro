package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.toMatchDateText
import pe.edu.esan.sportpro.data.model.toMatchTimeText
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.rememberNow

// US-010 · Vista "En vivo" para todos los miembros de la academia (RN-015: las reglas solo dejan leer a sus miembros).
// Los eventos llegan con un listener de Firestore (en segundos); el marcador se calcula en el teléfono (RN-012)
// y el minuto se actualiza cada segundo. Sin conexión se muestra el último estado conocido (caché del listener).
// PRIVACIDAD: jugadores con nombre corto; la observación solo la ven el DT, los operadores y el ADM.
@Composable
fun WatchMatchScreen(viewModel: LiveMatchViewModel, onBack: () -> Unit, onRecord: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val now by rememberNow(1_000)
    val match = state.match

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("En vivo")

        if (match == null) {
            Spacer(Modifier.height(16.dp))
            val error = state.loadErrors["partido"]
            when {
                error != null -> ErrorBox(error)
                !state.online -> OfflineBanner()
                else -> LoadingBox()
            }
            return@SportProScreen
        }
        Spacer(Modifier.height(4.dp))
        Subtitle("${match.teamName} vs ${match.rival}")
        Spacer(Modifier.height(16.dp))

        if (!state.online) {
            OfflineBanner()
            Spacer(Modifier.height(10.dp))
        }
        if (state.canRecord && state.pending > 0) {
            PendingBanner(state.pending)
            Spacer(Modifier.height(10.dp))
        }

        ScoreBoard(match, state.score, now, showClock = false)
        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }

        // Aún no empieza o fue cancelado: cuándo y dónde
        if (!match.hasLiveData) {
            Spacer(Modifier.height(12.dp))
            Subtitle(
                if (match.status == MatchStatus.CANCELLED) "Este partido fue cancelado."
                else "El partido aún no empieza" + (match.startsAt?.let { ": ${it.toMatchDateText()} a las ${it.toMatchTimeText()}" } ?: ".") +
                    " Aquí verás el marcador apenas empiece."
            )
        }

        // US-008: el DT del equipo y los operadores pueden ir a registrar desde aquí
        if (state.canRecord && match.status != MatchStatus.CANCELLED) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (match.isFinished) "Ver registro del partido" else "Registrar partido", onRecord)
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Cronología")
        Spacer(Modifier.height(10.dp))
        if (!state.eventsLoaded && state.loadErrors["eventos"] == null) LoadingBox()
        else EventTimeline(state.timeline, if (state.canSeeNotes) state.notes else emptyMap(), showAuthor = state.canSeeNotes)
        Spacer(Modifier.height(12.dp))
    }
}
