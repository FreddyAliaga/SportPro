package pe.edu.esan.sportpro.presentation.academy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.TEAM_CATEGORIES
import pe.edu.esan.sportpro.presentation.components.ConfirmDialog
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SelectablePill
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario

// US-002 · Nuevo equipo / Editar equipo (ADM): nombre, categoría, máximos, DT; desactivar o reactivar
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeamFormScreen(
    viewModel: AcademyViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onInvitationCreated: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.teamForm
    val errors = state.teamErrors
    val isNew = form.id.isEmpty()
    val team = state.teams.firstOrNull { it.id == form.id }
    var showCoachPicker by remember { mutableStateOf(false) }
    var showDeactivate by remember { mutableStateOf(false) }

    if (showCoachPicker) {
        CoachPickerDialog(
            team = team,
            selectedUid = state.teamFormCoachUid,
            coaches = state.activeCoaches,
            teamNameOf = state::teamName,
            onDismiss = { showCoachPicker = false },
            onConfirm = { uid ->
                viewModel.onTeamFormChange(form.copy(coachUid = uid, coachChanged = true))
                showCoachPicker = false
            }
        )
    }
    if (showDeactivate && team != null) {
        ConfirmDialog(
            title = "¿Desactivar ${team.name}?",
            text = "El equipo deja de aparecer entre los activos. Sus códigos pendientes (de DT, jugadores y apoderados) se anulan " +
                "y su DT queda sin equipo. Sus jugadores siguen en el equipo: puedes trasladarlos desde Ver plantel. " +
                "No se borra: sus partidos, entrenamientos y registros se conservan.",
            confirmText = "Desactivar",
            onDismiss = { showDeactivate = false },
            onConfirm = {
                showDeactivate = false
                viewModel.setTeamActive(team, active = false, onDone = onSaved)
            }
        )
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title(if (isNew) "Nuevo equipo" else "Editar equipo")
        Spacer(Modifier.height(8.dp))
        Subtitle("Cada equipo pertenece a una categoría y tiene un DT a cargo.")
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = form.name,
                onValueChange = { viewModel.onTeamFormChange(form.copy(name = it.take(40))) },   // las reglas aceptan hasta 40
                label = "Nombre del equipo",
                placeholder = "Sub-12 A",
                error = errors.name
            )
            Column {
                Text("Categoría", style = MaterialTheme.typography.labelMedium, color = TextoPrincipal)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TEAM_CATEGORIES.forEach { category ->
                        SelectablePill(category, selected = form.category == category) {
                            viewModel.onTeamFormChange(form.copy(category = category))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportProTextField(
                    value = form.maxStarters,
                    onValueChange = { viewModel.onTeamFormChange(form.copy(maxStarters = it.filter(Char::isDigit).take(2))) },
                    label = "Máx. titulares",
                    error = errors.maxStarters,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                SportProTextField(
                    value = form.maxSubstitutes,
                    onValueChange = { viewModel.onTeamFormChange(form.copy(maxSubstitutes = it.filter(Char::isDigit).take(2))) },
                    label = "Máx. suplentes",
                    error = errors.maxSubstitutes,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("DT asignado")
        Spacer(Modifier.height(10.dp))
        SportProCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !isNew && !state.membersLoaded -> "Cargando…"
                        else -> state.memberName(state.teamFormCoachUid).ifEmpty { "Sin DT" }
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = TextoPrincipal,
                    modifier = Modifier.weight(1f)
                )
                // A un equipo desactivado no se le asigna DT
                if (team?.active != false && (isNew || state.membersLoaded)) TextLink("Cambiar") { showCoachPicker = true }
            }
            val pendingCode = team?.let { state.pendingDtCodeOf(it.id) }
            Text(
                when {
                    team?.active == false -> "Reactiva el equipo para asignarle un DT."
                    pendingCode != null -> "Ya hay un código de DT pendiente (${pendingCode.code}). Si asignas un DT aquí, ese código se anula."
                    else -> "¿Tu DT aún no está en SportPro? Guarda el equipo como Sin DT e invítalo con un código."
                },
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Messages(state.errorMessage, state.infoMessage)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Guardar equipo", onClick = { viewModel.saveTeam(onSaved) }, loading = state.isLoading)

        // Acciones de un equipo ya guardado
        if (team != null) {
            Spacer(Modifier.height(12.dp))
            if (team.active) {
                // Un solo DT por equipo: sin DT y sin otro código de DT pendiente
                // (mientras cargan los usuarios o los códigos aún no se sabe: no se muestra)
                if (state.membersLoaded && state.invitationsLoaded && state.canInviteCoach(team.id)) {
                    SecondaryButton("Invitar DT con un código", { viewModel.createInvitation(team, onInvitationCreated) }, enabled = !state.invitationLoading)
                    Spacer(Modifier.height(12.dp))
                }
                SecondaryButton("Desactivar equipo", { showDeactivate = true }, enabled = !state.isLoading)
            } else {
                SecondaryButton("Reactivar equipo", { viewModel.setTeamActive(team, active = true, onDone = onSaved) }, enabled = !state.isLoading)
            }
        }
    }
}
