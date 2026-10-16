package pe.edu.esan.sportpro.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.Invitation
import pe.edu.esan.sportpro.data.remote.InvitationRepository
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InitialsBadge
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.ErrorBorde
import pe.edu.esan.sportpro.ui.theme.Fondo
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-001 · Pantallas 2 y 3: ingresar el código (válido / no válido)
@Composable
fun InvitationCodeScreen(
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val invitation = state.invitation

    SportProScreen {
        TopBarWithSteps(onBack = onBack, step = 1)
        Spacer(Modifier.height(32.dp))
        Title("¡Hola! Escribe tu código")
        Spacer(Modifier.height(8.dp))
        Subtitle("Tu academia te lo envió por mensaje. Sirve una sola vez y dura 7 días.")
        Spacer(Modifier.height(24.dp))

        SportProCard {
            Text("Código de invitación", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                singleLine = true,
                isError = state.codeError != null,
                placeholder = { Text("ABCD-1234", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = TextoSecundario, fontSize = 26.sp) },
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp, textAlign = TextAlign.Center, color = TextoPrincipal),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.validateCode() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Turquesa,
                    unfocusedBorderColor = Turquesa,
                    errorBorderColor = ErrorBorde,
                    focusedContainerColor = Fondo,
                    unfocusedContainerColor = Fondo,
                    errorContainerColor = Fondo
                ),
                modifier = Modifier.fillMaxWidth()
            )

            state.codeError?.let { error ->
                ErrorBox(error)
                // El recordatorio solo tiene sentido si el código realmente no sirve (no si falló la red)
                if (error == InvitationRepository.INVALID_CODE_MESSAGE) {
                    Text(
                        "Recuerda: cada código se usa una sola vez y vence a los 7 días de generado.",
                        color = TextoSecundario,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (invitation != null) {
                InvitationFound(invitation)
            }
        }

        Spacer(Modifier.height(24.dp))
        if (invitation == null) {
            PrimaryButton(
                text = "Validar código",
                onClick = viewModel::validateCode,
                enabled = state.code.isNotBlank(),
                loading = state.isLoading
            )
        } else {
            PrimaryButton(text = "Continuar", onClick = onContinue)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "¿No tienes código? Pídelo a tu academia.",
            color = TextoSecundario,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// La app muestra academia, rol y equipo ANTES de continuar (criterio 2 de US-001)
@Composable
private fun InvitationFound(invitation: Invitation) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Turquesa, modifier = Modifier.size(20.dp))
        Text("¡Te encontramos!", color = Turquesa, fontWeight = FontWeight.ExtraBold)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SuperficieSuave)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InitialsBadge(initialsOf(invitation.academyName))
        Column {
            Text(invitation.academyName, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            val place = listOf(invitation.teamName, invitation.city).filter { it.isNotBlank() }.joinToString(" · ")
            Text(place, color = TextoSobreSuave, fontSize = 14.sp)
        }
    }
    Text(
        buildAnnotatedString {
            append("Te unirás como ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(invitation.roleName) }
            if (invitation.playerName.isNotBlank()) {
                append(" de ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(invitation.playerName) }
            }
            if (invitation.invitedByName.isNotBlank()) append(", invitado por ${invitation.invitedByName}")
            append(".")
        },
        style = MaterialTheme.typography.bodyLarge
    )
}

// "Academia Los Halcones" -> "LH"
fun initialsOf(name: String): String {
    val words = name.split(" ").filter { it.isNotBlank() && it.lowercase() !in listOf("academia", "de", "del", "la", "el") }
    return words.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "SP" }
}
