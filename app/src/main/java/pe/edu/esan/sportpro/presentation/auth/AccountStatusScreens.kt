package pe.edu.esan.sportpro.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.presentation.components.AvisoProteccionDatos
import pe.edu.esan.sportpro.presentation.components.Illustration
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.ui.theme.SportProTheme
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// RN-003 · Menor de 18 años: no se crea la cuenta; su apoderado gestiona su perfil
@Composable
fun MinorBlockedScreen(onUnderstood: () -> Unit, onBackToStart: () -> Unit) {
    SportProScreen {
        Spacer(Modifier.height(16.dp))
        Illustration(icon = Icons.Outlined.FamilyRestroom)
        Spacer(Modifier.height(28.dp))
        Text(
            "Eres menor de edad: tu apoderado gestiona tu perfil",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        AvisoProteccionDatos(PrivacyTexts.TITLE_PERSONAL_DATA, PrivacyTexts.MINOR_BLOCKED)
        Spacer(Modifier.height(16.dp))
        SportProCard {
            Text("¿Qué puedes hacer?", fontWeight = FontWeight.ExtraBold)
            Text(
                "Pídele a tu apoderado que use el código que le envió tu entrenador. Desde su cuenta verá tus convocatorias, entrenamientos y partidos.",
                color = TextoSobreSuave,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Entendido", onUnderstood)
        Spacer(Modifier.height(14.dp))
        Text(
            "Volver al inicio",
            color = Turquesa,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)   // área táctil mínima
                .clickable(onClick = onBackToStart)
                .wrapContentHeight(Alignment.CenterVertically)
        )
    }
}

@Preview(showBackground = true, heightDp = 844, widthDp = 390)
@Composable
fun MinorBlockedPreview() {
    SportProTheme { MinorBlockedScreen({}, {}) }
}
