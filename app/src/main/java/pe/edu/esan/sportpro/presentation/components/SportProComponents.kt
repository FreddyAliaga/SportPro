package pe.edu.esan.sportpro.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.ui.theme.Borde
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.Coral
import pe.edu.esan.sportpro.ui.theme.CoralSuave
import pe.edu.esan.sportpro.ui.theme.ErrorBorde
import pe.edu.esan.sportpro.ui.theme.ErrorTexto
import pe.edu.esan.sportpro.ui.theme.Fondo
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Contenedor común de las pantallas: fondo turquesa claro, márgenes y scroll
@Composable
fun SportProScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        content = content
    )
}

// Botón "volver" circular blanco + barras de progreso del registro
@Composable
fun TopBarWithSteps(onBack: () -> Unit, step: Int = 0, totalSteps: Int = 3) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Tarjeta)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextoPrincipal)
        }
        if (step > 0) {
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(totalSteps) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (index < step) Turquesa else Borde)
                    )
                }
            }
        }
    }
}

@Composable
fun SportProLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Turquesa),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.SportsSoccer, contentDescription = null, tint = Tarjeta, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("SportPro", fontWeight = FontWeight.Black, fontSize = 22.sp, color = TextoPrincipal)
    }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineLarge, color = TextoPrincipal, modifier = modifier)
}

@Composable
fun Subtitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = TextoSecundario, modifier = modifier)
}

// Botón principal tipo píldora (58 dp)
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = Turquesa,
            contentColor = Tarjeta,
            disabledContainerColor = Borde,
            disabledContentColor = TextoSecundario
        ),
        modifier = Modifier.fillMaxWidth().height(58.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Tarjeta, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(2.dp, Borde),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Tarjeta, contentColor = TextoPrincipal),
        modifier = Modifier.fillMaxWidth().height(58.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// Tarjeta blanca redondeada
@Composable
fun SportProCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Tarjeta)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

// Campo de texto con el estilo de SportPro
@Composable
fun SportProTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextoPrincipal)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, color = TextoSecundario) } },
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Turquesa,
                unfocusedBorderColor = BordeSuave,
                errorBorderColor = ErrorBorde,
                focusedContainerColor = Fondo,
                unfocusedContainerColor = Fondo,
                errorContainerColor = Fondo,
                cursorColor = Turquesa
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = ErrorTexto, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// Recuadro de error coral
@Composable
fun ErrorBox(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CoralSuave)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = ErrorTexto, modifier = Modifier.size(20.dp))
        Text(message, color = ErrorTexto, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

// Recuadro informativo turquesa suave
@Composable
fun InfoBox(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SuperficieSuave)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// Ilustración: bloque turquesa suave con arco de cancha, círculo coral y un ícono al centro
@Composable
fun Illustration(icon: ImageVector, height: Dp = 210.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(SuperficieSuave),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = Tarjeta, radius = size.width * 0.33f, center = Offset(size.width / 2, size.height + 10f), style = Stroke(width = 8f))
            drawCircle(color = Coral, radius = 64f, center = Offset(size.width * 0.82f, size.height * 0.24f))
            drawCircle(color = Turquesa.copy(alpha = 0.5f), radius = 34f, center = Offset(size.width * 0.17f, size.height * 0.74f))
        }
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Turquesa),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Tarjeta, modifier = Modifier.size(52.dp))
        }
    }
}

// Chip pequeño (por ejemplo "En vivo")
@Composable
fun SmallChip(text: String) {
    Text(
        text,
        color = pe.edu.esan.sportpro.ui.theme.CoralTexto,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CoralSuave)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// Avatar cuadrado redondeado con iniciales
@Composable
fun InitialsBadge(initials: String, size: Dp = 48.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Turquesa)
            .border(0.dp, Turquesa, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Tarjeta, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

// Chip de estado (ej. "Pendiente", "Usado", "Activa")
@Composable
fun StatusChip(text: String, background: androidx.compose.ui.graphics.Color, textColor: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = textColor,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

// Píldora seleccionable para pestañas y filtros (área táctil de 44 dp)
@Composable
fun SelectablePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) Turquesa else Tarjeta)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Tarjeta else TextoSobreSuave,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp
        )
    }
}

// Enlace de texto turquesa (ej. "Anular", "Cambiar DT") con área táctil de 44 dp
@Composable
fun TextLink(text: String, color: androidx.compose.ui.graphics.Color = Turquesa, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

// Título de sección en mayúsculas (ej. "SUB-12")
@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextoSobreSuave)
}
