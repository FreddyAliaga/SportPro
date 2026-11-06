package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchEvent
import pe.edu.esan.sportpro.data.model.Score
import pe.edu.esan.sportpro.presentation.components.EventTypeBadge
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.eventIconLabel
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.CoralSuave
import pe.edu.esan.sportpro.ui.theme.CoralTexto
import pe.edu.esan.sportpro.ui.theme.Fondo
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-008 / US-010 · Piezas comunes del partido en vivo: marcador, cronómetro, avisos y cronología.
// Las usan "Registrar partido" (DT y operadores) y la vista "En vivo" (todos los miembros).

// Marcador grande: Nosotros x – y Rival, con el minuto (o el tiempo del cronómetro) y el estado
@Composable
fun ScoreBoard(match: Match, score: Score, now: Long, showClock: Boolean) {
    SportProCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TeamScore(match.teamName.ifEmpty { "Nosotros" }, score.local, Modifier.weight(1f))
            Text("–", fontWeight = FontWeight.Black, fontSize = 32.sp, color = TextoSecundario)
            TeamScore(match.rival, score.rival, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            MatchStatusChip(match.status)
            if (match.hasLiveData) {
                Spacer(Modifier.width(10.dp))
                val minute = match.currentMinute(now)
                Text(
                    if (showClock) "${match.clockText(now)} · min $minute'" else "Minuto $minute'",
                    fontWeight = FontWeight.Black,
                    fontSize = if (showClock) 24.sp else 20.sp,
                    color = TextoPrincipal,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Minuto $minute" }
                )
                if (match.isLive && !match.clockRunning) {
                    Spacer(Modifier.width(8.dp))
                    StatusChip("En pausa", SuperficieSuave, TurquesaOscuro)
                }
            }
        }
    }
}

@Composable
private fun TeamScore(name: String, goals: Int, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clearAndSetSemantics { contentDescription = "$name: $goals" }
    ) {
        Text(goals.toString(), fontWeight = FontWeight.Black, fontSize = 48.sp, color = TextoPrincipal)
        Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextoSecundario, textAlign = TextAlign.Center, maxLines = 2)
    }
}

// "Sin conexión": se muestra el último estado conocido (nada se bloquea)
@Composable
fun OfflineBanner() {
    InfoBox {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = TurquesaOscuro)
        Text(
            "Sin conexión. Te mostramos lo último que conocemos; se actualizará solo al volver la señal.",
            color = TextoPrincipal,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

// "X eventos pendientes de sincronizar"
@Composable
fun PendingBanner(pending: Int) {
    InfoBox {
        Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = TurquesaOscuro)
        Text(
            if (pending == 1) "1 evento pendiente de sincronizar" else "$pending eventos pendientes de sincronizar",
            color = TextoPrincipal,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

// Cronología del partido (los más nuevos arriba). notes = observaciones (vacío si no puede verlas).
@Composable
fun EventTimeline(events: List<MatchEvent>, notes: Map<String, String>, showAuthor: Boolean) {
    if (events.isEmpty()) {
        Text("Aún no hay eventos registrados.", color = TextoSecundario, fontSize = 15.sp)
        return
    }
    SportProCard {
        events.forEachIndexed { index, event ->
            if (index > 0) HorizontalDivider(color = BordeSuave)
            EventRow(event, notes[event.id], showAuthor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventRow(event: MatchEvent, note: String?, showAuthor: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "${event.minute}'",
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            color = TextoPrincipal,
            modifier = Modifier.width(40.dp).padding(top = 10.dp)
        )
        EventTypeBadge(event.typeIcon, event.typeColor, size = 40.dp, contentDescription = eventIconLabel(event.typeIcon))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(event.typeName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            val who = event.whoText()
            val sideText = when (event.side) {
                "LOCAL" -> "Nosotros"
                "RIVAL" -> if (who.startsWith("Rival")) "" else "Rival"
                else -> ""
            }
            listOf(sideText, who).filter { it.isNotEmpty() }.joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, color = TextoSecundario, fontSize = 14.sp)
            }
            // PRIVACIDAD: solo llega la observación a quien puede verla (DT, operadores y ADM)
            if (!note.isNullOrBlank()) Text("“$note”", color = TextoPrincipal, fontSize = 14.sp)
            if (showAuthor && event.createdByName.isNotBlank()) {
                Text("Registró ${event.createdByName}", color = TextoSecundario, fontSize = 12.sp)
            }
            val tags = listOfNotNull(
                if (event.pendingIdentification) "Pendiente de identificar" else null,
                if (event.late) "Registrado tarde" else null,
                if (event.postInsert) "Inserción posterior" else null
            )
            if (tags.isNotEmpty() || event.hasPendingWrites) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { StatusChip(it, SuperficieSuave, TurquesaOscuro) }
                    if (event.hasPendingWrites) StatusChip("Sin sincronizar", CoralSuave, CoralTexto)
                }
            }
        }
    }
}

// Botón grande de un tipo del catálogo (mínimo 56 dp de alto): ícono y color del tipo
@Composable
fun EventTypeButton(name: String, iconKey: String, colorHex: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (enabled) Tarjeta else Fondo)
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClickLabel = "Registrar $name", role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        EventTypeBadge(iconKey, colorHex, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = if (enabled) TextoPrincipal else TextoSecundario,
            maxLines = 2
        )
    }
}
