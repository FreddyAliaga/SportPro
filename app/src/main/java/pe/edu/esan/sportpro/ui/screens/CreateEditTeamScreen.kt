package pe.edu.esan.sportpro.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.domain.model.Category
import pe.edu.esan.sportpro.domain.model.Team
import pe.edu.esan.sportpro.ui.viewmodel.AcademyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTeamScreen(
    viewModel: AcademyViewModel,
    existingTeam: Team? = null,
    onNavigateBack: () -> Unit,
    onTeamSaved: (Team) -> Unit,
    onInviteCoachRequested: (Team) -> Unit
) {
    val users by viewModel.users.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val coaches = remember(users) {
        users.filter { it.role.code == "DT" && it.active }
    }

    var teamName by remember { mutableStateOf(existingTeam?.name ?: "") }
    var selectedCategory by remember { mutableStateOf(existingTeam?.category ?: Category.SUB_15) }
    var maxStarters by remember { mutableStateOf((existingTeam?.maxStarters ?: 11).toString()) }
    var maxSubstitutes by remember { mutableStateOf((existingTeam?.maxSubstitutes ?: 12).toString()) }
    var selectedCoachId by remember { mutableStateOf(existingTeam?.coachId) }
    var selectedCoachName by remember { mutableStateOf(existingTeam?.coachName) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var coachDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingTeam == null) "Nuevo equipo" else "Editar equipo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Cada equipo pertenece a una categoría y tiene un DT a cargo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Team Name
            OutlinedTextField(
                value = teamName,
                onValueChange = { teamName = it },
                label = { Text("Nombre del equipo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage?.contains("nombre") == true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryDropdownExpanded,
                onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCategory.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                selectedCategory = category
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Limits: Starters & Substitutes
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = maxStarters,
                    onValueChange = { maxStarters = it },
                    label = { Text("Máx. titulares") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = errorMessage?.contains("titular") == true
                )

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedTextField(
                    value = maxSubstitutes,
                    onValueChange = { maxSubstitutes = it },
                    label = { Text("Máx. suplentes") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Assigned DT Dropdown
            ExposedDropdownMenuBox(
                expanded = coachDropdownExpanded,
                onExpandedChange = { coachDropdownExpanded = !coachDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCoachName ?: "Sin DT",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("DT asignado") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = coachDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = coachDropdownExpanded,
                    onDismissRequest = { coachDropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin DT") },
                        onClick = {
                            selectedCoachId = null
                            selectedCoachName = null
                            coachDropdownExpanded = false
                        }
                    )
                    coaches.forEach { coach ->
                        DropdownMenuItem(
                            text = { Text(coach.name) },
                            onClick = {
                                selectedCoachId = coach.userId
                                selectedCoachName = coach.name
                                coachDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtext Notice
            Text(
                text = "¿Tu DT aún no está en SportPro? Guarda el equipo como Sin DT e invítalo con un código.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val starters = maxStarters.toIntOrNull() ?: 11
                    val subs = maxSubstitutes.toIntOrNull() ?: 12

                    viewModel.saveTeam(
                        teamId = existingTeam?.id ?: "",
                        name = teamName,
                        category = selectedCategory,
                        coachId = selectedCoachId,
                        coachName = selectedCoachName,
                        maxStarters = starters,
                        maxSubstitutes = subs,
                        onSuccess = { savedTeam ->
                            if (selectedCoachId == null) {
                                onInviteCoachRequested(savedTeam)
                            } else {
                                onTeamSaved(savedTeam)
                            }
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Guardar equipo")
            }
        }
    }
}
