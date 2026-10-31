package pe.edu.esan.sportpro.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.PLAYER_FEET
import pe.edu.esan.sportpro.data.model.PLAYER_POSITIONS
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.data.model.TEAM_CATEGORIES
import pe.edu.esan.sportpro.data.model.ageFromDigits
import pe.edu.esan.sportpro.data.model.isMinorAge
import pe.edu.esan.sportpro.presentation.components.AvisoProteccionDatos
import pe.edu.esan.sportpro.presentation.components.DateVisualTransformation
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SelectablePill
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.Coral
import pe.edu.esan.sportpro.ui.theme.CoralClaro
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-003 · Nuevo jugador / Editar datos deportivos (DT o ADM).
// Datos deportivos + fecha de nacimiento (solo la editan el DT o el ADM): con ella se sabe si es menor.
@Composable
fun PlayerFormScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.sportForm
    val errors = state.sportErrors
    val isNew = state.editingNew
    val age = ageFromDigits(form.birthDate)

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title(if (isNew) "Nuevo jugador" else "Datos deportivos")
        Spacer(Modifier.height(8.dp))
        Subtitle(
            if (isNew) "Agrégalo a ${state.rosterTeam?.name ?: "tu equipo"}. Su apoderado completará luego sus datos de contacto."
            else "Los ven el DT, la administración, sus apoderados y el propio jugador."
        )
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = form.name,
                onValueChange = { viewModel.onSportFormChange(form.copy(name = it.take(60))) },   // las reglas aceptan hasta 60
                label = "Nombre completo",
                placeholder = "Mateo Rodríguez",
                error = errors.name,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            SportProTextField(
                value = form.birthDate,
                // Solo se guardan los dígitos; las barras las pone DateVisualTransformation
                onValueChange = { viewModel.onSportFormChange(form.copy(birthDate = it.filter(Char::isDigit).take(8))) },
                label = "Fecha de nacimiento",
                placeholder = "DD/MM/AAAA",
                error = errors.birthDate,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DateVisualTransformation()
            )
            if (age != null && errors.birthDate == null) {
                InfoBox {
                    Text(
                        if (isMinorAge(age)) {
                            "Tiene $age años: es menor de edad. Quedará \"Pendiente de apoderado\" hasta que su apoderado " +
                                "se vincule con un código y registre un contacto de emergencia."
                        } else {
                            "Tiene $age años: es mayor de edad. Puedes invitarlo con un código de jugador para que gestione su perfil."
                        },
                        color = TextoPrincipal,
                        fontSize = 14.sp
                    )
                }
                if (isMinorAge(age)) {
                    AvisoProteccionDatos(PrivacyTexts.TITLE_PERSONAL_DATA, PrivacyTexts.NEW_MINOR_PLAYER, compact = true)
                }
            }
            SportProTextField(
                value = form.dorsal,
                onValueChange = { viewModel.onSportFormChange(form.copy(dorsal = it.filter(Char::isDigit).take(2))) },
                label = "Dorsal (opcional)",
                placeholder = "10",
                error = errors.dorsal,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Spacer(Modifier.height(16.dp))
        SportProCard {
            PillGroup("Categoría", TEAM_CATEGORIES, form.category) { viewModel.onSportFormChange(form.copy(category = it)) }
            PillGroup("Posición principal", PLAYER_POSITIONS, form.positionMain) {
                // Si la secundaria era la misma, queda en Ninguna
                val secondary = if (form.positionSecondary == it) "" else form.positionSecondary
                viewModel.onSportFormChange(form.copy(positionMain = it, positionSecondary = secondary))
            }
            PillGroup(
                "Posición secundaria",
                listOf(NO_POSITION) + PLAYER_POSITIONS.filter { it != form.positionMain },
                form.positionSecondary.ifEmpty { NO_POSITION }
            ) { viewModel.onSportFormChange(form.copy(positionSecondary = if (it == NO_POSITION) "" else it)) }
            PillGroup("Pie dominante", PLAYER_FEET, form.foot) { viewModel.onSportFormChange(form.copy(foot = it)) }
        }

        // Todo menor necesita la autorización: si aún no la tiene (nuevo, o un mayor que pasa a menor),
        // se pide la casilla y sin ella no se guarda; si ya la tiene, solo se muestra quién la declaró
        val existingAuthorization = state.existingGuardianAuthorization
        if (state.needsGuardianAuthorization) {
            Spacer(Modifier.height(16.dp))
            GuardianAuthorizationBox(
                checked = form.guardianAuthorized,
                error = errors.guardianAuthorization,
                onCheckedChange = { viewModel.onSportFormChange(form.copy(guardianAuthorized = it)) }
            )
        } else if (existingAuthorization != null) {
            Spacer(Modifier.height(12.dp))
            GuardianAuthorizationLine(existingAuthorization)
        }

        PlayerMessages(state)
        Spacer(Modifier.height(20.dp))
        // Mientras se guarda (isLoading del ViewModel): "Guardando…" y deshabilitado, para no tocarlo dos veces
        PrimaryButton(
            if (state.isLoading) "Guardando…" else if (isNew) "Agregar al plantel" else "Guardar cambios",
            onClick = { viewModel.saveSport(onSaved) },
            enabled = !state.isLoading
        )
    }
}

// Casilla "Cuento con la autorización del apoderado" (Reglamento D.S. 016-2024-JUS, art. 22)
// en un recuadro coral destacado, con su error debajo
@Composable
private fun GuardianAuthorizationBox(checked: Boolean, error: String?, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(CoralClaro)
                .border(1.5.dp, Coral, shape)
                .clickable { onCheckedChange(!checked) }
                .padding(end = 14.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = Turquesa, uncheckedColor = Coral, checkmarkColor = Tarjeta)
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(PrivacyTexts.GUARDIAN_AUTHORIZATION_BOLD) }
                    append(PrivacyTexts.GUARDIAN_AUTHORIZATION_REST)
                },
                color = TextoPrincipal,
                fontSize = 14.sp
            )
        }
        error?.let { Text(it, color = ErrorTexto, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
    }
}

// Texto que se muestra para la posición secundaria vacía
private const val NO_POSITION = "Ninguna"

// Grupo de píldoras seleccionables (una sola opción)
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillGroup(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextoPrincipal, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                SelectablePill(option, selected = option == selected) { onSelect(option) }
            }
        }
    }
}
