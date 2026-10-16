package pe.edu.esan.sportpro.presentation.academy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.INVITATION_VALID_DAYS
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SmallChip
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.TextLink
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-002 · Invita a un entrenador: muestra el código recién generado para compartirlo
@Composable
fun InviteCoachScreen(viewModel: AcademyViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val code = state.generatedCode
    val academyName = state.academy?.name.orEmpty()

    SportProScreen {
        TopBarWithSteps(onBack = onDone)
        Spacer(Modifier.height(20.dp))
        Title("Invita a un entrenador")
        Spacer(Modifier.height(8.dp))
        Subtitle("Envíale este código. Con él se registra como DT de tu academia.")
        Spacer(Modifier.height(20.dp))

        SportProCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                SmallChip("DT · ${state.generatedTeamName}")
                Spacer(Modifier.height(14.dp))
                Text(
                    code,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    letterSpacing = 2.sp,
                    color = Turquesa,
                    textAlign = TextAlign.Center
                )
            }
            Notice(Icons.Outlined.Schedule, "Vence en $INVITATION_VALID_DAYS días")
            Notice(Icons.Outlined.CheckCircle, "Se usa una sola vez")
        }

        Spacer(Modifier.height(24.dp))
        PrimaryButton("Compartir código", onClick = {
            // Mensaje sin datos sensibles: solo academia, equipo y código
            val message = "¡Hola! Te invito a SportPro como DT del equipo ${state.generatedTeamName} de $academyName. " +
                "Descarga la app, elige \"Tengo un código de invitación\" y escribe: $code (vence en $INVITATION_VALID_DAYS días)."
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir código"))
        })
        Spacer(Modifier.height(12.dp))
        SecondaryButton(if (copied) "¡Copiado!" else "Copiar código", onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Código SportPro", code))
            copied = true
        })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextLink("Listo", onClick = onDone)
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Turquesa, modifier = Modifier.size(18.dp))
        Text(text, color = TextoSobreSuave, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
