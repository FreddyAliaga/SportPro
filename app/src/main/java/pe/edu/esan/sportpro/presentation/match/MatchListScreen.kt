package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.Match
import pe.edu.esan.sportpro.data.model.MatchStatus
import pe.edu.esan.sportpro.data.model.toMatchDateText
import pe.edu.esan.sportpro.data.model.toMatchTimeText
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SelectablePill
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.rememberNow
import pe.edu.esan.sportpro.ui.theme.CoralSuave
import pe.edu.esan.sportpro.ui.theme.CoralTexto
import pe.edu.esan.sportpro.ui.theme.Fondo
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-006 · Partidos: el DT ve los de su equipo y crea nuevos; el ADM ve los de toda la academia (solo lectura)
@Composable
fun MatchListScreen(
    viewModel: MatchViewModel,
    onBack: () -> Unit,
    onNewMatch: () -> Unit,
    onOpenMatch: (Match) -> Unit,
    onOpenCatalog: () -> Unit       // DT: "Ver catálogo de eventos" (solo lectura, US-007)
) {
    val state by viewModel.uiState.collectAsState()
    val now by rememberNow()

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Partidos")
        Spacer(Modifier.height(8.dp))
        Subtitle(
            if (state.isAdmin) "Partidos de todos los equipos de tu academia. Los gestiona el DT de cada equipo."
            else "Crea el partido, elige a tus convocados y mira quién confirma."
        )
        Spacer(Modifier.height(16.dp))

        state.loadError?.let {
            ErrorBox(it)
            Spacer(Modifier.height(12.dp))
        }
        Messages(state.errorMessage, state.infoMessage)

        if (state.isCoach && state.profile?.teamId.isNullOrEmpty()) {
            InfoBox { Text(MatchViewModel.NO_TEAM_MESSAGE, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            return@SportProScreen
        }

        if (state.isCoach) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton("+ Nuevo partido", onNewMatch)
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Ver catálogo de eventos", onOpenCatalog)
        }

        // ADM: filtro por equipo
        if (state.isAdmin && state.filterTeams.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                SelectablePill("Todos", selected = state.teamFilter == null) { viewModel.setTeamFilter(null) }
                state.filterTeams.forEach { team ->
                    SelectablePill(team.name, selected = state.teamFilter == team.id) { viewModel.setTeamFilter(team.id) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (!state.matchesLoaded) {
            if (state.loadErrors["partidos"] == null) LoadingBox()
            return@SportProScreen
        }
        val upcoming = state.upcoming(now)
        val past = state.past(now)
        if (upcoming.isEmpty() && past.isEmpty()) {
            Subtitle(if (state.isCoach) "Aún no hay partidos. Crea el primero con “+ Nuevo partido”." else "Aún no hay partidos registrados.")
        }
        if (upcoming.isNotEmpty()) {
            SectionLabel("Próximos")
            Spacer(Modifier.height(10.dp))
            upcoming.forEach { match ->
                MatchCard(match, showTeam = state.isAdmin, onClick = { onOpenMatch(match) })
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        if (past.isNotEmpty()) {
            SectionLabel("Anteriores y cancelados")
            Spacer(Modifier.height(10.dp))
            past.forEach { match ->
                MatchCard(match, showTeam = state.isAdmin, onClick = { onOpenMatch(match) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

// Tarjeta de un partido: rival, fecha, hora, lugar y estado
@Composable
fun MatchCard(match: Match, showTeam: Boolean, onClick: () -> Unit) {
    val faded = match.status == MatchStatus.CANCELLED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (faded) 0.6f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(Tarjeta)
            .clickable(onClickLabel = "Ver partido", role = Role.Button, onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(SuperficieSuave),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.SportsSoccer, contentDescription = null, tint = Turquesa)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("vs ${match.rival}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextoPrincipal)
            if (showTeam) Text(match.teamName, color = TextoSecundario, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            match.startsAt?.let {
                Text("${it.toMatchDateText()} · ${it.toMatchTimeText()}", color = TextoSecundario, fontSize = 14.sp)
            }
            Text(match.place, color = TextoSecundario, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        MatchStatusChip(match.status)
    }
}

// Chip del estado del partido. Opción G: el coral se usa solo como acento (cancelado)
@Composable
fun MatchStatusChip(status: String) {
    val (background, text) = statusColors(status)
    StatusChip(MatchStatus.label(status), background, text)
}

private fun statusColors(status: String): Pair<Color, Color> = when (status) {
    MatchStatus.CALLED -> Turquesa to Tarjeta
    MatchStatus.LIVE -> CoralSuave to CoralTexto
    MatchStatus.CANCELLED -> CoralSuave to CoralTexto
    MatchStatus.FINISHED -> Fondo to TextoSecundario
    else -> SuperficieSuave to TurquesaOscuro
}
