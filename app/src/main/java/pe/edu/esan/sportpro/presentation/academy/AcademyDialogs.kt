package pe.edu.esan.sportpro.presentation.academy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.presentation.components.SportProDialog
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.ui.theme.Borde
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Elegir el DT de un equipo ("" = Sin DT). Un DT dirige un solo equipo.
@Composable
fun CoachPickerDialog(
    team: Team?,                         // null si el equipo aún no se guarda (nuevo equipo)
    selectedUid: String,
    coaches: List<UserProfile>,
    teamNameOf: (String) -> String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selected by remember { mutableStateOf(selectedUid) }

    SportProDialog(
        title = if (team != null) "DT de ${team.name}" else "DT del equipo",
        confirmText = "Guardar cambio",
        onConfirm = { onConfirm(selected) },
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CoachOption("Sin DT", null, selected == "") { selected = "" }
            coaches.forEach { coach ->
                // Si ya dirige otro equipo se avisa: ese equipo quedará Sin DT
                val otherTeam = if (coach.teamId.isNotEmpty() && coach.teamId != team?.id) teamNameOf(coach.teamId) else ""
                CoachOption(
                    coach.name,
                    if (otherTeam.isNotEmpty()) "Hoy dirige $otherTeam" else null,
                    selected == coach.uid
                ) { selected = coach.uid }
            }
            if (coaches.isEmpty()) {
                Text(
                    "Aún no hay DT activos en tu academia. Invítalos con un código.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Si eliges un DT que ya dirige otro equipo, ese equipo quedará Sin DT. Los partidos y entrenamientos de cada equipo se conservan.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CoachOption(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Turquesa, unselectedColor = Borde)
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            detail?.let { Text(it, color = TextoSecundario, fontSize = 13.sp) }
        }
    }
}

// Desactivar o reactivar una cuenta (nunca se borra)
@Composable
fun ToggleUserDialog(user: UserProfile, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val isActive = user.status == AccountStatus.ACTIVE
    val body = if (isActive) {
        "Ya no podrá iniciar sesión. Sus partidos, entrenamientos y registros anteriores se conservan." +
            if (user.role == "DT" && user.teamId.isNotEmpty()) {
                " Su equipo quedará Sin DT; si lo reactivas, asígnale un equipo con Cambiar DT."
            } else ""
    } else {
        "Podrá volver a iniciar sesión con sus mismos permisos."
    }
    SportProDialog(
        title = if (isActive) "¿Desactivar la cuenta de ${user.name}?" else "¿Reactivar la cuenta de ${user.name}?",
        confirmText = if (isActive) "Desactivar" else "Reactivar",
        confirmColor = if (isActive) ErrorTexto else Turquesa,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(body, color = TextoPrincipal, style = MaterialTheme.typography.bodyLarge)
    }
}

// Elegir el equipo para una nueva invitación de DT.
// Un solo DT por equipo: un equipo que ya tiene DT o un código de DT pendiente no se puede elegir.
@Composable
fun TeamPickerDialog(
    teams: List<Team>,
    blockedReason: (Team) -> String?,    // null = se puede invitar
    onDismiss: () -> Unit,
    onPick: (Team) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = { Text("¿Para qué equipo es el DT?", color = TextoPrincipal, fontWeight = FontWeight.Black, fontSize = 20.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (teams.isEmpty()) {
                    Text("Primero crea un equipo en Academia y equipos.", color = TextoSecundario)
                }
                teams.forEach { team ->
                    val reason = blockedReason(team)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = reason == null, role = Role.Button) { onPick(team) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            "${team.name} · ${team.category}",
                            color = if (reason == null) TextoPrincipal else TextoSecundario,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        reason?.let { Text(it, color = TextoSecundario, fontSize = 13.sp) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextLink("Cancelar", color = TextoSecundario, onClick = onDismiss) }
    )
}
