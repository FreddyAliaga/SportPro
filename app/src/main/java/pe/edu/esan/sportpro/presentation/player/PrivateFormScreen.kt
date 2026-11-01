package pe.edu.esan.sportpro.presentation.player

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.data.model.toBirthText
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LogoPicker
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario

// US-003 · Datos privados que editan el apoderado (de sus jugadores) y el jugador mayor (los suyos):
// contacto, contacto de emergencia, foto y datos físicos. La fecha de nacimiento solo la editan el DT o el ADM.
@Composable
fun PrivateFormScreen(
    viewModel: PlayerViewModel,
    onPhotoPicked: (Uri) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.privateForm
    val errors = state.privateErrors
    val player = state.player
    val isSelf = player != null && player.userId == state.profile?.uid

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title(if (isSelf) "Mis datos" else "Datos de ${player?.name?.split(" ")?.first().orEmpty()}")
        Spacer(Modifier.height(8.dp))
        Subtitle("Solo los ven tu academia (DT y administración) y ${if (isSelf) "tú" else "sus apoderados"}.")
        Spacer(Modifier.height(20.dp))

        SportProCard {
            LogoPicker(
                logoBase64 = form.photoBase64,
                loading = state.photoLoading,
                onPicked = onPhotoPicked,
                onRemove = { viewModel.onPrivateFormChange(form.copy(photoBase64 = "")) },
                title = "Foto del jugador",
                removeText = "Quitar foto"
            )
            state.playerPrivate?.birthDate?.let {
                InfoBox {
                    Text(
                        "Nacimiento: ${it.toBirthText()}. Si no es correcto, pide al DT que lo corrija.",
                        color = TextoPrincipal,
                        fontSize = 14.sp
                    )
                }
                Text(PrivacyTexts.BIRTH_DATE_LOCKED, color = TextoSecundario, fontSize = 12.sp)
            }
            SportProTextField(
                value = form.contactPhone,
                onValueChange = { viewModel.onPrivateFormChange(form.copy(contactPhone = it.filter(Char::isDigit).take(9))) },
                label = "Celular de contacto (opcional)",
                placeholder = "987654321",
                error = errors.contactPhone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Contacto de emergencia")
        Spacer(Modifier.height(10.dp))
        SportProCard {
            SportProTextField(
                value = form.emergencyName,
                onValueChange = { viewModel.onPrivateFormChange(form.copy(emergencyName = it.take(60))) },
                label = "Nombre",
                placeholder = "Rosa Paz",
                error = errors.emergencyName,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            SportProTextField(
                value = form.emergencyRelation,
                onValueChange = { viewModel.onPrivateFormChange(form.copy(emergencyRelation = it.take(30))) },
                label = "Parentesco",
                placeholder = "Madre",
                error = errors.emergencyRelation,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            SportProTextField(
                value = form.emergencyPhone,
                onValueChange = { viewModel.onPrivateFormChange(form.copy(emergencyPhone = it.filter(Char::isDigit).take(9))) },
                label = "Celular",
                placeholder = "987654321",
                error = errors.emergencyPhone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Datos físicos (opcionales)")
        Spacer(Modifier.height(10.dp))
        SportProCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportProTextField(
                    value = form.heightCm,
                    onValueChange = { viewModel.onPrivateFormChange(form.copy(heightCm = it.filter(Char::isDigit).take(3))) },
                    label = "Estatura (cm)",
                    placeholder = "150",
                    error = errors.heightCm,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                SportProTextField(
                    value = form.weightKg,
                    onValueChange = { viewModel.onPrivateFormChange(form.copy(weightKg = it.filter(Char::isDigit).take(3))) },
                    label = "Peso (kg)",
                    placeholder = "42",
                    error = errors.weightKg,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Messages(state.errorMessage, state.infoMessage)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Guardar datos", onClick = { viewModel.savePrivate(onSaved) }, loading = state.isLoading)
    }
}
