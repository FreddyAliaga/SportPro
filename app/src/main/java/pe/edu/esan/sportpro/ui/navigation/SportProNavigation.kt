package pe.edu.esan.sportpro.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pe.edu.esan.sportpro.ui.screens.AcademyTeamsScreen
import pe.edu.esan.sportpro.ui.screens.CreateEditTeamScreen
import pe.edu.esan.sportpro.ui.screens.DtInvitationsScreen
import pe.edu.esan.sportpro.ui.screens.InviteCoachScreen
import pe.edu.esan.sportpro.ui.screens.RegisterAcademyScreen
import pe.edu.esan.sportpro.ui.screens.UsersManagementScreen
import pe.edu.esan.sportpro.ui.screens.community.CommunityFeedScreen
import pe.edu.esan.sportpro.ui.screens.community.ModerationReportsScreen
import pe.edu.esan.sportpro.ui.screens.community.PostCommentsScreen
import pe.edu.esan.sportpro.ui.screens.match.LiveScoreboardScreen
import pe.edu.esan.sportpro.ui.screens.match.MatchesListScreen
import pe.edu.esan.sportpro.ui.screens.training.CreateEditSessionScreen
import pe.edu.esan.sportpro.ui.screens.training.ExerciseLibraryScreen
import pe.edu.esan.sportpro.ui.screens.training.PlayerAttendanceHistoryScreen
import pe.edu.esan.sportpro.ui.screens.training.TakeAttendanceScreen
import pe.edu.esan.sportpro.ui.screens.training.TrainingSessionsScreen
import pe.edu.esan.sportpro.ui.viewmodel.AcademyViewModel
import pe.edu.esan.sportpro.ui.viewmodel.CommunityViewModel
import pe.edu.esan.sportpro.ui.viewmodel.MatchViewModel
import pe.edu.esan.sportpro.ui.viewmodel.TrainingViewModel

object SportProDestinations {
    const val ACADEMY_TEAMS = "academy_teams"
    const val REGISTER_ACADEMY = "register_academy"
    const val REGISTER_NEW_ACADEMY = "register_new_academy"
    const val CREATE_TEAM = "create_team"
    const val INVITE_COACH = "invite_coach/{code}/{teamName}"
    const val DT_INVITATIONS = "dt_invitations"
    const val USERS_MANAGEMENT = "users_management"

    // US-005 Training Routes
    const val TRAINING_SESSIONS = "training_sessions"
    const val CREATE_SESSION = "create_session"
    const val EXERCISE_LIBRARY = "exercise_library"
    const val TAKE_ATTENDANCE = "take_attendance/{sessionId}"
    const val PLAYER_ATTENDANCE_HISTORY = "player_attendance_history/{playerId}/{playerName}"

    // US-010 Live Scoreboard Routes
    const val MATCHES_LIST = "matches_list"
    const val LIVE_SCOREBOARD = "live_scoreboard/{matchId}"

    // US-013 Community Routes
    const val COMMUNITY_FEED = "community_feed"
    const val POST_COMMENTS = "post_comments"
    const val MODERATION_REPORTS = "moderation_reports"

    fun inviteCoachRoute(code: String, teamName: String): String {
        return "invite_coach/$code/$teamName"
    }

    fun takeAttendanceRoute(sessionId: String): String {
        return "take_attendance/$sessionId"
    }

    fun playerAttendanceHistoryRoute(playerId: String, playerName: String): String {
        return "player_attendance_history/$playerId/$playerName"
    }

    fun liveScoreboardRoute(matchId: String): String {
        return "live_scoreboard/$matchId"
    }
}

@Composable
fun SportProNavigation(
    viewModel: AcademyViewModel,
    trainingViewModel: TrainingViewModel,
    matchViewModel: MatchViewModel,
    communityViewModel: CommunityViewModel,
    navController: NavHostController = rememberNavController()
) {
    val teams by viewModel.teams.collectAsState()
    val users by viewModel.users.collectAsState()

    NavHost(
        navController = navController,
        startDestination = SportProDestinations.ACADEMY_TEAMS
    ) {
        composable(SportProDestinations.ACADEMY_TEAMS) {
            AcademyTeamsScreen(
                viewModel = viewModel,
                onNavigateToEditAcademy = {
                    navController.navigate(SportProDestinations.REGISTER_ACADEMY)
                },
                onNavigateToCreateNewAcademy = {
                    navController.navigate(SportProDestinations.REGISTER_NEW_ACADEMY)
                },
                onNavigateToCreateTeam = {
                    viewModel.selectTeamForEdit(null)
                    navController.navigate(SportProDestinations.CREATE_TEAM)
                },
                onNavigateToEditTeam = { team ->
                    viewModel.selectTeamForEdit(team)
                    navController.navigate(SportProDestinations.CREATE_TEAM)
                },
                onNavigateToInvitations = {
                    navController.navigate(SportProDestinations.DT_INVITATIONS)
                },
                onNavigateToUsers = {
                    navController.navigate(SportProDestinations.USERS_MANAGEMENT)
                },
                onNavigateToTrainingSessions = {
                    navController.navigate(SportProDestinations.TRAINING_SESSIONS)
                },
                onNavigateToMatchesList = {
                    navController.navigate(SportProDestinations.MATCHES_LIST)
                },
                onNavigateToCommunity = {
                    navController.navigate(SportProDestinations.COMMUNITY_FEED)
                }
            )
        }

        composable(SportProDestinations.REGISTER_ACADEMY) {
            RegisterAcademyScreen(
                viewModel = viewModel,
                isCreateNew = false,
                onNavigateBack = { navController.popBackStack() },
                onAcademySaved = { navController.popBackStack() }
            )
        }

        composable(SportProDestinations.REGISTER_NEW_ACADEMY) {
            RegisterAcademyScreen(
                viewModel = viewModel,
                isCreateNew = true,
                onNavigateBack = { navController.popBackStack() },
                onAcademySaved = { navController.popBackStack() }
            )
        }

        composable(SportProDestinations.CREATE_TEAM) {
            val selectedTeam by viewModel.selectedTeamForEdit.collectAsState()

            CreateEditTeamScreen(
                viewModel = viewModel,
                existingTeam = selectedTeam,
                onNavigateBack = { navController.popBackStack() },
                onTeamSaved = {
                    navController.popBackStack()
                },
                onInviteCoachRequested = { savedTeam ->
                    viewModel.generateInvitation(savedTeam.id, savedTeam.name) { invCode ->
                        navController.popBackStack()
                        navController.navigate(
                            SportProDestinations.inviteCoachRoute(invCode.code, savedTeam.name)
                        )
                    }
                }
            )
        }

        composable(
            route = SportProDestinations.INVITE_COACH,
            arguments = listOf(
                navArgument("code") { type = NavType.StringType },
                navArgument("teamName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val code = backStackEntry.arguments?.getString("code") ?: "HALC-D4T9"
            val teamName = backStackEntry.arguments?.getString("teamName") ?: "Sub-15 B"

            InviteCoachScreen(
                code = code,
                teamName = teamName,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(SportProDestinations.DT_INVITATIONS) {
            DtInvitationsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateInvitation = {
                    viewModel.generateInvitation(null, "Sin equipo") { invCode ->
                        navController.navigate(
                            SportProDestinations.inviteCoachRoute(invCode.code, "Sin equipo")
                        )
                    }
                }
            )
        }

        composable(SportProDestinations.USERS_MANAGEMENT) {
            UsersManagementScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // US-005 Routes
        composable(SportProDestinations.TRAINING_SESSIONS) {
            TrainingSessionsScreen(
                viewModel = trainingViewModel,
                availableTeams = teams,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCreateSession = {
                    trainingViewModel.selectSessionForEdit(null)
                    navController.navigate(SportProDestinations.CREATE_SESSION)
                },
                onNavigateToExerciseLibrary = {
                    navController.navigate(SportProDestinations.EXERCISE_LIBRARY)
                },
                onNavigateToTakeAttendance = { session ->
                    navController.navigate(SportProDestinations.takeAttendanceRoute(session.id))
                }
            )
        }

        composable(SportProDestinations.CREATE_SESSION) {
            val selectedSession by trainingViewModel.selectedSessionForEdit.collectAsState()

            CreateEditSessionScreen(
                viewModel = trainingViewModel,
                availableTeams = teams,
                existingSession = selectedSession,
                onNavigateBack = { navController.popBackStack() },
                onSessionSaved = { navController.popBackStack() }
            )
        }

        composable(SportProDestinations.EXERCISE_LIBRARY) {
            ExerciseLibraryScreen(
                viewModel = trainingViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = SportProDestinations.TAKE_ATTENDANCE,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val sessionsList by trainingViewModel.sessions.collectAsState()
            val session = sessionsList.find { it.id == sessionId } ?: sessionsList.firstOrNull()

            if (session != null) {
                val teamPlayers = users.filter { it.role.code == "JUG" && (it.teamId == session.teamId || session.teamId.isBlank()) }

                TakeAttendanceScreen(
                    viewModel = trainingViewModel,
                    session = session,
                    teamPlayers = teamPlayers,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPlayerHistory = { playerId, playerName ->
                        navController.navigate(SportProDestinations.playerAttendanceHistoryRoute(playerId, playerName))
                    }
                )
            }
        }

        composable(
            route = SportProDestinations.PLAYER_ATTENDANCE_HISTORY,
            arguments = listOf(
                navArgument("playerId") { type = NavType.StringType },
                navArgument("playerName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val playerId = backStackEntry.arguments?.getString("playerId") ?: ""
            val playerName = backStackEntry.arguments?.getString("playerName") ?: ""

            PlayerAttendanceHistoryScreen(
                viewModel = trainingViewModel,
                playerId = playerId,
                playerName = playerName,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // US-010 Routes
        composable(SportProDestinations.MATCHES_LIST) {
            MatchesListScreen(
                viewModel = matchViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLiveScoreboard = { match ->
                    matchViewModel.selectMatch(match.id)
                    navController.navigate(SportProDestinations.liveScoreboardRoute(match.id))
                }
            )
        }

        composable(
            route = SportProDestinations.LIVE_SCOREBOARD,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType })
        ) { backStackEntry ->
            val matchId = backStackEntry.arguments?.getString("matchId") ?: "match_1"
            matchViewModel.selectMatch(matchId)

            LiveScoreboardScreen(
                viewModel = matchViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // US-013 Routes
        composable(SportProDestinations.COMMUNITY_FEED) {
            CommunityFeedScreen(
                viewModel = communityViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToComments = { post ->
                    communityViewModel.selectPost(post)
                    navController.navigate(SportProDestinations.POST_COMMENTS)
                },
                onNavigateToModeration = {
                    navController.navigate(SportProDestinations.MODERATION_REPORTS)
                }
            )
        }

        composable(SportProDestinations.POST_COMMENTS) {
            val selectedPost by communityViewModel.selectedPost.collectAsState()

            if (selectedPost != null) {
                PostCommentsScreen(
                    viewModel = communityViewModel,
                    post = selectedPost!!,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(SportProDestinations.MODERATION_REPORTS) {
            ModerationReportsScreen(
                viewModel = communityViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
