package pe.edu.esan.sportpro.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.presentation.components.Illustration
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.ui.theme.BordeSuave
import pe.edu.esan.sportpro.ui.theme.Fondo
import pe.edu.esan.sportpro.ui.theme.SuperficieSuave
import pe.edu.esan.sportpro.ui.theme.Tarjeta
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal
import pe.edu.esan.sportpro.ui.theme.TextoSecundario
import pe.edu.esan.sportpro.ui.theme.TextoSobreSuave
import pe.edu.esan.sportpro.ui.theme.Turquesa

// Una sección del menú: se desarrolla en la historia indicada
data class HomeSection(val id: String, val title: String, val story: String, val icon: ImageVector)

// Secciones del menú lateral según el rol (solo las permitidas para cada uno)
fun sectionsFor(role: String): List<HomeSection> = when (role) {
    "ADM" -> listOf(
        HomeSection("academia", "Academia y equipos", "US-002", Icons.Outlined.Groups),
        HomeSection("invitaciones", "Usuarios e invitaciones", "US-002", Icons.Outlined.ConfirmationNumber),
        HomeSection("catalogo", "Catálogo de eventos", "US-007", Icons.AutoMirrored.Outlined.ListAlt),
        HomeSection("partidos", "Partidos", "US-006", Icons.Outlined.SportsSoccer),   // solo lectura
        HomeSection("mensualidades", "Mensualidades", "US-004", Icons.Outlined.Payments),
        HomeSection("avisos", "Avisos de pruebas", "US-014", Icons.Outlined.Campaign),
        HomeSection("moderacion", "Moderación", "US-013", Icons.Outlined.Flag),
        HomeSection("notificaciones", "Notificaciones", "US-012", Icons.Outlined.Notifications)
    )
    "DT" -> listOf(
        HomeSection("plantel", "Plantel", "US-003", Icons.Outlined.Groups),
        HomeSection("entrenamientos", "Entrenamientos", "US-005", Icons.AutoMirrored.Outlined.EventNote),
        HomeSection("partidos", "Partidos", "US-006", Icons.Outlined.SportsSoccer),
        HomeSection("estadisticas", "Estadísticas", "US-011", Icons.Outlined.BarChart),
        HomeSection("anuncios", "Anuncios", "US-012", Icons.Outlined.Campaign),
        HomeSection("comunidad", "Comunidad", "US-013", Icons.Outlined.Forum),
        HomeSection("notificaciones", "Notificaciones", "US-012", Icons.Outlined.Notifications)
    )
    "PAD" -> listOf(
        HomeSection("jugadores", "Mis jugadores", "US-003", Icons.Outlined.Groups),
        HomeSection("convocatorias", "Convocatorias", "US-006", Icons.Outlined.ConfirmationNumber),
        HomeSection("entrenamientos", "Entrenamientos", "US-005", Icons.AutoMirrored.Outlined.EventNote),
        HomeSection("partidos", "Partidos", "US-010", Icons.Outlined.SportsSoccer),
        HomeSection("mensualidades", "Mensualidades", "US-004", Icons.Outlined.Payments),
        HomeSection("comunidad", "Comunidad", "US-013", Icons.Outlined.Forum),
        HomeSection("notificaciones", "Notificaciones", "US-012", Icons.Outlined.Notifications)
    )
    "JUG" -> listOf( // jugador mayor de edad
        HomeSection("convocatorias", "Convocatorias", "US-006", Icons.Outlined.ConfirmationNumber),
        HomeSection("entrenamientos", "Entrenamientos", "US-005", Icons.AutoMirrored.Outlined.EventNote),
        HomeSection("partidos", "Partidos", "US-010", Icons.Outlined.SportsSoccer),
        HomeSection("estadisticas", "Mis estadísticas", "US-011", Icons.Outlined.BarChart),
        HomeSection("mensualidades", "Mis mensualidades", "US-004", Icons.Outlined.Payments),
        HomeSection("comunidad", "Comunidad", "US-013", Icons.Outlined.Forum),
        HomeSection("notificaciones", "Notificaciones", "US-012", Icons.Outlined.Notifications)
    )
    else -> emptyList() // rol desconocido o perfil aún sin cargar: no se muestra ninguna sección
}

// US-001 · Inicio según el rol, con menú lateral (Navigation Drawer)
@Composable
fun HomeScreen(
    profile: UserProfile?,
    onOpenSection: (HomeSection) -> Unit,
    onMyProfile: () -> Unit,
    onSignOut: () -> Unit,
    unreadNotifications: Int = 0      // insignia del acceso "Notificaciones"
) {
    // Número que se muestra sobre cada sección (por ahora solo las notificaciones sin leer)
    val badgeOf: (HomeSection) -> Int = { if (it.id == "notificaciones") unreadNotifications else 0 }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()   // solo para abrir y cerrar el menú (animación de la interfaz)
    val sections = sectionsFor(profile?.role.orEmpty())

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Tarjeta,
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.width(306.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Avatar(profile?.initials.orEmpty(), size = 52)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(profile?.name.orEmpty(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextoPrincipal)
                            RoleChip(profile?.roleName.orEmpty())
                        }
                    }
                    Text(
                        profile?.academyName.orEmpty(),
                        color = TextoSecundario,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 10.dp)
                    )
                    HorizontalDivider(color = BordeSuave, modifier = Modifier.padding(vertical = 12.dp))

                    DrawerItem("Inicio", Icons.Outlined.Home, selected = true) { scope.launch { drawerState.close() } }
                    sections.forEach { section ->
                        DrawerItem(section.title, section.icon, badge = badgeOf(section)) {
                            scope.launch { drawerState.close() }
                            onOpenSection(section)
                        }
                    }

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(color = BordeSuave, modifier = Modifier.padding(vertical = 8.dp))
                    DrawerItem("Mi perfil", Icons.Outlined.Person) {
                        scope.launch { drawerState.close() }
                        onMyProfile()
                    }
                    DrawerItem("Cerrar sesión", Icons.AutoMirrored.Outlined.Logout, onClick = onSignOut)
                }
            }
        }
    ) {
        SportProScreen {
            // Barra superior: menú, logo y avatar
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Tarjeta)
                ) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Abrir menú", tint = TextoPrincipal)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Turquesa),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.SportsSoccer, contentDescription = null, tint = Tarjeta, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("SportPro", fontWeight = FontWeight.Black, fontSize = 19.sp, color = TextoPrincipal)
                Spacer(Modifier.weight(1f))
                Avatar(profile?.initials.orEmpty(), size = 44)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                if (profile == null) "Hola" else "Hola, ${profile.firstName}",
                style = MaterialTheme.typography.headlineLarge,
                color = TextoPrincipal
            )
            Spacer(Modifier.height(8.dp))
            if (profile != null) RoleChip("${profile.roleName} · ${profile.academyName}")

            Spacer(Modifier.height(20.dp))
            if (profile == null) {
                // Todavía no llega el perfil (por ejemplo, sin internet): indicador de carga
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Turquesa)
                }
            } else {
                Text("ACCESOS RÁPIDOS", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextoSobreSuave)
                Spacer(Modifier.height(10.dp))

                // Accesos rápidos en 2 columnas (las mismas secciones del menú)
                sections.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { section ->
                            QuickTile(section, Modifier.weight(1f), badge = badgeOf(section)) { onOpenSection(section) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean = false, badge: Int = 0, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, fontSize = 16.sp) },
        icon = { Icon(icon, contentDescription = null) },
        badge = if (badge > 0) ({ CountBadge(badge) }) else null,
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = SuperficieSuave,
            selectedTextColor = Turquesa,
            selectedIconColor = Turquesa,
            unselectedContainerColor = Tarjeta,
            unselectedTextColor = TextoSobreSuave,
            unselectedIconColor = TextoSobreSuave
        ),
        modifier = Modifier.height(48.dp)
    )
}

@Composable
private fun QuickTile(section: HomeSection, modifier: Modifier, badge: Int = 0, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Tarjeta)
            .clickable(onClick = onClick)
            .padding(14.dp)
            // TalkBack: "Notificaciones, 3 sin leer"
            .semantics(mergeDescendants = true) {
                if (badge > 0) contentDescription = "${section.title}, $badge sin leer"
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(SuperficieSuave),
                contentAlignment = Alignment.Center
            ) {
                Icon(section.icon, contentDescription = null, tint = Turquesa, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            if (badge > 0) CountBadge(badge)
        }
        Text(section.title, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = TextoPrincipal)
    }
}

// Insignia con un número (ej. notificaciones sin leer). Más de 99 se muestra "99+".
@Composable
private fun CountBadge(count: Int) {
    Text(
        if (count > 99) "99+" else count.toString(),
        color = Tarjeta,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Turquesa)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
private fun Avatar(initials: String, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Turquesa),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, color = Tarjeta, fontWeight = FontWeight.Black, fontSize = (size / 3).sp)
    }
}

@Composable
private fun RoleChip(text: String) {
    Text(
        text,
        color = TextoSobreSuave,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SuperficieSuave)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

// Pantalla genérica para las secciones que pertenecen a otras historias
@Composable
fun ComingSoonScreen(title: String, story: String, onBack: () -> Unit) {
    SportProScreen {
        Spacer(Modifier.height(16.dp))
        Illustration(icon = Icons.Outlined.Construction)
        Spacer(Modifier.height(28.dp))
        Title(title)
        Spacer(Modifier.height(8.dp))
        Subtitle("Esta parte se desarrolla en la $story.")
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Volver", onBack)
    }
}

// Fondo usado por el NavGraph mientras se revisa si hay una sesión abierta
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Fondo), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(Turquesa),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.SportsSoccer, contentDescription = null, tint = Tarjeta, modifier = Modifier.size(40.dp))
        }
    }
}
