package pe.edu.esan.sportpro.presentation.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.EMPTY_CATALOG_MESSAGE
import pe.edu.esan.sportpro.data.model.EventType
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.EventTypeBadge
import pe.edu.esan.sportpro.presentation.components.InfoBox
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.Messages
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SecondaryButton
import pe.edu.esan.sportpro.presentation.components.SectionLabel
import pe.edu.esan.sportpro.presentation.components.StatusChip
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.presentation.components.eventIconLabel
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa
import pe.edu.esan.sportpro.ui.theme.TurquesaOscuro

// US-007 · Catálogo de eventos: el ADM lo edita; el DT lo ve en solo lectura
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onBack: () -> Unit,
    onNewType: () -> Unit,
    onEditType: (EventType) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Catálogo de eventos")
        Spacer(Modifier.height(8.dp))
        Subtitle(
            if (state.canEdit) "Estos son los eventos que tu equipo registra en cada partido. Los cambios se ven al instante."
            else "Estos son los eventos que se registran en los partidos. Los administra la academia."
        )
        Spacer(Modifier.height(16.dp))

        state.loadError?.let {
            ErrorBox(it)
            Spacer(Modifier.height(12.dp))
        }
        Messages(state.errorMessage, state.infoMessage)

        when {
            !state.loaded || state.creatingDefaults -> if (state.loadError == null) LoadingBox()
            state.types.isEmpty() -> {
                Spacer(Modifier.height(12.dp))
                if (state.canEdit) {
                    // No se pudo crear el catálogo de fábrica (por ejemplo, sin internet)
                    state.defaultsError?.let {
                        ErrorBox(it)
                        Spacer(Modifier.height(12.dp))
                    }
                    PrimaryButton("Crear el catálogo de fábrica", onClick = viewModel::ensureDefaults, loading = state.creatingDefaults)
                } else {
                    InfoBox {
                        Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = null, tint = Turquesa)
                        Text(EMPTY_CATALOG_MESSAGE, color = TextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            else -> {
                if (state.canEdit) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("+ Nuevo tipo de evento", onNewType)
                }
                Spacer(Modifier.height(20.dp))
                SectionLabel("Activos")
                Spacer(Modifier.height(10.dp))
                state.activeTypes.forEach { type ->
                    EventTypeRow(type, onClick = if (state.canEdit) ({ onEditType(type) }) else null)
                    Spacer(Modifier.height(10.dp))
                }
                if (state.inactiveTypes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("Desactivados")
                    Spacer(Modifier.height(10.dp))
                    state.inactiveTypes.forEach { type ->
                        EventTypeRow(type, onClick = if (state.canEdit) ({ onEditType(type) }) else null)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Volver", onBack)
    }
}

// Una fila del catálogo: ícono y color, nombre y etiquetas de sus propiedades
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventTypeRow(type: EventType, onClick: (() -> Unit)?) {
    val base = Modifier
        .fillMaxWidth()
        .alpha(if (type.active) 1f else 0.6f)
        .clip(RoundedCornerShape(22.dp))
        .background(Tarjeta)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) base.clickable(onClickLabel = "Editar tipo de evento", role = Role.Button, onClick = onClick) else base)
            .padding(14.dp)
    ) {
        EventTypeBadge(type.icon, type.color, contentDescription = eventIconLabel(type.icon))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(type.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextoPrincipal)
            val tags = buildList {
                if (type.affectsScore) add("Marcador")
                if (type.requiresPlayer) add("Jugador")
                if (type.requiresTeam) add("Equipo")
                if (type.allowsNote) add("Observación")
            }
            if (tags.isEmpty()) {
                Text(if (type.alwaysActive) "Siempre activo" else "Sin propiedades", color = TextoSecundario, fontSize = 13.sp)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { StatusChip(it, SuperficieSuave, TurquesaOscuro) }
                }
            }
        }
    }
}
