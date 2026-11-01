package pe.edu.esan.sportpro.presentation.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.CallupResponse
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.ui.theme.CoralSuave
import pe.edu.esan.sportpro.ui.theme.CoralTexto
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-006 · Piezas comunes de la convocatoria (DT, ADM, apoderado y jugador)

// Chip de la respuesta: Confirmado (turquesa), No puede (coral suave), Sin confirmar (turquesa suave)
@Composable
fun ResponseChip(response: String) {
    val (background, text) = responseColors(response)
    StatusChip(CallupResponse.label(response), background, text)
}

private fun responseColors(response: String): Pair<Color, Color> = when (response) {
    CallupResponse.CONFIRMED -> Turquesa to Tarjeta
    CallupResponse.DECLINED -> CoralSuave to CoralTexto
    else -> SuperficieSuave to TurquesaOscuro
}

// Resumen en tiempo real: Confirmado X · No puede Y · Sin confirmar Z
@Composable
fun CallupCounters(confirmed: Int, declined: Int, pending: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Counter("Confirmado", confirmed, Turquesa, Tarjeta, Modifier.weight(1f))
        Counter("No puede", declined, CoralSuave, CoralTexto, Modifier.weight(1f))
        Counter("Sin confirmar", pending, SuperficieSuave, TurquesaOscuro, Modifier.weight(1f))
    }
}

@Composable
private fun Counter(label: String, value: Int, background: Color, text: Color, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            // TalkBack lee "Confirmado: 5"
            .clearAndSetSemantics { contentDescription = "$label: $value" }
    ) {
        Text(value.toString(), color = text, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Text(label, color = if (background == SuperficieSuave) TextoSecundario else text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
