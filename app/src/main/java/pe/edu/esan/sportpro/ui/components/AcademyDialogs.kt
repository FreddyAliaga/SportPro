package pe.edu.esan.sportpro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.domain.model.Academy
import pe.edu.esan.sportpro.domain.model.AcademyUser
import pe.edu.esan.sportpro.domain.model.Team

@Composable
fun ReassignCoachDialog(
    team: Team,
    coaches: List<AcademyUser>,
    onDismiss: () -> Unit,
    onConfirm: (coachId: String?, coachName: String?) -> Unit
) {
    var selectedCoachId by remember { mutableStateOf(team.coachId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(text = team.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${team.category.displayName} · ${team.playerCount} jugadores",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = "Asignar DT a ${team.name}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Option: Sin DT
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(
                            selected = (selectedCoachId == null),
                            onClick = { selectedCoachId = null },
                            role = Role.RadioButton
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedCoachId == null),
                        onClick = null
                    )
                    Text(
                        text = "Sin DT",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }

                coaches.forEach { coach ->
                    val isSelected = (selectedCoachId == coach.userId)
                    val assignedTeamsText = if (coach.assignedTeamNames.isNotEmpty()) {
                        " (${coach.assignedTeamNames.joinToString(", ")})"
                    } else ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = { selectedCoachId = coach.userId },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Text(
                            text = coach.name + assignedTeamsText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "El cambio se registra y el historial del equipo se conserva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosenCoach = coaches.find { it.userId == selectedCoachId }
                    onConfirm(chosenCoach?.userId, chosenCoach?.name)
                }
            ) {
                Text("Guardar cambio")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun DeactivateUserDialog(
    user: AcademyUser,
    onDismiss: () -> Unit,
    onConfirmToggle: () -> Unit
) {
    val isCurrentlyActive = user.active
    val titleText = if (isCurrentlyActive) "¿Desactivar la cuenta de ${user.name}?" else "¿Reactivar la cuenta de ${user.name}?"
    val bodyText = if (isCurrentlyActive) {
        "Ya no podrá iniciar sesión. Sus partidos, entrenamientos y registros anteriores se conservan."
    } else {
        "El usuario podrá volver a iniciar sesión con sus mismos permisos."
    }
    val actionText = if (isCurrentlyActive) "Desactivar" else "Reactivar"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = titleText, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Text(text = bodyText, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onConfirmToggle,
                colors = if (isCurrentlyActive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else ButtonDefaults.buttonColors()
            ) {
                Text(actionText)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun SwitchAcademyDialog(
    academies: List<Academy>,
    currentAcademyId: String,
    onDismiss: () -> Unit,
    onSelectAcademy: (String) -> Unit,
    onCreateNewAcademy: () -> Unit
) {
    var selectedId by remember { mutableStateOf(currentAcademyId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Mis academias", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = "Selecciona la academia activa para gestionar sus equipos y usuarios:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                academies.forEach { item ->
                    val isSelected = (selectedId == item.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = {
                                    selectedId = item.id
                                },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = item.city,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onCreateNewAcademy()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Registrar nueva academia")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectAcademy(selectedId)
                    onDismiss()
                }
            ) {
                Text("Cambiar academia")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
