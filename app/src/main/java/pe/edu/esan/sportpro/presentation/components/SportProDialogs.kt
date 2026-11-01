package pe.edu.esan.sportpro.presentation.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Diálogos con el estilo de SportPro (los usan la US-002 y la US-003)

// Diálogo base: tarjeta blanca muy redondeada
@Composable
fun SportProDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = Turquesa,
    dismissText: String = "Cancelar",
    dismissOnOutsideTap: Boolean = true,   // false = solo se cierra con sus botones
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (dismissOnOutsideTap) onDismiss() },
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = TextoPrincipal, fontWeight = FontWeight.Black, fontSize = 20.sp) },
        text = content,
        confirmButton = { TextLink(confirmText, color = confirmColor, onClick = onConfirm) },
        dismissButton = { TextLink(dismissText, color = TextoSecundario, onClick = onDismiss) }
    )
}

// Confirmación simple (anular un código, desactivar un equipo, retirar a un jugador)
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    SportProDialog(title = title, confirmText = confirmText, confirmColor = ErrorTexto, onConfirm = onConfirm, onDismiss = onDismiss) {
        Text(text, color = TextoPrincipal, style = MaterialTheme.typography.bodyLarge)
    }
}
