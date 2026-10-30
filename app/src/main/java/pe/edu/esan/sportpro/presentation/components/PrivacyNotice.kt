package pe.edu.esan.sportpro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.ui.theme.SportProTheme
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Aviso de protección de datos personales (Ley N.° 29733): tarjeta blanca con borde turquesa,
// franja izquierda más gruesa, candado y la etiqueta de la ley. Los textos están en PrivacyTexts.
// compact: versión más pequeña para formularios (crear cuenta, nuevo jugador, invitar…)
@Composable
fun AvisoProteccionDatos(title: String, text: String, compact: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)   // la franja izquierda toma el alto del contenido
            .clip(shape)
            .background(Tarjeta)
            .border(1.5.dp, Turquesa, shape)
    ) {
        // Borde izquierdo más grueso
        Box(Modifier.width(5.dp).fillMaxHeight().background(Turquesa))
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Turquesa),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = "Protección de datos", tint = Tarjeta, modifier = Modifier.size(16.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                Text(title, color = Turquesa, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                Text(
                    text,
                    color = TextoSobreSuave,
                    fontSize = if (compact) 11.sp else 12.sp,
                    lineHeight = if (compact) 15.sp else 17.sp
                )
                Text(
                    PrivacyTexts.LAW_LABEL,
                    color = Turquesa,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SuperficieSuave)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Etiqueta pequeña "🔒 Protegido" para títulos de secciones con datos privados
@Composable
fun EtiquetaProtegido(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Turquesa, modifier = Modifier.size(14.dp))
        Text("Protegido", color = Turquesa, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
fun AvisoProteccionDatosPreview() {
    SportProTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AvisoProteccionDatos(PrivacyTexts.TITLE_PERSONAL_DATA, PrivacyTexts.MINOR_BLOCKED)
            AvisoProteccionDatos(PrivacyTexts.TITLE_YOUR_DATA, PrivacyTexts.CREATE_ACCOUNT, compact = true)
        }
    }
}
