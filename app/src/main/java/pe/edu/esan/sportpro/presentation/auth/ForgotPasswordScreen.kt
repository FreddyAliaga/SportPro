package pe.edu.esan.sportpro.presentation.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-001 · Recuperar contraseña: Firebase envía un enlace al correo
@Composable
fun ForgotPasswordScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(28.dp))
        Title("Recupera tu contraseña")
        Spacer(Modifier.height(8.dp))
        Subtitle("Escribe tu correo y te enviaremos un enlace para crear una nueva.")
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = state.resetEmail,
                onValueChange = viewModel::onResetEmailChange,
                label = "Correo electrónico",
                error = state.resetEmailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            if (state.resetSent) {
                InfoBox {
                    Icon(Icons.Outlined.MarkEmailRead, contentDescription = null, tint = Turquesa)
                    Text(
                        "¡Listo! Si el correo está registrado, te llegará el enlace en unos minutos. Revisa también tu carpeta de spam.",
                        color = TextoSobreSuave,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }

        Spacer(Modifier.height(24.dp))
        if (state.resetSent) {
            PrimaryButton(text = "Volver a iniciar sesión", onClick = onBack)
        } else {
            PrimaryButton(text = "Enviar enlace", loading = state.isLoading, onClick = viewModel::sendPasswordReset)
        }
    }
}
