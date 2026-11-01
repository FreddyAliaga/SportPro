package pe.edu.esan.sportpro.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.ui.theme.Borde
import pe.edu.esan.sportpro.ui.theme.InterruptorApagado
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Interruptor con título y explicación (toda la fila se puede tocar; área táctil ≥ 44 dp)
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            if (description != null) Text(description, fontSize = 13.sp, color = TextoSecundario)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,   // lo maneja la fila completa
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Tarjeta,
                checkedTrackColor = Turquesa,
                uncheckedThumbColor = Tarjeta,
                uncheckedTrackColor = InterruptorApagado,
                uncheckedBorderColor = InterruptorApagado,
                disabledCheckedTrackColor = Turquesa,
                disabledCheckedThumbColor = Tarjeta,
                disabledUncheckedTrackColor = InterruptorApagado,
                disabledUncheckedThumbColor = Tarjeta,
                disabledUncheckedBorderColor = InterruptorApagado
            )
        )
    }
}

// Casilla con texto (toda la fila se puede tocar; área táctil ≥ 44 dp)
@Composable
fun CheckRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,   // lo maneja la fila completa
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Turquesa, uncheckedColor = Borde, checkmarkColor = Tarjeta)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = TextoPrincipal)
            if (detail != null) Text(detail, fontSize = 13.sp, color = TextoSecundario)
        }
    }
}
