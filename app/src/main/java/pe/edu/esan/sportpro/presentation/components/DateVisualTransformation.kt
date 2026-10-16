package pe.edu.esan.sportpro.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Muestra la fecha como DD/MM/AAAA mientras se escribe.
// En el estado solo se guardan los dígitos (ej. "14031988"); las barras son solo visuales.
// La barra aparece apenas se completa el día ("14/") y el mes ("14/03/").
class DateVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val formatted = buildString {
            digits.forEachIndexed { index, c ->
                append(c)
                if (index == 1 || index == 3) append('/')
            }
        }

        // Le dice al cursor dónde está cada dígito considerando las barras
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val transformed = when {
                    offset < 2 -> offset
                    offset < 4 -> offset + 1
                    else -> offset + 2
                }
                return transformed.coerceIn(0, formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val original = when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    else -> offset - 2
                }
                return original.coerceIn(0, digits.length)
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
