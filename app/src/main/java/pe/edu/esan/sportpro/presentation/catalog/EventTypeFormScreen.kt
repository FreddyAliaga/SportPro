package pe.edu.esan.sportpro.presentation.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.EVENT_COLORS
import pe.edu.esan.sportpro.data.model.EVENT_ICONS
import pe.edu.esan.sportpro.data.model.EVENT_NAME_MAX
import pe.edu.esan.sportpro.presentation.components.ConfirmDialog
import pe.edu.esan.sportpro.presentation.components.EventTypeBadge
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.SwitchRow
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.eventColor
import pe.edu.esan.sportpro.presentation.components.eventIconLabel
import pe.edu.esan.sportpro.presentation.components.eventIconVector
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.Fondo
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-007 · Nuevo / Editar tipo de evento (ADM): nombre, ícono, color, propiedades y desactivar o reactivar
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventTypeFormScreen(
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.form
    val editing = state.editingType
    val isNew = form.id.isEmpty()
    var showDeactivate by remember { mutableStateOf(false) }

    if (showDeactivate && editing != null) {
        ConfirmDialog(
            title = "¿Desactivar ${editing.name}?",
            text = "Ya no se podrá elegir en los partidos. No se borra: los eventos ya registrados se conservan " +
                "tal como están y puedes reactivarlo cuando quieras.",
            confirmText = "Desactivar",
            onDismiss = { showDeactivate = false },
            onConfirm = {
                showDeactivate = false
                viewModel.setActive(editing, active = false, onDone = onSaved)
            }
        )
    }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title(if (isNew) "Nuevo tipo de evento" else "Editar tipo de evento")
        Spacer(Modifier.height(8.dp))
        Subtitle("Los cambios no modifican los eventos ya registrados en los partidos.")
        Spacer(Modifier.height(20.dp))

        // Vista previa
        SportProCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventTypeBadge(form.icon, form.color, size = 56.dp, contentDescription = eventIconLabel(form.icon))
                Spacer(Modifier.width(14.dp))
                Text(
                    form.name.trim().ifEmpty { "Nombre del evento" },
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    color = if (form.name.isBlank()) TextoSecundario else TextoPrincipal
                )
            }
            SportProTextField(
                value = form.name,
                onValueChange = { viewModel.onFormChange(form.copy(name = it.take(EVENT_NAME_MAX))) },
                label = "Nombre",
                placeholder = "Asistencia",
                error = state.nameError
            )
        }

        Spacer(Modifier.height(16.dp))
        SportProCard {
            Text("Ícono", style = MaterialTheme.typography.labelMedium, color = TextoPrincipal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EVENT_ICONS.forEach { icon ->
                    val selected = form.icon == icon.key
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Turquesa else Fondo)
                            .border(2.dp, if (selected) Turquesa else BordeSuave, RoundedCornerShape(14.dp))
                            .selectable(selected = selected, role = Role.RadioButton) {
                                viewModel.onFormChange(form.copy(icon = icon.key))
                            }
                            .semantics { contentDescription = icon.label },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(eventIconVector(icon.key), contentDescription = null, tint = if (selected) Tarjeta else TextoPrincipal)
                    }
                }
            }
            Text("Color", style = MaterialTheme.typography.labelMedium, color = TextoPrincipal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EVENT_COLORS.forEach { option ->
                    val selected = form.color == option.hex
                    val color = eventColor(option.hex)
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(3.dp, if (selected) TextoPrincipal else Tarjeta, CircleShape)
                            .selectable(selected = selected, role = Role.RadioButton) {
                                viewModel.onFormChange(form.copy(color = option.hex))
                            }
                            .semantics { contentDescription = option.label },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = if (color.luminance() > 0.5f) TextoPrincipal else Tarjeta
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SportProCard {
            val locked = form.alwaysActive
            SwitchRow(
                "Afecta el marcador",
                if (locked) "Inicio y Fin no cambian el marcador." else "Suma un gol al equipo que lo registra.",
                checked = form.affectsScore,
                onCheckedChange = { viewModel.onFormChange(form.copy(affectsScore = it)) },
                enabled = !locked
            )
            HorizontalDivider(color = BordeSuave)
            SwitchRow(
                "Requiere jugador",
                "Hay que elegir al jugador del evento.",
                checked = form.requiresPlayer,
                onCheckedChange = { viewModel.onFormChange(form.copy(requiresPlayer = it)) },
                enabled = !locked
            )
            HorizontalDivider(color = BordeSuave)
            SwitchRow(
                "Requiere equipo",
                "Hay que indicar si fue de tu equipo o del rival.",
                checked = form.requiresTeam,
                onCheckedChange = { viewModel.onFormChange(form.copy(requiresTeam = it)) },
                enabled = !locked
            )
            HorizontalDivider(color = BordeSuave)
            SwitchRow(
                "Admite observación",
                "Permite escribir una nota corta.",
                checked = form.allowsNote,
                onCheckedChange = { viewModel.onFormChange(form.copy(allowsNote = it)) }
            )
        }

        Messages(state.errorMessage, state.infoMessage)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (isNew) "Agregar al catálogo" else "Guardar cambios", onClick = { viewModel.save(onSaved) }, loading = state.isLoading)

        // Desactivar / Reactivar (un tipo ya guardado)
        if (editing != null) {
            Spacer(Modifier.height(12.dp))
            when {
                editing.alwaysActive -> InfoBox {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = Turquesa)
                    Text("Inicio y Fin siempre están activos", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                editing.active -> SecondaryButton("Desactivar", { showDeactivate = true }, enabled = !state.isLoading)
                else -> SecondaryButton(
                    "Reactivar",
                    { viewModel.setActive(editing, active = true, onDone = onSaved) },
                    enabled = !state.isLoading
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
