package pe.edu.esan.sportpro.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    // US-001: un ViewModel para el registro y otro para el inicio de sesión y la sesión
    val registerViewModel: RegisterViewModel = viewModel()
    val loginViewModel: LoginViewModel = viewModel()

    // Cierra la sesión y vuelve a la bienvenida sin dejar pantallas en el historial
    val signOut: () -> Unit = {
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
                onRegisterAcademy = { navController.navigate("register_academy") },
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
                onMinor = { navController.navigate("minor_blocked") },
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
                onOpenSection = { section -> navController.navigate("section/${section.id}") },
                onMyProfile = { navController.navigate("section/perfil") },
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

        // "Registrar mi academia" pertenece a la US-002
        composable("register_academy") {
            ComingSoonScreen("Registrar mi academia", "US-002") { navController.popBackStack() }
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
