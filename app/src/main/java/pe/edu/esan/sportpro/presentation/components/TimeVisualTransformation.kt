package pe.edu.esan.sportpro.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Muestra la hora como HH:MM mientras se escribe (igual que DateVisualTransformation con la fecha).
// En el estado solo se guardan los dígitos (ej. "1630"); los dos puntos son solo visuales.
class TimeVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(4)
        val formatted = buildString {
            digits.forEachIndexed { index, c ->
                append(c)
                if (index == 1) append(':')
            }
        }

        // Le dice al cursor dónde está cada dígito considerando los dos puntos
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                (if (offset < 2) offset else offset + 1).coerceIn(0, formatted.length)

            override fun transformedToOriginal(offset: Int): Int =
                (if (offset <= 2) offset else offset - 1).coerceIn(0, digits.length)
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
