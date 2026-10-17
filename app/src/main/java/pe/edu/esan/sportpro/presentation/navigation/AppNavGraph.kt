package pe.edu.esan.sportpro.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    // Cierra la sesión y vuelve a la bienvenida sin dejar pantallas en el historial
    val signOut: () -> Unit = {
        academyViewModel.stop()
        playerViewModel.stop()
        loginViewModel.signOut()
        registerViewModel.signOutAndReset()
        navController.goTo("welcome")
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
            HomeScreen(
                profile = state.profile,
                onOpenSection = { section ->
                    when (section.id) {
                        "academia" -> navController.navigate("academy")              // US-002
                        "invitaciones" -> navController.navigate("academy_users")    // US-002
                        "plantel" -> navController.navigate("roster")                // US-003 (DT)
                        "jugadores" -> navController.navigate("my_players")          // US-003 (PAD)
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
            AcademyTeamsScreen(
                viewModel = academyViewModel,
                onBack = { navController.popBackStack() },
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
                    navController.navigate("academy_team")
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
            val state by academyViewModel.uiState.collectAsState()
            val resolver = LocalContext.current.contentResolver
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
                onBack = { navController.popBackStack() },
                onSubmit = {
                    academyViewModel.saveAcademy {
                        loginViewModel.loadProfile()   // el menú muestra el nombre nuevo de la academia
                        navController.popBackStack()
                    }
                }
            )
        }
        composable("academy_team") {
            StartAcademy(loginViewModel, academyViewModel)
            TeamFormScreen(
                viewModel = academyViewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onInvitationCreated = { navController.navigate("academy_invite") }
            )
        }
        composable("academy_users") {
            StartAcademy(loginViewModel, academyViewModel)
            UsersInvitationsScreen(
                viewModel = academyViewModel,
                onBack = { navController.popBackStack() },
                onInvitationCreated = { navController.navigate("academy_invite") }
            )
        }
        composable("academy_invite") {
            InviteCoachScreen(viewModel = academyViewModel, onDone = { navController.popBackStack() })
        }

        // ---------- US-003 · Plantel y perfil del jugador (DT y ADM) ----------
        composable("roster") {
            StartPlayers(loginViewModel, playerViewModel)
            RosterScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
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
            PlayerFormScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onSaved = { playerId ->
                    // Se abre el perfil del jugador nuevo (para invitar a su apoderado) en lugar del formulario
                    playerViewModel.selectPlayer(playerId)
                    navController.navigate("player_detail") { popUpTo("player_new") { inclusive = true } }
                }
            )
        }
        composable("player_detail") {
            StartPlayers(loginViewModel, playerViewModel)
            PlayerDetailScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
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
            val resolver = LocalContext.current.contentResolver
            PrivateFormScreen(
                viewModel = playerViewModel,
                onPhotoPicked = { uri -> playerViewModel.onPhotoPicked(resolver, uri) },
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        // ---------- US-003 · Mis jugadores (PAD) ----------
        composable("my_players") {
            StartPlayers(loginViewModel, playerViewModel)
            MyPlayersScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
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
            LinkPlayerScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onLinked = { navController.popBackStack() }
            )
        }
        composable("player_edit") {
            StartPlayers(loginViewModel, playerViewModel)
            PlayerFormScreen(
                viewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        composable("player_invite") {
            InvitePlayerScreen(viewModel = playerViewModel, onDone = { navController.popBackStack() })
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

// US-002: las pantallas del ADM escuchan la academia del perfil con sesión (academyId del UserProfile)
@Composable
private fun StartAcademy(loginViewModel: LoginViewModel, academyViewModel: AcademyViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) academyViewModel.start(profile)
    }
}

// US-003: las pantallas del jugador escuchan los datos según el perfil con sesión (rol, academia y equipo)
@Composable
private fun StartPlayers(loginViewModel: LoginViewModel, playerViewModel: PlayerViewModel) {
    val profile = loginViewModel.uiState.collectAsState().value.profile
    LaunchedEffect(profile) {
        if (profile != null) playerViewModel.start(profile)
    }
}
