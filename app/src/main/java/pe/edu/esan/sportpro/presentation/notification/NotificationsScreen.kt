package pe.edu.esan.sportpro.presentation.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.edu.esan.sportpro.data.model.AppNotification
import pe.edu.esan.sportpro.data.model.NotificationType
import pe.edu.esan.sportpro.data.model.toMatchTimeText
import pe.edu.esan.sportpro.data.model.toShortDateText
import pe.edu.esan.sportpro.presentation.components.ErrorBox
import pe.edu.esan.sportpro.presentation.components.Illustration
import pe.edu.esan.sportpro.presentation.components.LoadingBox
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps
import pe.edu.esan.sportpro.ui.theme.Coral
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Base de la US-012 · Notificaciones: lista en tiempo real, más nuevas primero, no leídas resaltadas.
// Al tocar una se marca como leída y se abre la pantalla relacionada.
@Composable
fun NotificationsScreen(
    viewModel: NotificationViewModel,
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Title("Notificaciones")
        Spacer(Modifier.height(8.dp))
        Subtitle(if (state.unreadCount > 0) "Tienes ${state.unreadCount} sin leer." else "¡Estás al día!")
        Spacer(Modifier.height(16.dp))

        state.loadError?.let {
            ErrorBox(it)
            Spacer(Modifier.height(12.dp))
        }
        state.errorMessage?.let {
            ErrorBox(it)
            Spacer(Modifier.height(12.dp))
        }
        if (!state.loaded) {
            if (state.loadError == null) LoadingBox()
            return@SportProScreen
        }
        if (state.notifications.isEmpty()) {
            Illustration(icon = Icons.Outlined.Notifications, height = 160.dp)
            Spacer(Modifier.height(16.dp))
            Subtitle("Aún no tienes notificaciones. Aquí verás convocatorias y cambios de tus partidos.")
            return@SportProScreen
        }
        state.sorted.forEach { notification ->
            NotificationRow(notification) {
                viewModel.markRead(notification)
                onOpen(notification)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    val unread = !notification.read
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (unread) SuperficieSuave else Tarjeta)
            .clickable(onClickLabel = "Abrir notificación", role = Role.Button, onClick = onClick)
            .padding(16.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (unread) Turquesa else SuperficieSuave),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(notification.type), contentDescription = null, tint = if (unread) Tarjeta else Turquesa)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                notification.title,
                fontWeight = if (unread) FontWeight.Black else FontWeight.Bold,
                fontSize = 16.sp,
                color = TextoPrincipal
            )
            Text(notification.body, color = TextoSecundario, fontSize = 14.sp)
            notification.createdAt?.let {
                Text("${it.toShortDateText()} · ${it.toMatchTimeText()}", color = TextoSecundario, fontSize = 12.sp)
            }
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            // Punto de "sin leer" (el texto en negrita y el fondo también lo indican)
            Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(Coral))
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    NotificationType.CALLUP -> Icons.Outlined.ConfirmationNumber
    NotificationType.CALLUP_RESPONSE -> Icons.Outlined.MarkChatRead
    NotificationType.MATCH_RESCHEDULED -> Icons.Outlined.EventRepeat
    NotificationType.MATCH_CANCELLED -> Icons.Outlined.Cancel
    else -> Icons.Outlined.Notifications
}
