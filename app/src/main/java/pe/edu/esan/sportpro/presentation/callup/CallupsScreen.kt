package pe.edu.esan.sportpro.presentation.callup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.CALLUP_REASON_MAX
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.toDeadlineText
import pe.edu.esan.sportpro.data.model.toMatchDateText
import pe.edu.esan.sportpro.data.model.toMatchTimeText
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.Illustration
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProDialog
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.rememberNow
import pe.edu.esan.sportpro.presentation.match.MatchStatusChip
import pe.edu.esan.sportpro.presentation.match.ResponseChip
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario

// US-006 · Convocatorias del apoderado (una tarjeta por cada hijo convocado) y del jugador mayor (la suya).
// Se responde "Confirmo" o "No puedo" (motivo opcional que solo ve el DT) dentro del plazo de la academia.
@Composable
fun CallupsScreen(viewModel: CallupViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val now by rememberNow()
    var declining by remember { mutableStateOf<CallupCard?>(null) }
    var reason by remember { mutableStateOf("") }

    declining?.let { card ->
        SportProDialog(
            title = "${card.player.shortName} no puede ir",
            confirmText = "Enviar",
            onConfirm = {
                viewModel.respond(card, CallupResponse.DECLINED, reason)
                declining = null
            },
            onDismiss = { declining = null }
        ) {
            Column {
                Text(
                    "Si quieres, cuéntale al DT el motivo. Solo lo verá el DT.",
                    color = TextoPrincipal,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(12.dp))
                SportProTextField(
                    value = reason,
                    onValueChange = { reason = it.take(CALLUP_REASON_MAX) },
                    label = "Motivo (opcional)",
                    placeholder = "Viaje familiar"
                )
                Text("${reason.length}/$CALLUP_REASON_MAX", color = TextoSecundario, fontSize = 12.sp)
            }
        }
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Convocatorias")
        Spacer(Modifier.height(8.dp))
        Subtitle(
            if (state.profile?.role == "PAD") "Responde por cada uno de tus jugadores convocados."
            else "Responde si puedes ir a los partidos a los que te convocaron."
        )
        Spacer(Modifier.height(16.dp))

        state.loadError?.let {
            ErrorBox(it)
            Spacer(Modifier.height(12.dp))
        }
        Messages(state.errorMessage, state.infoMessage)

        if (!state.loaded) {
            if (state.loadError == null) LoadingBox()
            return@SportProScreen
        }
        val upcoming = state.upcoming(now)
        val past = state.past(now)
        if (upcoming.isEmpty() && past.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Illustration(icon = Icons.Outlined.ConfirmationNumber, height = 160.dp)
            Spacer(Modifier.height(16.dp))
            Subtitle("Por ahora no hay convocatorias. Te avisaremos cuando llegue una.")
            return@SportProScreen
        }
        Spacer(Modifier.height(8.dp))
        if (upcoming.isNotEmpty()) {
            SectionLabel("Próximas")
            Spacer(Modifier.height(10.dp))
            upcoming.forEach { card ->
                CallupCardView(
                    card = card,
                    showPlayer = state.profile?.role == "PAD",
                    confirmHours = state.confirmHours,
                    now = now,
                    saving = state.respondingKey == card.key,
                    busy = state.respondingKey.isNotEmpty(),
                    onConfirm = { viewModel.respond(card, CallupResponse.CONFIRMED) },
                    onDecline = {
                        reason = card.myReason
                        declining = card
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        if (past.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SectionLabel("Anteriores y canceladas")
            Spacer(Modifier.height(10.dp))
            past.forEach { card ->
                CallupCardView(card, state.profile?.role == "PAD", state.confirmHours, now, false, true, {}, {})
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun CallupCardView(
    card: CallupCard,
    showPlayer: Boolean,
    confirmHours: Int,
    now: Long,
    saving: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    val match = card.match
    val cancelled = match.status == MatchStatus.CANCELLED
    val canRespond = match.canRespond(confirmHours, now)
    SportProCard(Modifier.alpha(if (cancelled || match.hasStarted(now)) 0.7f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (showPlayer) Text(card.player.name, color = TextoSecundario, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("vs ${match.rival}", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextoPrincipal)
                Text(match.teamName, color = TextoSecundario, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (cancelled) MatchStatusChip(match.status) else ResponseChip(card.callup.response)
        }
        match.startsAt?.let {
            Text("${it.toMatchDateText()} · ${it.toMatchTimeText()}", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(match.place, color = TextoSecundario, fontSize = 15.sp)

        when {
            cancelled -> InfoBox { Text("Se canceló este partido.", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            canRespond -> match.confirmDeadline(confirmHours)?.let {
                InfoBox { Text("Responde hasta el ${it.toDeadlineText()}.", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
            !match.hasStarted(now) -> Text(
                if (card.callup.response == CallupResponse.PENDING) "El plazo para responder terminó: quedó Sin confirmar."
                else "El plazo para responder terminó.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (card.callup.response == CallupResponse.DECLINED && card.myReason.isNotBlank()) {
            Text("Tu motivo (solo lo ve el DT): ${card.myReason}", color = TextoSecundario, fontSize = 14.sp)
        }
        if (canRespond) {
            // La respuesta actual se ve en el chip de arriba
            if (card.callup.response != CallupResponse.CONFIRMED) {
                PrimaryButton("Confirmo", onClick = onConfirm, enabled = !busy, loading = saving)
            }
            SecondaryButton(
                if (card.callup.response == CallupResponse.DECLINED) "Cambiar motivo de No puedo" else "No puedo",
                onClick = onDecline,
                enabled = !busy
            )
            if (card.callup.response != CallupResponse.PENDING) {
                Text("Puedes cambiar tu respuesta hasta que termine el plazo.", color = TextoSecundario, fontSize = 13.sp)
            }
        }
    }
}
