package pe.edu.esan.sportpro.presentation.academy

import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.AcademyForm
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LogoPicker
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Errores por campo del formulario de la academia
data class AcademyFormErrors(
    val name: String? = null,
    val city: String? = null,
    val institutionalEmail: String? = null,
    val callupNoticeHours: String? = null,
    val callupConfirmHours: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(name, city, institutionalEmail, callupNoticeHours, callupConfirmHours).any { it != null }
}

// Validación del formulario (la usan el registro y "Editar academia")
fun validateAcademyForm(form: AcademyForm): AcademyFormErrors {
    val notice = form.callupNoticeHours.toIntOrNull()
    val confirm = form.callupConfirmHours.toIntOrNull()
    return AcademyFormErrors(
        name = if (form.name.trim().length < 3) "Escribe el nombre de la academia." else null,
        city = if (form.city.isBlank()) "Escribe la ciudad." else null,
        institutionalEmail = if (!Patterns.EMAIL_ADDRESS.matcher(form.institutionalEmail.trim()).matches()) "Escribe un correo válido." else null,
        callupNoticeHours = if (notice == null || notice !in 1..168) "Entre 1 y 168 horas." else null,
        callupConfirmHours = when {
            confirm == null || confirm < 0 -> "Escribe las horas."
            notice != null && confirm >= notice -> "Debe ser menor que la anticipación."
            else -> null
        }
    )
}

// RN-018: mientras no esté verificada (campo verified, se cambia desde la consola),
// la academia no puede publicar avisos de pruebas. Aquí solo se muestra el aviso.
@Composable
fun VerificationNotice(verified: Boolean) {
    InfoBox {
        if (verified) {
            Icon(Icons.Outlined.Verified, contentDescription = null, tint = Turquesa, modifier = Modifier.size(22.dp))
            Text("Academia verificada por SportPro.", color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
            Icon(Icons.Outlined.HourglassTop, contentDescription = null, tint = Turquesa, modifier = Modifier.size(22.dp))
            Text(
                "Pendiente de verificación: podrás publicar avisos de pruebas cuando SportPro verifique tu academia.",
                color = TextoPrincipal,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

// US-002 · "Registra tu academia" (registro, step = 1) y "Editar academia" (con sesión, step = 0).
// verified = null en el registro (la academia aún no existe).
@Composable
fun AcademyFormScreen(
    title: String,
    subtitle: String,
    buttonText: String,
    form: AcademyForm,
    errors: AcademyFormErrors,
    verified: Boolean?,
    logoLoading: Boolean,
    loading: Boolean,
    errorMessage: String?,
    step: Int,
    onFormChange: (AcademyForm) -> Unit,
    onLogoPicked: (Uri) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    SportProScreen {
        TopBarWithSteps(onBack = onBack, step = step)
        Spacer(Modifier.height(28.dp))
        Title(title)
        Spacer(Modifier.height(8.dp))
        Subtitle(subtitle)
        Spacer(Modifier.height(20.dp))

        LogoPicker(
            logoBase64 = form.logoBase64,
            loading = logoLoading,
            onPicked = onLogoPicked,
            onRemove = { onFormChange(form.copy(logoBase64 = "")) }
        )
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = form.name,
                onValueChange = { onFormChange(form.copy(name = it.take(80))) },   // las reglas aceptan hasta 80
                label = "Nombre de la academia",
                placeholder = "Academia Los Halcones",
                error = errors.name
            )
            SportProTextField(
                value = form.city,
                onValueChange = { onFormChange(form.copy(city = it.take(60))) },
                label = "Ciudad",
                placeholder = "Lima",
                error = errors.city
            )
            SportProTextField(
                value = form.institutionalEmail,
                onValueChange = { onFormChange(form.copy(institutionalEmail = it.take(100))) },
                label = "Correo institucional",
                placeholder = "contacto@miacademia.pe",
                error = errors.institutionalEmail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("PLAZOS DE CONVOCATORIA", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextoSobreSuave)
        Spacer(Modifier.height(10.dp))
        SportProCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportProTextField(
                    value = form.callupNoticeHours,
                    onValueChange = { onFormChange(form.copy(callupNoticeHours = it.filter(Char::isDigit).take(3))) },
                    label = "Publicar con (h)",
                    error = errors.callupNoticeHours,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                SportProTextField(
                    value = form.callupConfirmHours,
                    onValueChange = { onFormChange(form.copy(callupConfirmHours = it.filter(Char::isDigit).take(3))) },
                    label = "Confirmar hasta (h)",
                    error = errors.callupConfirmHours,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "La convocatoria se publica con esa anticipación y se confirma hasta esas horas antes del partido. " +
                    "Por defecto: 24 h y 2 h.",
                color = TextoSobreSuave,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))
        VerificationNotice(verified == true)

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            ErrorBox(it)
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = buttonText, onClick = onSubmit, loading = loading, enabled = !logoLoading)
    }
}
