package pe.edu.esan.sportpro.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProLogo
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-001 · Iniciar sesión con correo y contraseña
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onNotVerified: () -> Unit,
    onForgotPassword: () -> Unit,
    onUseCode: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        SportProLogo()
        Spacer(Modifier.height(20.dp))
        Title("¡Qué bueno verte de nuevo!")
        Spacer(Modifier.height(8.dp))
        Subtitle("Ingresa con el correo y la contraseña de tu cuenta.")
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = "Correo electrónico",
                error = state.emailError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            SportProTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = "Contraseña",
                error = state.passwordError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                            tint = TextoSecundario
                        )
                    }
                }
            )
            Text(
                "¿Olvidaste tu contraseña?",
                color = Turquesa,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)   // área táctil mínima
                    .clickable {
                        viewModel.startPasswordReset()
                        onForgotPassword()
                    }
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Turquesa, modifier = Modifier.size(18.dp))
            Text("Tu sesión queda abierta hasta que la cierres.", color = TextoSobreSuave, style = MaterialTheme.typography.bodyMedium)
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Iniciar sesión",
            loading = state.isLoading,
            onClick = { viewModel.login(onActive = onLoggedIn, onNotVerified = onNotVerified) }
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "¿Aún no tienes cuenta? Usa tu código de invitación",
            color = Turquesa,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)   // área táctil mínima
                .clickable(onClick = onUseCode)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}
