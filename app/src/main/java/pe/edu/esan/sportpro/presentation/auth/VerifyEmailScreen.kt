package pe.edu.esan.sportpro.presentation.auth

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.Illustration
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-001 · Verifica tu correo.
// Se usa al terminar el registro y también al iniciar sesión con un correo sin verificar (fromLogin = true).
@Composable
fun VerifyEmailScreen(
    viewModel: RegisterViewModel,
    onActive: () -> Unit,
    onUseOtherAccount: () -> Unit,
    fromLogin: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val email = viewModel.currentEmail()

    SportProScreen {
        Illustration(icon = Icons.Outlined.MailOutline)
        Spacer(Modifier.height(28.dp))
        Text(
            if (fromLogin) "Primero confirma tu correo" else "¡Revisa tu correo!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                append(if (fromLogin) "Para entrar, abre el enlace que enviamos a " else "Te enviamos un enlace a ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = TextoPrincipal)) { append(email) }
                append(if (fromLogin) "." else ". Ábrelo para activar tu cuenta.")
            },
            color = TextoSecundario,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        if (!fromLogin) {
            SportProCard {
                StepLine(done = true, text = "Código de invitación aplicado")
                StepLine(done = true, text = "Términos de uso aceptados")
                StepLine(done = false, text = "Confirmar correo · pendiente")
            }
        }

        state.infoMessage?.let {
            Spacer(Modifier.height(12.dp))
            InfoBox { Text(it, color = TextoSobreSuave, fontWeight = FontWeight.Bold) }
        }
        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Abrir mi correo",
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // El emulador puede no tener app de correo: se ignora
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = "Ya lo confirmé",
            onClick = { viewModel.checkVerified(onActive = onActive) },
            enabled = !state.isLoading
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (state.resendSeconds > 0) {
                Text("Reenviar enlace en 0:%02d".format(state.resendSeconds), color = TextoSecundario)
            } else {
                Text(
                    "Reenviar enlace",
                    color = Turquesa,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .heightIn(min = 44.dp)   // área táctil mínima
                        .clickable { viewModel.resendEmail() }
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "¿Correo equivocado? Usar otra cuenta",
            color = Turquesa,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)   // área táctil mínima
                .clickable(onClick = onUseOtherAccount)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}

@Composable
private fun StepLine(done: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule,
            contentDescription = null,
            tint = if (done) Turquesa else TextoSecundario,
            modifier = Modifier.size(22.dp)
        )
        Text(text, fontWeight = FontWeight.Bold, color = TextoPrincipal)
    }
}
