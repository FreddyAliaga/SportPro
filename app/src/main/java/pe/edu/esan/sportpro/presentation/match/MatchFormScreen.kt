package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.data.model.PLACE_MAX
import pe.edu.esan.sportpro.data.model.RIVAL_MAX
import pe.edu.esan.sportpro.presentation.components.DateVisualTransformation
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TimeVisualTransformation
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps

// US-006 · "Nuevo partido" (y "Reprogramar partido", Parte C): rival, fecha, hora y lugar.
// La fecha y la hora se escriben en la hora del teléfono; se guardan como un solo momento (Timestamp).
@Composable
fun MatchFormScreen(
    viewModel: MatchViewModel,
    title: String,
    subtitle: String,
    buttonText: String,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    rivalEditable: Boolean = true      // al reprogramar solo cambian fecha, hora y lugar
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.form
    val errors = state.formErrors

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title(title)
        Spacer(Modifier.height(8.dp))
        Subtitle(subtitle)
        Spacer(Modifier.height(20.dp))

        SportProCard {
            if (rivalEditable) {
                SportProTextField(
                    value = form.rival,
                    onValueChange = { viewModel.onFormChange(form.copy(rival = it.take(RIVAL_MAX))) },
                    label = "Rival",
                    placeholder = "Club Los Pumas",
                    error = errors.rival
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportProTextField(
                    value = form.date,
                    onValueChange = { viewModel.onFormChange(form.copy(date = it.filter(Char::isDigit).take(8))) },
                    label = "Fecha",
                    placeholder = "DD/MM/AAAA",
                    error = errors.date,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                    modifier = Modifier.weight(1.3f)
                )
                SportProTextField(
                    value = form.time,
                    onValueChange = { viewModel.onFormChange(form.copy(time = it.filter(Char::isDigit).take(4))) },
                    label = "Hora",
                    placeholder = "HH:MM",
                    error = errors.time,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = TimeVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
            SportProTextField(
                value = form.place,
                onValueChange = { viewModel.onFormChange(form.copy(place = it.take(PLACE_MAX))) },
                label = "Lugar",
                placeholder = "Estadio municipal, cancha 2",
                error = errors.place
            )
        }

        Messages(state.errorMessage, state.infoMessage)
        Spacer(Modifier.height(20.dp))
        PrimaryButton(buttonText, onClick = onSubmit, loading = state.isLoading)
    }
}
