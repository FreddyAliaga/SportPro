package pe.edu.esan.sportpro.presentation.academy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.Academy
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.shortCategory
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.AcademyLogo
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-002 · Academia y equipos (ADM): datos de la academia y equipos agrupados por categoría (RN-004)
@Composable
fun AcademyTeamsScreen(
    viewModel: AcademyViewModel,
    onBack: () -> Unit,
    onEditAcademy: () -> Unit,
    onNewTeam: () -> Unit,
    onEditTeam: (Team) -> Unit,
    onOpenRoster: (Team) -> Unit,      // US-003: plantel del equipo
    onUsersInvitations: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var teamForCoach by remember { mutableStateOf<Team?>(null) }

    teamForCoach?.let { team ->
        CoachPickerDialog(
            team = team,
            selectedUid = state.coachesOf(team.id).firstOrNull()?.uid.orEmpty(),
            coaches = state.activeCoaches,
            teamNameOf = state::teamName,
            onDismiss = { teamForCoach = null },
            onConfirm = { uid ->
                viewModel.changeCoach(team, uid)
                teamForCoach = null
            }
        )
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Academia y equipos")
        Spacer(Modifier.height(16.dp))

        val academy = state.academy
        if (academy == null) {
            // Aún no llegan los datos (o hubo un error al leerlos)
            val loadError = state.loadError
            if (loadError != null) ErrorBox(loadError) else LoadingBox()
            return@SportProScreen
        }

        AcademyCard(academy, onEditAcademy)
        // Errores al leer equipos, usuarios o códigos (la academia sí cargó)
        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        Messages(state.errorMessage, state.infoMessage)

        Spacer(Modifier.height(20.dp))
        PrimaryButton("+ Nuevo equipo", onNewTeam)
        Spacer(Modifier.height(20.dp))

        if (!state.teamsLoaded) {
            if (state.loadErrors["equipos"] == null) LoadingBox()
        } else if (state.activeTeamsByCategory.isEmpty()) {
            Subtitle("Aún no tienes equipos. Crea el primero y luego invita a su DT con un código.")
        }
        state.activeTeamsByCategory.forEach { (category, teams) ->
            SectionLabel(category)
            Spacer(Modifier.height(10.dp))
            teams.forEach { team ->
                TeamCard(
                    team = team,
                    coaches = state.coachesOf(team.id),
                    coachesLoading = !state.membersLoaded,
                    onClick = { onEditTeam(team) },
                    onChangeCoach = { teamForCoach = team },
                    onOpenRoster = { onOpenRoster(team) }
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.inactiveTeams.isNotEmpty()) {
            SectionLabel("Desactivados")
            Spacer(Modifier.height(10.dp))
            state.inactiveTeams.forEach { team ->
                TeamCard(
                    team = team,
                    coaches = emptyList(),
                    coachesLoading = false,
                    onClick = { onEditTeam(team) },
                    onChangeCoach = null,
                    onOpenRoster = { onOpenRoster(team) }   // para trasladar a sus jugadores
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        SecondaryButton("Usuarios e invitaciones", onUsersInvitations)
    }
}

@Composable
private fun AcademyCard(academy: Academy, onEdit: () -> Unit) {
    SportProCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AcademyLogo(academy.logoBase64, academy.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(academy.name, fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextoPrincipal)
                Text("${academy.city} · ${academy.institutionalEmail}", color = TextoSecundario, fontSize = 13.sp)
                Text(
                    "Convocatoria: ${academy.callupNoticeHours} h antes · confirmar hasta ${academy.callupConfirmHours} h antes",
                    color = TextoSecundario,
                    fontSize = 13.sp
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(SuperficieSuave)
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = "Editar academia", tint = Turquesa)
            }
        }
        VerificationNotice(academy.verified)
    }
}

@Composable
private fun TeamCard(
    team: Team,
    coaches: List<UserProfile>,
    coachesLoading: Boolean,         // aún no llega la lista de usuarios: no se sabe si tiene DT
    onClick: () -> Unit,
    onChangeCoach: (() -> Unit)?,    // null en equipos desactivados
    onOpenRoster: () -> Unit         // US-003: "Ver plantel"
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (team.active) 1f else 0.6f)
            .clip(RoundedCornerShape(22.dp))
            .background(Tarjeta)
            .clickable(onClickLabel = "Editar equipo", role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(SuperficieSuave),
                contentAlignment = Alignment.Center
            ) {
                Text(shortCategory(team.category), fontWeight = FontWeight.Black, fontSize = 13.sp, color = Turquesa)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextoPrincipal)
                Text(
                    when {
                        !team.active -> "Equipo desactivado · su historial se conserva"
                        coachesLoading -> "Cargando DT…"
                        coaches.isEmpty() -> "Titulares ${team.maxStarters} · suplentes ${team.maxSubstitutes}"
                        else -> "DT ${coaches.joinToString(" y ") { it.name }}"
                    },
                    color = TextoSecundario,
                    fontSize = 14.sp
                )
            }
            // Opción G: el coral es solo decorativo; "Sin DT" va en turquesa suave
            if (team.active && !coachesLoading && coaches.isEmpty()) StatusChip("Sin DT", SuperficieSuave, TurquesaOscuro)
        }
        // Dos DT con el mismo equipo (por ejemplo, se invitó a otro DT): hay que elegir uno
        if (coaches.size > 1) {
            Text(
                "Hay ${coaches.size} DT en este equipo. Elige quién queda con Cambiar DT.",
                color = ErrorTexto,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextLink("Ver plantel", onClick = onOpenRoster)
            if (onChangeCoach != null && !coachesLoading) TextLink("Cambiar DT", onClick = onChangeCoach)
        }
    }
}
