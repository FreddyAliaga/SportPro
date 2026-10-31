package pe.edu.esan.sportpro.presentation.components

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.edu.esan.sportpro.data.model.academyInitials
import pe.edu.esan.sportpro.ui.theme.Borde
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Tamaño máximo de una foto guardada en Firestore (plan Spark: no hay Cloud Storage)
const val MAX_PHOTO_BYTES = 150 * 1024

// Lado máximo de la imagen antes de comprimir (un logo no necesita más)
private const val MAX_PHOTO_SIDE = 512

// Selector de logo: abre el selector de fotos de Android (no pide permisos) y muestra el logo elegido.
// La compresión la hace el ViewModel con compressImageToBase64.
// US-003: también se usa para la foto del jugador (title / removeText).
@Composable
fun LogoPicker(
    logoBase64: String,
    loading: Boolean,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    title: String = "Logo de la academia",
    removeText: String = "Quitar logo"
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
    }
    val openPicker = {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(SuperficieSuave)
                .border(2.dp, Borde, RoundedCornerShape(30.dp))
                .clickable(enabled = !loading, onClick = openPicker),
            contentAlignment = Alignment.Center
        ) {
            val image = rememberBase64Image(logoBase64)
            when {
                loading -> CircularProgressIndicator(color = Turquesa, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                image != null -> Image(
                    bitmap = image,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp)
                )
                else -> Icon(Icons.Outlined.AddAPhoto, contentDescription = "Elegir imagen: $title", tint = Turquesa, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextoPrincipal)
        Text("Opcional · se comprime a 150 KB", fontSize = 13.sp, color = TextoSecundario)
        if (logoBase64.isNotEmpty() && !loading) {
            Text(
                removeText,
                color = Turquesa,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = 44.dp)   // área táctil mínima
                    .clickable(onClick = onRemove)
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}

// Logo de la academia (o sus iniciales si no tiene) en un cuadrado redondeado
@Composable
fun AcademyLogo(logoBase64: String, academyName: String, size: Dp = 52.dp) {
    val image = rememberBase64Image(logoBase64)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Logo de $academyName",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(16.dp))
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Turquesa),
            contentAlignment = Alignment.Center
        ) {
            Text(academyInitials(academyName), color = Tarjeta, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

// US-003: foto del jugador (o sus iniciales si no tiene) en un círculo
@Composable
fun PlayerAvatar(photoBase64: String, initials: String, size: Dp = 48.dp) {
    val image = rememberBase64Image(photoBase64)
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Foto del jugador",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(Turquesa),
            contentAlignment = Alignment.Center
        ) {
            Text(initials, color = Tarjeta, fontWeight = FontWeight.Black, fontSize = (size.value / 3).sp)
        }
    }
}

// Convierte el texto Base64 guardado en Firestore en una imagen (null si está vacío o dañado)
@Composable
private fun rememberBase64Image(base64: String): ImageBitmap? = remember(base64) {
    if (base64.isEmpty()) return@remember null
    try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

// Reduce la imagen y la comprime en JPEG hasta que pese MAX_PHOTO_BYTES o menos; devuelve el Base64.
// Se llama desde el ViewModel (corre fuera del hilo principal).
suspend fun compressImageToBase64(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.Default) {
    val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        val scale = minOf(1f, MAX_PHOTO_SIDE.toFloat() / maxOf(width, height))
        decoder.setTargetSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    // JPEG no tiene transparencia: se pinta sobre fondo blanco (si no, un logo PNG saldría con fondo negro)
    var bitmap = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).apply {
        drawColor(android.graphics.Color.WHITE)
        drawBitmap(decoded, 0f, 0f, null)
    }

    var quality = 90
    var bytes = toJpeg(bitmap, quality)
    while (bytes.size > MAX_PHOTO_BYTES) {
        if (quality > 40) {
            quality -= 10   // primero se baja la calidad...
        } else {
            // ...y si no alcanza, se achica la imagen y se vuelve a intentar
            if (bitmap.width < 64 || bitmap.height < 64) throw IllegalStateException("No se pudo comprimir la imagen.")
            bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width * 3 / 4, bitmap.height * 3 / 4, true)
            quality = 80
        }
        bytes = toJpeg(bitmap, quality)
    }
    Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
    return output.toByteArray()
}
