package pe.edu.esan.sportpro.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.presentation.components.DateVisualTransformation
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.Borde
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-001 · Crea tu cuenta (con la casilla "Acepto los términos de uso")
@Composable
fun CreateAccountScreen(
    viewModel: RegisterViewModel,
    onBack: () -> Unit,
    onMinor: () -> Unit,
    onAccountCreated: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form = state.form
    val errors = state.formErrors
    var passwordVisible by remember { mutableStateOf(false) }

    SportProScreen {
        TopBarWithSteps(onBack = onBack, step = 2)
        Spacer(Modifier.height(28.dp))
        Title("Crea tu cuenta")
        val roleLine = when {
            state.invitation != null ->
                "${state.invitation!!.roleName.replaceFirstChar { it.uppercase() }} · ${state.invitation!!.academyName}"
            state.registeringAcademy -> "Administrador · ${state.academyForm.name.trim()}"   // US-002
            else -> null
        }
        roleLine?.let {
            Spacer(Modifier.height(10.dp))
            Text("●  $it", color = TextoSobreSuave, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = form.fullName,
                onValueChange = { viewModel.onFormChange(form.copy(fullName = it)) },
                label = "Nombre completo",
                error = errors.fullName
            )
            SportProTextField(
                value = form.email,
                onValueChange = { viewModel.onFormChange(form.copy(email = it)) },
                label = "Correo electrónico",
                error = errors.email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportProTextField(
                    value = form.phone,
                    onValueChange = { viewModel.onFormChange(form.copy(phone = it.filter(Char::isDigit).take(9))) },
                    label = "Celular",
                    placeholder = "987654321",
                    error = errors.phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f)
                )
                SportProTextField(
                    value = form.birthDate,
                    // Solo se guardan los dígitos; las barras las pone DateVisualTransformation
                    onValueChange = { viewModel.onFormChange(form.copy(birthDate = it.filter(Char::isDigit).take(8))) },
                    label = "Nacimiento",
                    placeholder = "DD/MM/AAAA",
                    error = errors.birthDate,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DateVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Usamos tu fecha de nacimiento solo para confirmar que eres mayor de edad.",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium
            )
            SportProTextField(
                value = form.password,
                onValueChange = { viewModel.onFormChange(form.copy(password = it)) },
                label = "Contraseña",
                error = errors.password,
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
            // Reglas de la contraseña que se van marcando mientras escribe
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PasswordRule("8 caracteres o más", form.password.length >= 8)
                PasswordRule("Una mayúscula", form.password.any { it.isUpperCase() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PasswordRule("Una minúscula", form.password.any { it.isLowerCase() })
                PasswordRule("Un número", form.password.any { it.isDigit() })
            }
        }

        Spacer(Modifier.height(20.dp))
        // Casilla de términos de uso: sin marcarla no se crea la cuenta
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { viewModel.onTermsChange(!state.termsAccepted) }
        ) {
            Checkbox(
                checked = state.termsAccepted,
                onCheckedChange = { viewModel.onTermsChange(it) },
                colors = CheckboxDefaults.colors(checkedColor = Turquesa, uncheckedColor = Borde, checkmarkColor = Tarjeta)
            )
            Text(
                buildAnnotatedString {
                    append("Acepto los ")
                    withStyle(SpanStyle(color = Turquesa, fontWeight = FontWeight.ExtraBold)) { append("términos de uso") }
                    append(" de SportPro")
                },
                color = TextoPrincipal,
                fontSize = 15.sp
            )
        }
        state.termsError?.let {
            Text(it, color = ErrorTexto, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            ErrorBox(it)
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Crear cuenta",
            loading = state.isLoading,
            onClick = {
                val age = viewModel.validateForm()
                when {
                    age == null -> Unit                      // hay errores: se muestran en cada campo
                    viewModel.isMinor(age) -> onMinor()      // RN-003: menores de 18 no tienen cuenta
                    else -> viewModel.createAccount(onSuccess = onAccountCreated)
                }
            }
        )
    }
}

@Composable
private fun PasswordRule(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (ok) Turquesa else SuperficieSuave,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (ok) Turquesa else TextoSecundario)
    }
}
