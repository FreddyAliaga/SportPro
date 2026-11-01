package pe.edu.esan.sportpro.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import pe.edu.esan.sportpro.presentation.academy.AcademyFormScreen
import pe.edu.esan.sportpro.presentation.academy.AcademyTeamsScreen
import pe.edu.esan.sportpro.presentation.academy.AcademyViewModel
import pe.edu.esan.sportpro.presentation.academy.InviteCoachScreen
import pe.edu.esan.sportpro.presentation.academy.TeamFormScreen
import pe.edu.esan.sportpro.presentation.academy.UsersInvitationsScreen
import pe.edu.esan.sportpro.presentation.auth.CreateAccountScreen
import pe.edu.esan.sportpro.presentation.auth.ForgotPasswordScreen
import pe.edu.esan.sportpro.presentation.auth.InvitationCodeScreen
import pe.edu.esan.sportpro.presentation.auth.LoginScreen
import pe.edu.esan.sportpro.presentation.auth.LoginViewModel
import pe.edu.esan.sportpro.presentation.auth.MinorBlockedScreen
import pe.edu.esan.sportpro.presentation.auth.RegisterViewModel
import pe.edu.esan.sportpro.presentation.auth.VerifyEmailScreen
import pe.edu.esan.sportpro.presentation.auth.WelcomeScreen
import pe.edu.esan.sportpro.presentation.callup.CallupViewModel
import pe.edu.esan.sportpro.presentation.callup.CallupsScreen
import pe.edu.esan.sportpro.presentation.notification.NotificationViewModel
import pe.edu.esan.sportpro.presentation.notification.NotificationsScreen
import pe.edu.esan.sportpro.presentation.match.LineupScreen
import pe.edu.esan.sportpro.presentation.match.MatchDetailScreen
import pe.edu.esan.sportpro.presentation.match.MatchFormScreen
import pe.edu.esan.sportpro.presentation.match.MatchListScreen
import pe.edu.esan.sportpro.presentation.match.MatchViewModel
import pe.edu.esan.sportpro.presentation.catalog.CatalogScreen
import pe.edu.esan.sportpro.presentation.catalog.CatalogViewModel
import pe.edu.esan.sportpro.presentation.catalog.EventTypeFormScreen
import pe.edu.esan.sportpro.presentation.home.ComingSoonScreen
import pe.edu.esan.sportpro.presentation.home.HomeScreen
import pe.edu.esan.sportpro.presentation.home.SplashScreen
import pe.edu.esan.sportpro.presentation.home.sectionsFor
import pe.edu.esan.sportpro.presentation.player.InvitePlayerScreen
import pe.edu.esan.sportpro.presentation.player.LinkPlayerScreen
import pe.edu.esan.sportpro.presentation.player.MyPlayersScreen
import pe.edu.esan.sportpro.presentation.player.PlayerDetailScreen
import pe.edu.esan.sportpro.presentation.player.PlayerFormScreen
import pe.edu.esan.sportpro.presentation.player.PlayerViewModel
import pe.edu.esan.sportpro.presentation.player.PrivateFormScreen
import pe.edu.esan.sportpro.presentation.player.RosterScreen
import pe.edu.esan.sportpro.presentation.components.SportProDialog
import pe.edu.esan.sportpro.ui.theme.TextoPrincipal

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    // US-001: un ViewModel para el registro y otro para el inicio de sesión y la sesión
    val registerViewModel: RegisterViewModel = viewModel()
    val loginViewModel: LoginViewModel = viewModel()
    // US-002: academia, equipos, usuarios e invitaciones del ADM
    val academyViewModel: AcademyViewModel = viewModel()
    // US-003: plantel y perfil del jugador (DT, ADM, PAD y JUG)
    val playerViewModel: PlayerViewModel = viewModel()
    // US-007: catálogo de eventos (el ADM lo edita; el DT lo ve)
    val catalogViewModel: CatalogViewModel = viewModel()
    // US-006: partidos y convocatoria (DT y ADM) / convocatorias (PAD y JUG)
    val matchViewModel: MatchViewModel = viewModel()
    val callupViewModel: CallupViewModel = viewModel()
    // Base de la US-012: bandeja de notificaciones dentro de la app (todos los roles)
    val notificationViewModel: NotificationViewModel = viewModel()

    val closeSession: () -> Unit = {
        notificationViewModel.stop()
        academyViewModel.stop()
        playerViewModel.stop()
        catalogViewModel.stop()
        matchViewModel.stop()
        callupViewModel.stop()
        loginViewModel.signOut()
        registerViewModel.signOutAndReset()
    }

    // Cierra la sesión y vuelve a la bienvenida sin dejar pantallas en el historial
    val signOut: () -> Unit = {
        closeSession()
        navController.goTo("welcome")
    }

    // US-002: si el ADM desactiva la cuenta con la sesión abierta, se cierra la sesión
    // y se explica el motivo en "Iniciar sesión" (atrás vuelve a la bienvenida)
    LaunchedEffect(Unit) {
        loginViewModel.uiState.collect { state ->
            state.sessionEndedMessage?.let { message ->
                closeSession()
                loginViewModel.showMessage(message)
                navController.goTo("welcome")
                navController.navigate("login")
            }
        }
    }

    // Tras un cierre de la app por Android no se pudo volver a cargar el perfil: se ofrece reintentar
    val profileError by remember {
        loginViewModel.uiState.map { it.profileError }.distinctUntilChanged()
    }.collectAsState(initial = null)
    profileError?.let { message ->
        SportProDialog(
            title = "No pudimos cargar tu cuenta",
            confirmText = "Reintentar",
            onConfirm = loginViewModel::ensureProfile,
            onDismiss = signOut,
            dismissText = "Cerrar sesión",
            dismissOnOutsideTap = false
        ) {
            Text(message, color = TextoPrincipal, style = MaterialTheme.typography.bodyLarge)
        }
    }

    NavHost(
        navController = navController,
        startDestination = "start"
    ) {
        // Al abrir la app: si la sesión sigue abierta, entra directo al inicio
        composable("start") {
            SplashScreen()
            LaunchedEffect(Unit) {
                loginViewModel.restoreSession(
                    onLoggedIn = { navController.goTo("home") },
                    onLoggedOut = { navController.goTo("welcome") }
                )
            }
        }
        composable("welcome") {
            WelcomeScreen(
                onHaveCode = { navController.navigate("invitation_code") },
                onRegisterAcademy = {
                    registerViewModel.startAcademyRegistration()
                    navController.navigate("register_academy")
                },
                onLogin = { navController.navigate("login") }
            )
        }

        // ---------- Registro con código de invitación ----------
        composable("invitation_code") {
            InvitationCodeScreen(
                viewModel = registerViewModel,
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate("create_account") }
            )
        }
        composable("create_account") {
            CreateAccountScreen(
                viewModel = registerViewModel,
                onBack = { navController.popBackStack() },
                onMinor = {
                    // Quien registra una academia ve el error en el formulario; los demás, la pantalla de menor de edad
                    if (registerViewModel.uiState.value.registeringAcademy) registerViewModel.showAdminMinorError()
                    else navController.navigate("minor_blocked")
                },
                onAccountCreated = { navController.goTo("verify_email") }
            )
        }
        composable("verify_email") {
            VerifyEmailScreen(
                viewModel = registerViewModel,
                onActive = {
                    loginViewModel.loadProfile()
                    navController.goTo("home")
                },
                onUseOtherAccount = signOut
            )
        }
        composable("minor_blocked") {
            MinorBlockedScreen(onUnderstood = signOut, onBackToStart = signOut)
        }

        // ---------- Inicio de sesión ----------
        composable("login") {
            LoginScreen(
                viewModel = loginViewModel,
                onBack = { navController.popBackStack() },
                onLoggedIn = { navController.goTo("home") },
                onNotVerified = { navController.navigate("verify_login") },
                onForgotPassword = { navController.navigate("forgot_password") },
                onUseCode = { navController.navigate("invitation_code") }
            )
        }
        composable("verify_login") {
            // Correo sin verificar: se niega el acceso y se ofrece reenviar el enlace
            VerifyEmailScreen(
                viewModel = registerViewModel,
                onActive = {
                    loginViewModel.loadProfile()
                    navController.goTo("home")
                },
                onUseOtherAccount = signOut,
                fromLogin = true
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = loginViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ---------- Inicio por rol ----------
        composable("home") {
            val state by loginViewModel.uiState.collectAsState()
            // US-007: al entrar el ADM al inicio, si su academia no tiene catálogo de eventos se crea el de fábrica
            LaunchedEffect(state.profile) {
                state.profile?.takeIf { it.role == "ADM" }?.let { catalogViewModel.start(it) }
                // Insignia de notificaciones sin leer (todos los roles)
                state.profile?.let { notificationViewModel.start(it) }
            }
            val unread = notificationViewModel.uiState.collectAsState().value.unreadCount
            HomeScreen(
                profile = state.profile,
                unreadNotifications = unread,
                onOpenSection = { section ->
                    when (section.id) {
                        "academia" -> navController.navigate("academy")              // US-002
                        "invitaciones" -> navController.navigate("academy_users")    // US-002
                        "plantel" -> navController.navigate("roster")                // US-003 (DT)
                        "jugadores" -> navController.navigate("my_players")          // US-003 (PAD)
                        "catalogo" -> navController.navigate("catalog")              // US-007 (ADM)
                        "convocatorias" -> navController.navigate("callups")         // US-006 (PAD y JUG)
                        "notificaciones" -> navController.navigate("notifications")  // base de la US-012 (todos)
                        // US-006: "Partidos" del DT y del ADM. El del PAD/JUG es de la US-010.
                        "partidos" ->
                            if (state.profile?.role in listOf("DT", "ADM")) navController.navigate("matches")
                            else navController.navigate("section/partidos")
                        else -> navController.navigate("section/${section.id}")
                    }
                },
                onMyProfile = {
                    val profile = state.profile
                    if (profile?.role == "JUG" && profile.playerId.isNotEmpty()) {
                        // US-003: el jugador mayor ve y edita su propio perfil de jugador
                        playerViewModel.start(profile)
                        playerViewModel.selectPlayer(profile.playerId)
                        navController.navigate("player_detail")
                    } else {
                        navController.navigate("section/perfil")
                    }
                },
                onSignOut = signOut
            )
        }
        // Secciones del menú que se desarrollan en otras historias
        composable("section/{id}") { backStackEntry ->
            val state by loginViewModel.uiState.collectAsState()
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            val section = sectionsFor(state.profile?.role.orEmpty()).firstOrNull { it.id == id }
            ComingSoonScreen(
                title = section?.title ?: "Mi perfil",
                story = section?.story ?: "US-003"
            ) { navController.popBackStack() }
        }

        // ---------- US-002 · Academia y equipos / Usuarios e invitaciones (ADM con sesión) ----------
        composable("academy") {
            StartAcademy(loginViewModel, academyViewModel)
            val back = backClearingMessages(navController, academyViewModel::clearMessages)
            AcademyTeamsScreen(
                viewModel = academyViewModel,
                onBack = back,
                onEditAcademy = {
                    academyViewModel.startEditAcademy()
                    navController.navigate("academy_edit")
                },
                onNewTeam = {
                    academyViewModel.startNewTeam()
                    navController.navigate("academy_team")
                },
                onEditTeam = { team ->
                    academyViewModel.startEditTeam(team)
                    navController.navigate("academy_team?teamId=${team.id}")
                },
                onOpenRoster = { team ->
                    // US-003: el ADM ve el plantel del equipo con las mismas acciones del DT.
                    // Primero se inicia el ViewModel (si no, start() borraría el equipo elegido).
                    loginViewModel.uiState.value.profile?.let { playerViewModel.start(it) }
                    playerViewModel.openTeam(team.id)
                    navController.navigate("roster")
                },
                onUsersInvitations = { navController.navigate("academy_users") }
            )
        }
        composable("academy_edit") {
            StartAcademy(loginViewModel, academyViewModel)
            val back = backClearingMessages(navController, academyViewModel::clearMessages)
            val state by academyViewModel.uiState.collectAsState()
            val resolver = LocalContext.current.contentResolver
            // Si Android cerró la app, el formulario se vuelve a llenar cuando llega la academia
            LaunchedEffect(state.academy != null) { academyViewModel.restoreAcademyForm() }
            AcademyFormScreen(
                title = "Editar academia",
                subtitle = "Los cambios se ven al instante en toda tu academia.",
                buttonText = "Guardar cambios",
                form = state.academyForm,
                errors = state.academyErrors,
                verified = state.academy?.verified,
                logoLoading = state.logoLoading,
                loading = state.isLoading,
                errorMessage = state.errorMessage,
                step = 0,
                onFormChange = academyViewModel::onAcademyFormChange,
                onLogoPicked = { uri -> academyViewModel.onLogoPicked(resolver, uri) },
                onBack = back,
                onSubmit = {
                    academyViewModel.saveAcademy {
                        loginViewModel.loadProfile()   // el menú muestra el nombre nuevo de la academia
                        navController.popBackStack()
                    }
                }
            )
        }
        // "Nuevo equipo" = academy_team; "Editar equipo" = academy_team?teamId=… (el id en la ruta
        // sirve para volver a cargar el equipo si Android cerró la app)
        composable(
            "academy_team?teamId={teamId}",
            arguments = listOf(navArgument("teamId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            StartAcademy(loginViewModel, academyViewModel)
            val back = backClearingMessages(navController, academyViewModel::clearMessages)
            val teamId = backStackEntry.arguments?.getString("teamId").orEmpty()
            val teamsLoaded = academyViewModel.uiState.collectAsState().value.teamsLoaded
            LaunchedEffect(teamId, teamsLoaded) {
                if (!academyViewModel.restoreTeamForm(teamId)) navController.popBackStack()
            }
            TeamFormScreen(
                viewModel = academyViewModel,
                onBack = back,
                onSaved = { navController.popBackStack() },
                onInvitationCreated = { navController.navigateToInvite(academyViewModel) }
            )
        }
        composable("academy_users") {
            StartAcademy(loginViewModel, academyViewModel)
            val back = backClearingMessages(navController, academyViewModel::clearMessages)
            UsersInvitationsScreen(
                viewModel = academyViewModel,
                onBack = back,
                onInvitationCreated = { navController.navigateToInvite(academyViewModel) }
            )
        }
        composable("academy_invite/{code}") { backStackEntry ->
            StartAcademy(loginViewModel, academyViewModel)
            val code = backStackEntry.arguments?.getString("code").orEmpty()
            val invitationsLoaded = academyViewModel.uiState.collectAsState().value.invitationsLoaded
            LaunchedEffect(code, invitationsLoaded) {
                if (!academyViewModel.restoreGeneratedCode(code)) navController.popBackStack()
            }
            InviteCoachScreen(viewModel = academyViewModel, onDone = { navController.popBackStack() })
        }

        // ---------- US-003 · Plantel y perfil del jugador (DT y ADM) ----------
        composable("roster") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            RosterScreen(
                viewModel = playerViewModel,
                onBack = back,
                onNewPlayer = {
                    playerViewModel.startNewPlayer()
                    navController.navigate("player_new")
                },
                onOpenPlayer = { player ->
                    playerViewModel.selectPlayer(player.id)
                    navController.navigate("player_detail")
                }
            )
        }
        composable("player_new") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            PlayerFormScreen(
                viewModel = playerViewModel,
                onBack = back,
                onSaved = { playerId ->
                    // Se abre el perfil del jugador nuevo (para invitar a su apoderado) en lugar del formulario
                    playerViewModel.selectPlayer(playerId)
                    navController.navigate("player_detail") { popUpTo("player_new") { inclusive = true } }
                }
            )
        }
        composable("player_detail") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            PlayerDetailScreen(
                viewModel = playerViewModel,
                onBack = back,
                onEditSport = {
                    playerViewModel.startEditSport()
                    navController.navigate("player_edit")
                },
                onInvitationCreated = { navController.navigate("player_invite") },
                onTransferred = { navController.popBackStack() },
                // Apoderado o jugador mayor (la pantalla solo muestra "Editar" a ellos)
                onEditPrivate = {
                    playerViewModel.startEditPrivate()
                    navController.navigate("player_private")
                }
            )
        }
        composable("player_private") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            val resolver = LocalContext.current.contentResolver
            PrivateFormScreen(
                viewModel = playerViewModel,
                onPhotoPicked = { uri -> playerViewModel.onPhotoPicked(resolver, uri) },
                onBack = back,
                onSaved = { navController.popBackStack() }
            )
        }

        // ---------- US-003 · Mis jugadores (PAD) ----------
        composable("my_players") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            MyPlayersScreen(
                viewModel = playerViewModel,
                onBack = back,
                onOpenPlayer = { player ->
                    playerViewModel.selectPlayer(player.id)
                    navController.navigate("player_detail")
                },
                onLinkPlayer = {
                    playerViewModel.startLink()
                    navController.navigate("link_player")
                }
            )
        }
        composable("link_player") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            LinkPlayerScreen(
                viewModel = playerViewModel,
                onBack = back,
                onLinked = { navController.popBackStack() }
            )
        }
        composable("player_edit") {
            StartPlayers(loginViewModel, playerViewModel)
            val back = backClearingMessages(navController, playerViewModel::clearMessages)
            PlayerFormScreen(
                viewModel = playerViewModel,
                onBack = back,
                onSaved = { navController.popBackStack() }
            )
        }
        composable("player_invite") {
            InvitePlayerScreen(viewModel = playerViewModel, onDone = { navController.popBackStack() })
        }

        // ---------- US-007 · Catálogo de eventos (ADM edita; DT solo lectura) ----------
        composable("catalog") {
            StartCatalog(loginViewModel, catalogViewModel)
            val back = backClearingMessages(navController, catalogViewModel::clearMessages)
            CatalogScreen(
                viewModel = catalogViewModel,
                onBack = back,
                onNewType = {
                    catalogViewModel.startNew()
                    navController.navigate("catalog_type")
                },
                onEditType = { type ->
                    catalogViewModel.startEdit(type)
                    navController.navigate("catalog_type?typeId=${type.id}")
                }
            )
        }
        // "Nuevo tipo" = catalog_type; "Editar tipo" = catalog_type?typeId=… (el id en la ruta sirve para
        // volver a cargarlo si Android cerró la app)
        composable(
            "catalog_type?typeId={typeId}",
            arguments = listOf(navArgument("typeId") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            StartCatalog(loginViewModel, catalogViewModel)
            val back = backClearingMessages(navController, catalogViewModel::clearMessages)
            val typeId = backStackEntry.arguments?.getString("typeId").orEmpty()
            val loaded = catalogViewModel.uiState.collectAsState().value.loaded
            val canEdit = catalogViewModel.uiState.collectAsState().value.canEdit
            LaunchedEffect(typeId, loaded) {
                if (!catalogViewModel.restoreForm(typeId)) navController.popBackStack()
            }
            // Solo el ADM edita (el DT no llega aquí; si llegara, vuelve a la lista)
            LaunchedEffect(canEdit, loaded) {
                if (loaded && !canEdit) navController.popBackStack()
            }
            EventTypeFormScreen(
                viewModel = catalogViewModel,
                onBack = back,
                onSaved = { navController.popBackStack() }
            )
        }

        // ---------- US-006 · Partidos y convocatoria (DT gestiona; ADM solo lectura) ----------
        composable("matches") {
            StartMatches(loginViewModel, matchViewModel)
            val back = backClearingMessages(navController, matchViewModel::clearMessages)
            MatchListScreen(
                viewModel = matchViewModel,
                onBack = back,
                onNewMatch = {
                    matchViewModel.startNewMatch()
                    navController.navigate("match_new")
                },
                onOpenMatch = { match -> navController.navigate("match/${match.id}") },
                onOpenCatalog = { navController.navigate("catalog") }   // US-007: el DT lo ve en solo lectura
            )
        }
        composable("match_new") {
            StartMatches(loginViewModel, matchViewModel)
            val back = backClearingMessages(navController, matchViewModel::clearMessages)
            MatchFormScreen(
                viewModel = matchViewModel,
                title = "Nuevo partido",
                subtitle = "Luego eliges a los convocados y publicas la convocatoria.",
                buttonText = "Crear partido",
                onBack = back,
                onSubmit = {
                    // Se abre el detalle del partido nuevo (para convocar) en lugar del formulario
                    matchViewModel.createMatch { matchId ->
                        navController.navigate("match/$matchId") { popUpTo("match_new") { inclusive = true } }
                    }
                }
            )
        }
        // El id en la ruta sirve para volver a cargar el partido si Android cerró la app
        composable("match/{matchId}") { backStackEntry ->
            StartMatches(loginViewModel, matchViewModel)
            val back = backClearingMessages(navController, matchViewModel::clearMessages)
            val matchId = backStackEntry.arguments?.getString("matchId").orEmpty()
            val profile = loginViewModel.uiState.collectAsState().value.profile
            LaunchedEffect(matchId, profile) { if (profile != null) matchViewModel.selectMatch(matchId) }
            MatchDetailScreen(
                viewModel = matchViewModel,
                onBack = back,
                onLineup = { navController.navigate("match/$matchId/lineup") },
                onReschedule = {
                    matchViewModel.startReschedule()
                    navController.navigate("match/$matchId/edit")
                }
            )
        }
        // Parte C · Alineación (DT edita hasta el inicio; ADM solo lectura)
        composable("match/{matchId}/lineup") { backStackEntry ->
            StartMatches(loginViewModel, matchViewModel)
            // Al salir sin guardar se descarta el borrador (la próxima vez se llena con la alineación guardada)
            val back = backClearingMessages(navController) {
                matchViewModel.clearMessages()
                matchViewModel.discardLineupDraft()
            }
            val matchId = backStackEntry.arguments?.getString("matchId").orEmpty()
            val state by matchViewModel.uiState.collectAsState()
            LaunchedEffect(matchId, state.profile) { if (state.profile != null) matchViewModel.selectMatch(matchId) }
            // El borrador se llena cuando llegan la alineación y los convocados
            LaunchedEffect(state.lineupLoaded, state.callupsLoaded, state.selectedMatchId) { matchViewModel.ensureLineupDraft() }
            LineupScreen(
                viewModel = matchViewModel,
                onBack = back,
                onSaved = {
                    matchViewModel.discardLineupDraft()
                    navController.popBackStack()
                }
            )
        }
        // Parte C · Reprogramar (fecha, hora o lugar; el rival no cambia)
        composable("match/{matchId}/edit") { backStackEntry ->
            StartMatches(loginViewModel, matchViewModel)
            val back = backClearingMessages(navController, matchViewModel::clearMessages)
            val matchId = backStackEntry.arguments?.getString("matchId").orEmpty()
            val state by matchViewModel.uiState.collectAsState()
            LaunchedEffect(matchId, state.profile) { if (state.profile != null) matchViewModel.selectMatch(matchId) }
            LaunchedEffect(state.match?.id) { matchViewModel.restoreRescheduleForm() }
            MatchFormScreen(
                viewModel = matchViewModel,
                title = "Reprogramar partido",
                subtitle = "Cambia la fecha, la hora o el lugar. Las respuestas de la convocatoria se mantienen y avisamos a los convocados.",
                buttonText = "Guardar cambios",
                onBack = back,
                onSubmit = { matchViewModel.saveReschedule { navController.popBackStack() } },
                rivalEditable = false
            )
        }

        // ---------- US-006 · Convocatorias (PAD y JUG) ----------
        composable("callups") {
            StartCallups(loginViewModel, callupViewModel)
            val back = backClearingMessages(navController, callupViewModel::clearMessages)
            CallupsScreen(viewModel = callupViewModel, onBack = back)
        }

        // ---------- Base de la US-012 · Notificaciones dentro de la app (todos los roles) ----------
        composable("notifications") {
            val profile = loginViewModel.uiState.collectAsState().value.profile
            LaunchedEffect(profile) {
                if (profile != null) notificationViewModel.start(profile) else loginViewModel.ensureProfile()
            }
            val back = backClearingMessages(navController, notificationViewModel::clearMessages)
            NotificationsScreen(
                viewModel = notificationViewModel,
                onBack = back,
                onOpen = { notification ->
                    // Abre la pantalla relacionada: el partido (DT y ADM) o "Convocatorias" (PAD y JUG)
                    when {
                        notification.matchId.isEmpty() -> Unit
                        profile?.role in listOf("DT", "ADM") -> navController.navigate("match/${notification.matchId}")
                        profile?.role in listOf("PAD", "JUG") -> navController.navigate("callups")
                    }
                }
            )
        }

        // ---------- US-002 · Registrar mi academia (luego sigue "Crea tu cuenta" y "Revisa tu correo") ----------
        composable("register_academy") {
            val state by registerViewModel.uiState.collectAsState()
            val resolver = LocalContext.current.contentResolver
            AcademyFormScreen(
                title = "Registra tu academia",
                subtitle = "Serás su administrador: podrás crear equipos e invitar a tus entrenadores.",
                buttonText = "Continuar",
                form = state.academyForm,
                errors = state.academyErrors,
                verified = null,
                logoLoading = state.logoLoading,
                loading = false,
                errorMessage = state.errorMessage,
                step = 1,
                onFormChange = registerViewModel::onAcademyFormChange,
                onLogoPicked = { uri -> registerViewModel.onLogoPicked(resolver, uri) },
                onBack = { navController.popBackStack() },
                onSubmit = { registerViewModel.validateAcademy { navController.navigate("create_account") } }
            )
        }
    }
}

// Navega y borra el historial, para que "atrás" no regrese a pantallas ya terminadas
private fun NavController.goTo(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

// US-002: pantalla del código recién generado (el código va en la ruta por si Android cierra la app)
private fun NavController.navigateToInvite(academyViewModel: AcademyViewModel) {
    navigate("academy_invite/${academyViewModel.uiState.value.generatedCode}")
}

// Mensajes de la última acción (US-002 y US-003): se borran al entrar a una pantalla por primera vez
// y al volver atrás sin guardar, para que no aparezcan en otra pantalla. Si se vuelve porque se
// guardó (onSaved), el mensaje "¡Listo!" sí se ve en la pantalla anterior.
// Devuelve la acción "atrás" (flecha de la pantalla y botón atrás del teléfono).
@Composable
private fun backClearingMessages(navController: NavController, clearMessages: () -> Unit): () -> Unit {
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!entered) {
            entered = true
            clearMessages()
        }
    }
    val back: () -> Unit = {
        clearMessages()
        navController.popBackStack()
    }
    BackHandler(onBack = back)
    return back
}

// US-002: las pantallas del ADM escuchan la academia del perfil con sesión (academyId del UserProfile).
// Si no hay perfil (Android cerró la app), se vuelve a cargar.
@Composable
private fun StartAcademy(loginViewModel: LoginViewModel, academyViewModel: AcademyViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) academyViewModel.start(profile) else loginViewModel.ensureProfile()
    }
}

// US-007: el catálogo escucha los tipos de evento de la academia del perfil con sesión (ADM o DT)
@Composable
private fun StartCatalog(loginViewModel: LoginViewModel, catalogViewModel: CatalogViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) catalogViewModel.start(profile) else loginViewModel.ensureProfile()
    }
}

// US-006: los partidos escuchan según el perfil con sesión (DT: su equipo; ADM: toda la academia)
@Composable
private fun StartMatches(loginViewModel: LoginViewModel, matchViewModel: MatchViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) matchViewModel.start(profile) else loginViewModel.ensureProfile()
    }
}

// US-006: las convocatorias del apoderado (sus hijos) o del jugador mayor (la suya)
@Composable
private fun StartCallups(loginViewModel: LoginViewModel, callupViewModel: CallupViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) callupViewModel.start(profile) else loginViewModel.ensureProfile()
    }
}

// US-003: las pantallas del jugador escuchan los datos según el perfil con sesión (rol, academia y equipo)
@Composable
private fun StartPlayers(loginViewModel: LoginViewModel, playerViewModel: PlayerViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) playerViewModel.start(profile) else loginViewModel.ensureProfile()
    }
}
