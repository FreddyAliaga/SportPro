package pe.edu.esan.sportpro.presentation.academy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.DtInvitation
import pe.edu.esan.sportpro.data.model.InvitationState
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.toLocalDateText
import pe.edu.esan.sportpro.presentation.components.InitialsBadge
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SelectablePill
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.CoralSuave
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-002 · Usuarios e invitaciones (ADM): códigos de DT con su estado (RN-002) y usuarios por rol
@Composable
fun UsersInvitationsScreen(
    viewModel: AcademyViewModel,
    onBack: () -> Unit,
    onInvitationCreated: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showTeamPicker by remember { mutableStateOf(false) }
    var codeToRevoke by remember { mutableStateOf<String?>(null) }
    var userToToggle by remember { mutableStateOf<UserProfile?>(null) }

    if (showTeamPicker) {
        TeamPickerDialog(
            teams = state.activeTeamsByCategory.flatMap { it.second },
            coachesOf = state::coachesOf,
            onDismiss = { showTeamPicker = false },
            onPick = { team ->
                showTeamPicker = false
                viewModel.createInvitation(team, onInvitationCreated)
            }
        )
    }
    codeToRevoke?.let { code ->
        ConfirmDialog(
            title = "¿Anular el código $code?",
            text = "Nadie podrá registrarse con él. El código no se borra: queda en la lista como Anulado.",
            confirmText = "Anular",
            onDismiss = { codeToRevoke = null },
            onConfirm = {
                codeToRevoke = null
                viewModel.revokeInvitation(code)
            }
        )
    }
    userToToggle?.let { user ->
        ToggleUserDialog(
            user = user,
            onDismiss = { userToToggle = null },
            onConfirm = {
                userToToggle = null
                viewModel.setUserActive(user, active = user.status != AccountStatus.ACTIVE)
            }
        )
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Usuarios e invitaciones")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectablePill("Invitaciones", selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            SelectablePill("Usuarios", selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
        }
        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            pe.edu.esan.sportpro.presentation.components.ErrorBox(it)
        }
        Messages(state)
        Spacer(Modifier.height(16.dp))

        if (tab == 0) {
            Subtitle("Genera un código para cada DT. Revisa su estado y anula los que ya no necesites.")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("+ Nueva invitación de DT", onClick = { showTeamPicker = true }, loading = state.isLoading)
            Spacer(Modifier.height(16.dp))
            if (state.sortedInvitations.isEmpty()) Subtitle("Aún no has generado códigos.")
            state.sortedInvitations.forEach { invitation ->
                InvitationCard(
                    invitation = invitation,
                    usedByName = state.memberName(invitation.usedBy),
                    onRevoke = { codeToRevoke = invitation.code },
                    onRegenerate = { viewModel.regenerateInvitation(invitation, onInvitationCreated) }
                )
                Spacer(Modifier.height(12.dp))
            }
        } else {
            // Filtro por rol (null = Todos)
            val filters = listOf(null to "Todos", "DT" to "Entrenadores", "JUG" to "Jugadores", "PAD" to "Apoderados", "ADM" to "Administración")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { (role, label) ->
                    val count = if (role == null) state.members.size else state.members.count { it.role == role }
                    SelectablePill("$label ($count)", selected = state.roleFilter == role) { viewModel.setRoleFilter(role) }
                }
            }
            Spacer(Modifier.height(16.dp))
            state.filteredMembers.forEach { member ->
                // Solo se puede desactivar/reactivar una cuenta activa o desactivada, y nunca la propia
                val canToggle = member.uid != state.admin?.uid &&
                    (member.status == AccountStatus.ACTIVE || member.status == AccountStatus.INACTIVE)
                MemberCard(
                    member = member,
                    teamName = state.teamName(member.teamId),
                    onClick = if (canToggle) ({ userToToggle = member }) else null
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "Toca un usuario para desactivar o reactivar su cuenta. Su historial se conserva.",
                color = TextoSecundario,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: DtInvitation,
    usedByName: String,
    onRevoke: () -> Unit,
    onRegenerate: () -> Unit
) {
    val state = invitation.state()
    val (chipBackground, chipText) = when (state) {
        InvitationState.PENDING -> SuperficieSuave to TurquesaOscuro
        InvitationState.USED -> Turquesa to Tarjeta
        InvitationState.EXPIRED -> BordeSuave to TextoSecundario
        InvitationState.REVOKED -> CoralSuave to ErrorTexto
    }
    val detail = when (state) {
        InvitationState.USED -> if (usedByName.isNotEmpty()) "usado por $usedByName" else "usado"
        InvitationState.PENDING -> invitation.expiresAt?.let { "vence el ${it.toLocalDateText()}" } ?: ""
        InvitationState.EXPIRED -> invitation.expiresAt?.let { "venció el ${it.toLocalDateText()}" } ?: ""
        InvitationState.REVOKED -> "anulado"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Tarjeta)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(invitation.code, fontWeight = FontWeight.Black, fontSize = 19.sp, color = Turquesa, letterSpacing = 1.sp)
                Text(
                    listOf("DT · ${invitation.teamName}", detail).filter { it.isNotBlank() }.joinToString(" · "),
                    color = TextoSecundario,
                    fontSize = 14.sp
                )
            }
            StatusChip(state.label, chipBackground, chipText)
        }
        when (state) {
            InvitationState.PENDING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextLink("Anular", color = ErrorTexto, onClick = onRevoke)
            }
            InvitationState.EXPIRED, InvitationState.REVOKED -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextLink("Generar otro", onClick = onRegenerate)
            }
            InvitationState.USED -> Unit
        }
    }
}

@Composable
private fun MemberCard(member: UserProfile, teamName: String, onClick: (() -> Unit)?) {
    val (statusText, chipBackground, chipText) = when (member.status) {
        AccountStatus.ACTIVE -> Triple("Activa", SuperficieSuave, TurquesaOscuro)
        AccountStatus.INACTIVE -> Triple("Desactivada", BordeSuave, TextoSecundario)
        AccountStatus.BLOCKED -> Triple("Bloqueada", CoralSuave, ErrorTexto)
        else -> Triple("Sin verificar", BordeSuave, TextoSecundario)   // aún no confirma su correo
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Tarjeta)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp)
    ) {
        InitialsBadge(member.initials, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            Text(
                when {
                    member.role == "DT" && teamName.isEmpty() -> "Entrenador · Sin equipo"
                    teamName.isNotEmpty() -> "${member.roleName} · $teamName"
                    else -> member.roleName
                },
                color = TextoSecundario,
                fontSize = 14.sp
            )
        }
        StatusChip(statusText, chipBackground, chipText)
    }
}
