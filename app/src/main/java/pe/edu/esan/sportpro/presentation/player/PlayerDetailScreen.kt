package pe.edu.esan.sportpro.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.DtInvitation
import pe.edu.esan.sportpro.data.model.InvitationState
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PlayerHistoryEntry
import pe.edu.esan.sportpro.data.model.PlayerHistoryType
import pe.edu.esan.sportpro.data.model.PlayerPrivate
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.toBirthText
import pe.edu.esan.sportpro.data.model.toLocalDateText
import pe.edu.esan.sportpro.presentation.components.ConfirmDialog
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.AvisoProteccionDatos
import pe.edu.esan.sportpro.presentation.components.EtiquetaProtegido
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.PlayerAvatar
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.TextLink
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

// US-003 · Perfil del jugador.
// - DT y ADM: editan los datos deportivos, invitan al apoderado o al jugador, trasladan, retiran y reactivan.
// - Apoderado y jugador mayor: ven el perfil y editan sus datos privados permitidos (onEditPrivate, US-003 Parte 5).
@Composable
fun PlayerDetailScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onEditSport: () -> Unit,
    onInvitationCreated: () -> Unit,
    onTransferred: () -> Unit,
    onEditPrivate: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsState()
    val player = state.player
    var showTransfer by remember { mutableStateOf(false) }
    var showRetire by remember { mutableStateOf(false) }
    var codeToRevoke by remember { mutableStateOf<String?>(null) }

    if (showTransfer && player != null) {
        TransferDialog(
            player = player,
            teams = state.transferTargets,
            onDismiss = { showTransfer = false },
            onPick = { team ->
                showTransfer = false
                viewModel.transfer(team, onTransferred)
            }
        )
    }
    if (showRetire && player != null) {
        ConfirmDialog(
            title = "¿Retirar a ${player.name}?",
            text = "Quedará Inactivo y no aparecerá entre los jugadores activos. No se borra: sus partidos, " +
                "entrenamientos e historial se conservan, y puedes reactivarlo cuando quieras. Sus códigos pendientes se anulan.",
            confirmText = "Retirar",
            onDismiss = { showRetire = false },
            onConfirm = {
                showRetire = false
                viewModel.setActive(false)
            }
        )
    }
    codeToRevoke?.let { code ->
        ConfirmDialog(
            title = "¿Anular el código $code?",
            text = "Ya no se podrá usar para registrarse. Puedes generar otro cuando quieras.",
            confirmText = "Anular",
            onDismiss = { codeToRevoke = null },
            onConfirm = {
                codeToRevoke = null
                viewModel.revokeInvitation(code)
            }
        )
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))

        if (player == null) {
            val loadError = state.loadError
            if (loadError != null) {
                ErrorBox(loadError)
            } else {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Turquesa)
                }
            }
            return@SportProScreen
        }

        val uid = state.profile?.uid.orEmpty()
        val isFamily = uid.isNotEmpty() && (uid in player.guardianIds || player.userId == uid)

        HeaderCard(player, state.photos[player.id].orEmpty(), state.teamName(player.teamId))
        state.loadError?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        Messages(state.errorMessage, state.infoMessage)

        if (state.canManage && state.turnedAdult && !player.hasAccount && player.active) {
            Spacer(Modifier.height(12.dp))
            InfoBox {
                Text(
                    "¡${player.name.split(" ").first()} ya cumplió 18! Invítalo como jugador: con su código asume su perfil " +
                        "y se retira el vínculo con sus apoderados.",
                    color = TextoPrincipal,
                    fontSize = 14.sp
                )
            }
        }

        // ---------- Datos deportivos ----------
        Spacer(Modifier.height(20.dp))
        SectionTitle("Datos deportivos", if (state.canManage) onEditSport else null)
        Spacer(Modifier.height(10.dp))
        SportProCard {
            DataRow("Categoría", player.category)
            DataRow("Posición principal", player.positionMain)
            DataRow("Posición secundaria", player.positionSecondary.ifEmpty { "Ninguna" })
            DataRow("Dorsal", player.dorsal?.toString() ?: "Sin dorsal")
            DataRow("Pie dominante", player.foot)
            // El apoderado también ve la autorización (DT y ADM la ven en "Cuenta y apoderados")
            if (!state.canManage && player.isMinor) GuardianAuthorizationLine(player)
        }

        // ---------- Datos privados ----------
        Spacer(Modifier.height(20.dp))
        SectionTitle("Datos privados", if (isFamily) onEditPrivate else null, isProtected = true)
        Spacer(Modifier.height(10.dp))
        PrivateCard(state.playerPrivate, state.playerAge)
        Spacer(Modifier.height(12.dp))
        AvisoProteccionDatos(PrivacyTexts.TITLE_PRIVATE_DATA, PrivacyTexts.PLAYER_PRIVATE_DATA, compact = true)

        if (state.canManage) {
            ManagerSection(
                state = state,
                player = player,
                onInvite = { viewModel.createInvitation(onInvitationCreated) },
                onRevoke = { codeToRevoke = it },
                onTransfer = { showTransfer = true },
                onRetire = { showRetire = true },
                onReactivate = { viewModel.setActive(true) }
            )
        }
    }
}

@Composable
private fun HeaderCard(player: Player, photo: String, teamName: String) {
    SportProCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(photo, player.initials, size = 72.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(player.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextoPrincipal)
                Text(
                    listOfNotNull(player.dorsal?.let { "#$it" }, teamName.ifBlank { null }).joinToString(" · "),
                    color = TextoSecundario,
                    fontSize = 14.sp
                )
                PlayerStatusChip(player.status)
            }
        }
    }
}

// Título de sección con la etiqueta "Protegido" y un enlace "Editar" opcionales
@Composable
private fun SectionTitle(text: String, onEdit: (() -> Unit)?, isProtected: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { SectionLabel(text) }
        if (isProtected) EtiquetaProtegido(Modifier.padding(start = 8.dp, end = if (onEdit != null) 4.dp else 0.dp))
        if (onEdit != null) TextLink("Editar", onClick = onEdit)
    }
}

@Composable
private fun PrivateCard(data: PlayerPrivate?, age: Int?) {
    SportProCard {
        if (data == null) {
            Text("Cargando…", color = TextoSecundario)
            return@SportProCard
        }
        DataRow(
            "Nacimiento",
            data.birthDate?.let { date -> "${date.toBirthText()}${age?.let { " ($it años)" } ?: ""}" } ?: "Sin registrar"
        )
        DataRow("Celular de contacto", data.contactPhone.ifEmpty { "Sin registrar" })
        DataRow(
            "Contacto de emergencia",
            if (data.hasEmergencyContact) "${data.emergencyName} (${data.emergencyRelation}) · ${data.emergencyPhone}"
            else "Sin registrar"
        )
        DataRow("Estatura", data.heightCm?.let { "$it cm" } ?: "Sin registrar")
        DataRow("Peso", data.weightKg?.let { "$it kg" } ?: "Sin registrar")
    }
}

// DT y ADM: cuenta y apoderados, códigos, traslado, retiro e historial
@Composable
private fun ManagerSection(
    state: PlayerUiState,
    player: Player,
    onInvite: () -> Unit,
    onRevoke: (String) -> Unit,
    onTransfer: () -> Unit,
    onRetire: () -> Unit,
    onReactivate: () -> Unit
) {
    Spacer(Modifier.height(20.dp))
    SectionLabel("Cuenta y apoderados")
    Spacer(Modifier.height(10.dp))
    SportProCard {
        val guardians = player.guardianIds.size
        Text(
            when {
                player.hasAccount -> "El jugador gestiona su propia cuenta."
                player.isMinor && guardians == 0 -> "Aún no tiene apoderado vinculado. Invítalo con un código."
                player.isMinor -> "$guardians ${if (guardians == 1) "apoderado vinculado" else "apoderados vinculados"}."
                else -> "Es mayor de edad y aún no tiene cuenta. Invítalo con un código de jugador."
            },
            color = TextoPrincipal,
            style = MaterialTheme.typography.bodyLarge
        )
        // Autorización del apoderado: todo menor la tiene (si faltara, se avisa en color de error)
        if (player.isMinor) GuardianAuthorizationLine(player)
        val teamActive = state.teams.firstOrNull { it.id == player.teamId }?.active == true
        if (!teamActive && player.active && !player.hasAccount) {
            Text(
                "Su equipo está desactivado: trasládalo a un equipo activo para poder invitarlo.",
                color = ErrorTexto,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        when (state.invitationRole) {
            "PAD" -> SecondaryButton("Invitar apoderado", onInvite, enabled = !state.isLoading)
            "JUG" -> SecondaryButton("Invitar jugador", onInvite, enabled = !state.isLoading)
        }
        state.sortedInvitations.forEach { invitation ->
            InvitationRow(invitation, onRevoke = { onRevoke(invitation.code) })
        }
    }

    Spacer(Modifier.height(20.dp))
    if (player.active) {
        if (state.transferTargets.isNotEmpty()) {
            SecondaryButton("Trasladar a otro equipo", onTransfer, enabled = !state.isLoading)
            Spacer(Modifier.height(12.dp))
        }
        SecondaryButton("Retirar jugador", onRetire, enabled = !state.isLoading)
    } else if (state.teams.firstOrNull { it.id == player.teamId }?.active == true) {
        SecondaryButton("Reactivar jugador", onReactivate, enabled = !state.isLoading)
    } else {
        // La regla tampoco deja reactivarlo en un equipo desactivado
        Text(
            "Su equipo está desactivado: trasládalo a un equipo activo para reactivarlo.",
            color = ErrorTexto,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        if (state.transferTargets.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Trasladar a otro equipo", onTransfer, enabled = !state.isLoading)
        }
    }

    if (state.history.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        SectionLabel("Historial")
        Spacer(Modifier.height(10.dp))
        SportProCard {
            state.sortedHistory.forEach { HistoryRow(it) }
        }
    }
}

@Composable
private fun InvitationRow(invitation: DtInvitation, onRevoke: () -> Unit) {
    val invitationState = invitation.state()
    val (chipBackground, chipText) = when (invitationState) {
        InvitationState.PENDING -> SuperficieSuave to TurquesaOscuro
        InvitationState.USED -> Turquesa to Tarjeta
        InvitationState.EXPIRED -> BordeSuave to TextoSecundario
        InvitationState.REVOKED -> CoralSuave to ErrorTexto
    }
    val detail = when (invitationState) {
        InvitationState.PENDING -> invitation.expiresAt?.let { "vence el ${it.toLocalDateText()}" }
        InvitationState.EXPIRED -> invitation.expiresAt?.let { "venció el ${it.toLocalDateText()}" }
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(invitation.code, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
                Text(
                    listOfNotNull(if (invitation.role == "JUG") "Jugador" else "Apoderado", detail).joinToString(" · "),
                    color = TextoSecundario,
                    fontSize = 13.sp
                )
            }
            StatusChip(invitationState.label, chipBackground, chipText)
        }
        if (invitationState == InvitationState.PENDING) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextLink("Anular", color = ErrorTexto, onClick = onRevoke)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: PlayerHistoryEntry) {
    val text = when (entry.type) {
        PlayerHistoryType.CREATED -> "Alta en ${entry.toTeamName}"
        PlayerHistoryType.TRANSFER -> "Traslado de ${entry.fromTeamName} a ${entry.toTeamName}"
        PlayerHistoryType.RETIRE -> "Retirado (Inactivo) en ${entry.fromTeamName}"
        PlayerHistoryType.REACTIVATE -> "Reactivado en ${entry.toTeamName}"
        else -> entry.type
    }
    Column {
        Text(text, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            listOfNotNull(entry.at?.toLocalDateText(), entry.byName.ifBlank { null }?.let { "por $it" })
                .joinToString(" · "),
            color = TextoSecundario,
            fontSize = 13.sp
        )
    }
}

// Elegir el equipo ACTIVO de la academia al que se traslada al jugador
@Composable
private fun TransferDialog(player: Player, teams: List<Team>, onDismiss: () -> Unit, onPick: (Team) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = { Text("¿A qué equipo pasa ${player.name}?", color = TextoPrincipal, fontWeight = FontWeight.Black, fontSize = 20.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                teams.forEach { team ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onPick(team) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text("${team.name} · ${team.category}", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Text(
                    "Su historial se conserva y sus códigos pendientes se anulan. Su categoría pasa a ser la del equipo nuevo.",
                    color = TextoSecundario,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextLink("Cancelar", color = TextoSecundario, onClick = onDismiss) }
    )
}
