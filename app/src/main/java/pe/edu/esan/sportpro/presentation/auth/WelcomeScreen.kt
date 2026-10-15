package pe.edu.esan.sportpro.presentation.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.presentation.components.InitialsBadge
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SmallChip
import pe.edu.esan.sportpro.presentation.components.SportProLogo
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.ui.theme.Coral
import pe.edu.esan.sportpro.ui.theme.SportProTheme
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// US-001 · Pantalla 1: Bienvenida
@Composable
fun WelcomeScreen(
    onHaveCode: () -> Unit,
    onRegisterAcademy: () -> Unit,
    onLogin: () -> Unit
) {
    SportProScreen {
        SportProLogo()
        Spacer(Modifier.height(24.dp))

        // Ilustración con el partido en vivo
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(SuperficieSuave)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Tarjeta, radius = size.width * 0.36f, center = Offset(size.width / 2, size.height), style = Stroke(width = 8f))
                drawLine(Tarjeta, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), strokeWidth = 8f)
                drawCircle(Coral, radius = 90f, center = Offset(size.width * 0.78f, size.height * 0.26f))
                drawCircle(Turquesa, radius = 38f, center = Offset(size.width * 0.22f, size.height * 0.40f))
                drawCircle(Turquesa.copy(alpha = 0.5f), radius = 22f, center = Offset(size.width * 0.35f, size.height * 0.21f))
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Tarjeta)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialsBadge("LH", size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Halcones 2 – 1 Águilas", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("Sub-12 A · minuto 67'", color = TextoSecundario, fontSize = 13.sp)
                }
                SmallChip("En vivo")
            }
        }

        Spacer(Modifier.height(28.dp))
        Title("Toda tu academia, cerca de tu familia")
        Spacer(Modifier.height(8.dp))
        Subtitle("Convocatorias, entrenamientos y partidos en vivo para entrenadores, jugadores y padres.")

        Spacer(Modifier.height(28.dp))
        PrimaryButton("Tengo un código de invitación", onHaveCode)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Registrar mi academia", onRegisterAcademy)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text("¿Ya tienes cuenta? ", color = TextoSecundario, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Inicia sesión",
                color = Turquesa,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onLogin)
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable
fun WelcomeScreenPreview() {
    SportProTheme { WelcomeScreen({}, {}, {}) }
}
