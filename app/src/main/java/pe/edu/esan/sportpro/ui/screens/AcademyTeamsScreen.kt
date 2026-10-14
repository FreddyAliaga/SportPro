package pe.edu.esan.sportpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.domain.model.Team
import pe.edu.esan.sportpro.ui.components.ReassignCoachDialog
import pe.edu.esan.sportpro.ui.components.SwitchAcademyDialog
import pe.edu.esan.sportpro.ui.viewmodel.AcademyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademyTeamsScreen(
    viewModel: AcademyViewModel,
    onNavigateToEditAcademy: () -> Unit,
    onNavigateToCreateNewAcademy: () -> Unit,
    onNavigateToCreateTeam: () -> Unit,
    onNavigateToEditTeam: (Team) -> Unit,
    onNavigateToInvitations: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToTrainingSessions: () -> Unit,
    onNavigateToMatchesList: () -> Unit,
    onNavigateToCommunity: () -> Unit
) {
    val academy by viewModel.academy.collectAsState()
    val academies by viewModel.academies.collectAsState()
    val teamsByCategory by viewModel.teamsByCategory.collectAsState()
    val coaches by viewModel.users.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var teamToReassign by remember { mutableStateOf<Team?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showSwitchAcademyDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    if (showSwitchAcademyDialog) {
        SwitchAcademyDialog(
            academies = academies,
            currentAcademyId = academy.id,
            onDismiss = { showSwitchAcademyDialog = false },
            onSelectAcademy = { academyId ->
                viewModel.switchAcademy(academyId)
            },
            onCreateNewAcademy = onNavigateToCreateNewAcademy
        )
    }

    if (teamToReassign != null) {
        ReassignCoachDialog(
            team = teamToReassign!!,
            coaches = coaches.filter { it.role.code == "DT" && it.active },
            onDismiss = { teamToReassign = null },
            onConfirm = { coachId, coachName ->
                viewModel.reassignCoach(teamToReassign!!.id, coachId, coachName)
                teamToReassign = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academia y equipos", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCommunity) {
                        Icon(Icons.Default.Groups, contentDescription = "Comunidad")
                    }
                    IconButton(onClick = onNavigateToTrainingSessions) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = "Entrenamientos")
                    }
                    IconButton(onClick = onNavigateToUsers) {
                        Icon(Icons.Default.Group, contentDescription = "Usuarios")
                    }
                    IconButton(onClick = onNavigateToInvitations) {
                        Icon(Icons.Default.Mail, contentDescription = "Invitaciones DT")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Comunidad y Publicaciones") },
                            leadingIcon = { Icon(Icons.Default.Groups, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToCommunity()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Partidos y Marcador en Vivo") },
                            leadingIcon = { Icon(Icons.Default.Sports, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToMatchesList()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Entrenamientos y Asistencia") },
                            leadingIcon = { Icon(Icons.Default.FitnessCenter, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToTrainingSessions()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Mis academias / Cambiar") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                showSwitchAcademyDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Editar datos de esta academia") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToEditAcademy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Registrar otra academia") },
                            leadingIcon = { Icon(Icons.Default.AddBusiness, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onNavigateToCreateNewAcademy()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onNavigateToCreateTeam,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+ Nuevo equipo")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Academy Banner Card (clickable to switch academy)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSwitchAcademyDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = academy.name.take(2).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = academy.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${academy.city} · ${academy.institutionalEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onNavigateToEditAcademy) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar academia")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Verification warning badge
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pendiente de verificación: podrás publicar avisos de pruebas cuando SportPro verifique tu academia.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Teams grouped by category
            teamsByCategory.forEach { (category, teamList) ->
                item {
                    Text(
                        text = category.displayName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(teamList) { team ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (team.coachId == null) {
                                    teamToReassign = team
                                } else {
                                    onNavigateToEditTeam(team)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = team.name.take(3),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (team.coachName != null) {
                                        "DT ${team.coachName} · ${team.playerCount} jugadores"
                                    } else {
                                        "${team.playerCount} jugadores"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (team.coachId == null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFFEBEE))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Sin DT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFC62828),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
