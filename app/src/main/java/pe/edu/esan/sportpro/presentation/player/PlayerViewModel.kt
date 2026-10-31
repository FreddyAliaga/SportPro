package pe.edu.esan.sportpro.presentation.player

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.DtInvitation
import pe.edu.esan.sportpro.data.model.Player
import pe.edu.esan.sportpro.data.model.PlayerHistoryEntry
import pe.edu.esan.sportpro.data.model.PlayerPrivate
import pe.edu.esan.sportpro.data.model.PlayerPrivateErrors
import pe.edu.esan.sportpro.data.model.PlayerPrivateForm
import pe.edu.esan.sportpro.data.model.PlayerSportErrors
import pe.edu.esan.sportpro.data.model.PlayerSportForm
import pe.edu.esan.sportpro.data.model.PlayerStatus
import pe.edu.esan.sportpro.data.model.Team
import pe.edu.esan.sportpro.data.model.UserProfile
import pe.edu.esan.sportpro.data.model.ageFromDigits
import pe.edu.esan.sportpro.data.model.ageOn
import pe.edu.esan.sportpro.data.model.isMinorAge
import pe.edu.esan.sportpro.data.model.categoryOrder
import pe.edu.esan.sportpro.data.model.toForm
import pe.edu.esan.sportpro.data.model.toSportForm
import pe.edu.esan.sportpro.data.model.validatePlayerPrivateForm
import pe.edu.esan.sportpro.data.model.validatePlayerSportForm
import pe.edu.esan.sportpro.data.model.withFinalPeriod
import pe.edu.esan.sportpro.data.remote.AcademyRepository
import pe.edu.esan.sportpro.data.remote.InvitationRepository
import pe.edu.esan.sportpro.data.remote.PlayerRepository
import pe.edu.esan.sportpro.presentation.components.compressImageToBase64

// Estado de toda la US-003 (una sola fuente de verdad para las pantallas del DT, ADM, PAD y JUG)
data class PlayerUiState(
    val profile: UserProfile? = null,
    val teams: List<Team> = emptyList(),                 // equipos de la academia (nombres y traslados)
    // Plantel (DT: su equipo; ADM: el equipo que eligió)
    val rosterTeamId: String = "",
    val roster: List<Player> = emptyList(),
    val rosterLoaded: Boolean = false,
    val photos: Map<String, String> = emptyMap(),        // foto de cada jugador del plantel (playerId -> Base64)
    // "Mis jugadores" del apoderado (puede tener varios hijos)
    val myPlayers: List<Player> = emptyList(),
    val myPlayersLoaded: Boolean = false,
    // Editar datos privados (apoderado o jugador mayor)
    val privateForm: PlayerPrivateForm = PlayerPrivateForm(),
    val privateErrors: PlayerPrivateErrors = PlayerPrivateErrors(),
    val photoLoading: Boolean = false,
    // "Vincular otro jugador"
    val linkCode: String = "",
    // Jugador elegido (detalle)
    val selectedPlayerId: String = "",
    val player: Player? = null,
    val playerPrivate: PlayerPrivate? = null,
    val history: List<PlayerHistoryEntry> = emptyList(),
    val invitations: List<DtInvitation> = emptyList(),
    // Un error por fuente: que una lista cargue bien no borra el error de otra
    val loadErrors: Map<String, String> = emptyMap(),
    // Nuevo jugador / editar datos deportivos
    val sportForm: PlayerSportForm = PlayerSportForm(),
    val sportErrors: PlayerSportErrors = PlayerSportErrors(),
    val editingNew: Boolean = true,
    // Último código generado (pantalla "Invita al apoderado / jugador")
    val generatedCode: String = "",
    val generatedRole: String = "",
    val generatedPlayerName: String = "",
    val generatedTeamName: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val loadError: String? get() = loadErrors.values.firstOrNull()

    // DT y ADM gestionan el plantel (las reglas lo vuelven a revisar)
    val canManage: Boolean get() = profile?.role == "DT" || profile?.role == "ADM"

    val rosterTeam: Team? get() = teams.firstOrNull { it.id == rosterTeamId }

    fun teamName(teamId: String): String = teams.firstOrNull { it.id == teamId }?.name.orEmpty()

    // Activos (y pendientes) primero, ordenados por dorsal y nombre; los inactivos aparte
    val activeRoster: List<Player>
        get() = roster.filter { it.active }.sortedWith(PLAYER_ORDER)

    val inactiveRoster: List<Player>
        get() = roster.filter { !it.active }.sortedWith(PLAYER_ORDER)

    val pendingCount: Int get() = roster.count { it.status == PlayerStatus.PENDING_GUARDIAN }

    // Equipos ACTIVOS de la academia a los que se puede trasladar al jugador
    val transferTargets: List<Team>
        get() = teams.filter { it.active && it.id != player?.teamId }
            .sortedWith(compareBy({ categoryOrder(it.category) }, { it.name.lowercase() }))

    val playerAge: Int? get() = playerPrivate?.birthDate?.let { ageOn(it) }

    // Casilla "Cuento con la autorización del apoderado": todo menor debe tenerla. Se pide al registrar
    // a un menor nuevo o al editar a uno que aún no la tiene (por ejemplo, un mayor que pasa a menor).
    val needsGuardianAuthorization: Boolean
        get() {
            val age = ageFromDigits(sportForm.birthDate) ?: return false
            return isMinorAge(age) && (editingNew || player?.guardianAuthorization != true)
        }

    // Al editar a un menor que ya tiene la autorización: se muestra quién la declaró en vez de la casilla
    val existingGuardianAuthorization: Player?
        get() {
            val age = ageFromDigits(sportForm.birthDate) ?: return null
            val p = player ?: return null
            return if (!editingNew && isMinorAge(age) && p.guardianAuthorization) p else null
        }
    // Un menor que ya cumplió 18: el DT le genera un código JUG para el MISMO perfil
    val turnedAdult: Boolean get() = player?.isMinor == true && (playerAge ?: 0) >= 18

    // Qué código se puede generar: PAD para un menor, JUG para un mayor sin cuenta (null = ninguno)
    val invitationRole: String?
        get() {
            val p = player ?: return null
            val age = playerAge ?: return null
            val teamActive = teams.firstOrNull { it.id == p.teamId }?.active == true
            return when {
                // equipo desactivado: la regla no deja crear códigos (primero se traslada al jugador)
                !p.active || p.hasAccount || !teamActive -> null
                age >= 18 -> "JUG"
                else -> "PAD"
            }
        }

    // Los más nuevos primero (un código recién creado aún no tiene fecha del servidor)
    val sortedInvitations: List<DtInvitation>
        get() = invitations.sortedByDescending { it.createdAt?.time ?: Long.MAX_VALUE }

    val sortedHistory: List<PlayerHistoryEntry>
        get() = history.sortedByDescending { it.at?.time ?: Long.MAX_VALUE }

    val sortedMyPlayers: List<Player>
        get() = myPlayers.sortedWith(compareBy({ !it.active }, { it.name.lowercase() }))

    companion object {
        private val PLAYER_ORDER = compareBy<Player>({ it.dorsal ?: Int.MAX_VALUE }, { it.name.lowercase() })
    }
}

class PlayerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var baseJobs: List<Job> = emptyList()      // equipos de la academia
    private var rosterJob: Job? = null
    private var detailJobs: List<Job> = emptyList()
    private var invitationsJob: Job? = null
    private var invitationsTeamId = ""                 // equipo con el que se escuchan los códigos del jugador
    private val requestedPhotos = mutableSetOf<String>()

    // ---------- Inicio: según el rol del perfil con sesión ----------
    fun start(profile: UserProfile) {
        if (profile.academyId.isEmpty()) return
        val current = _uiState.value.profile
        if (current?.uid == profile.uid && baseJobs.isNotEmpty()) {
            _uiState.update { it.copy(profile = profile) }
            // Al DT le pueden cambiar el equipo (US-002)
            if (profile.role == "DT" && profile.teamId != _uiState.value.rosterTeamId) openTeam(profile.teamId)
            return
        }
        stop()
        _uiState.value = PlayerUiState(profile = profile)
        baseJobs = listOf(
            listen("equipos", AcademyRepository.teamsFlow(profile.academyId)) { state, teams -> state.copy(teams = teams) }
        )
        when (profile.role) {
            "DT" -> openTeam(profile.teamId)
            // Apoderado: sus hijos son los jugadores donde está en guardianIds (no se usa users.playerId)
            "PAD" -> baseJobs = baseJobs + listen(
                "mis jugadores",
                PlayerRepository.guardianPlayersFlow(profile.uid),
                after = ::loadMissingPhotos
            ) { state, players -> state.copy(myPlayers = players, myPlayersLoaded = true) }
        }
    }

    // Al cerrar sesión se deja de escuchar todo
    fun stop() {
        baseJobs.forEach { it.cancel() }
        baseJobs = emptyList()
        rosterJob?.cancel()
        rosterJob = null
        clearSelection()
        requestedPhotos.clear()
        _uiState.value = PlayerUiState()
    }

    // Escucha una fuente; su error se guarda (o se quita) solo con su propia clave.
    // after: algo que hacer con cada dato que llega (por ejemplo, cargar fotos)
    private fun <T> listen(
        source: String,
        flow: Flow<Result<T>>,
        after: (T) -> Unit = {},
        onData: (PlayerUiState, T) -> PlayerUiState
    ): Job =
        viewModelScope.launch {
            flow.collect { result ->
                _uiState.update { state ->
                    result.fold(
                        onSuccess = { onData(state, it).copy(loadErrors = state.loadErrors - source) },
                        onFailure = {
                            state.copy(loadErrors = state.loadErrors + (source to (it.message ?: "No se pudo cargar la información.")))
                        }
                    )
                }
                result.getOrNull()?.let(after)
            }
        }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    // ---------- Plantel de un equipo (DT: el suyo; ADM: el que elige) ----------
    fun openTeam(teamId: String) {
        val academyId = _uiState.value.profile?.academyId.orEmpty()
        rosterJob?.cancel()
        rosterJob = null
        _uiState.update {
            it.copy(
                rosterTeamId = teamId,
                roster = emptyList(),
                rosterLoaded = false,
                loadErrors = it.loadErrors - "plantel",
                errorMessage = null,
                infoMessage = null
            )
        }
        if (teamId.isEmpty() || academyId.isEmpty()) return
        rosterJob = listen(
            "plantel",
            PlayerRepository.teamPlayersFlow(academyId, teamId),
            after = ::loadMissingPhotos
        ) { state, players -> state.copy(roster = players, rosterLoaded = true) }
    }

    // La foto está en los datos privados: se lee una vez por jugador.
    // Si falla, se quita de requestedPhotos para reintentarlo cuando vuelva a llegar la lista.
    private fun loadMissingPhotos(players: List<Player>) {
        players.map { it.id }.filter { requestedPhotos.add(it) }.forEach { id ->
            viewModelScope.launch {
                val photo = PlayerRepository.loadPhoto(id)
                if (photo == null) {
                    requestedPhotos.remove(id)
                } else {
                    _uiState.update { it.copy(photos = it.photos + (id to photo)) }
                }
            }
        }
    }

    // ---------- Detalle de un jugador ----------
    fun selectPlayer(playerId: String) {
        if (playerId == _uiState.value.selectedPlayerId && detailJobs.isNotEmpty()) return
        clearSelection()
        val canManage = _uiState.value.canManage
        _uiState.update { it.copy(selectedPlayerId = playerId, errorMessage = null, infoMessage = null) }
        detailJobs = listOfNotNull(
            listen("jugador", PlayerRepository.playerFlow(playerId), after = { if (canManage) listenInvitations(it) }) { state, player ->
                state.copy(player = player)
            },
            listen("datos privados", PlayerRepository.privateFlow(playerId)) { state, data ->
                state.copy(playerPrivate = data, photos = state.photos + (playerId to data.photoBase64))
            },
            if (canManage) {
                listen("historial", PlayerRepository.historyFlow(playerId)) { state, history -> state.copy(history = history) }
            } else null
        )
    }

    // Los códigos del jugador se buscan por su equipo: si lo trasladan, se vuelve a escuchar
    private fun listenInvitations(player: Player) {
        if (player.teamId == invitationsTeamId && invitationsJob != null) return
        invitationsJob?.cancel()
        invitationsTeamId = player.teamId
        invitationsJob = listen("códigos", PlayerRepository.playerInvitationsFlow(player)) { state, invitations ->
            state.copy(invitations = invitations)
        }
    }

    fun clearSelection() {
        detailJobs.forEach { it.cancel() }
        detailJobs = emptyList()
        invitationsJob?.cancel()
        invitationsJob = null
        invitationsTeamId = ""
        _uiState.update {
            it.copy(
                selectedPlayerId = "",
                player = null,
                playerPrivate = null,
                history = emptyList(),
                invitations = emptyList(),
                loadErrors = it.loadErrors - listOf("jugador", "datos privados", "historial", "códigos")
            )
        }
    }

    // ---------- Nuevo jugador / editar datos deportivos (DT o ADM) ----------
    fun startNewPlayer() {
        val category = _uiState.value.rosterTeam?.category ?: PlayerSportForm().category
        _uiState.update {
            it.copy(
                sportForm = PlayerSportForm(category = category),
                sportErrors = PlayerSportErrors(),
                editingNew = true,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun startEditSport() {
        val state = _uiState.value
        val player = state.player ?: return
        _uiState.update {
            it.copy(
                sportForm = player.toSportForm(state.playerPrivate?.birthDate),
                sportErrors = PlayerSportErrors(),
                editingNew = false,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onSportFormChange(form: PlayerSportForm) {
        _uiState.update { it.copy(sportForm = form, sportErrors = PlayerSportErrors(), errorMessage = null) }
    }

    // onSaved recibe el ID del jugador (para abrir su detalle si es nuevo)
    fun saveSport(onSaved: (String) -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.isLoading) return   // evita el doble toque
        val needsAuthorization = state.needsGuardianAuthorization
        val errors = validatePlayerSportForm(state.sportForm, requireGuardianAuthorization = needsAuthorization)
        _uiState.update { it.copy(sportErrors = errors) }
        if (errors.hasErrors) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val name = state.sportForm.name.trim()
            val result = if (state.editingNew) {
                val team = state.rosterTeam
                if (team == null || !team.active) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Este equipo está desactivado. No se pueden agregar jugadores.") }
                    return@launch
                }
                PlayerRepository.createPlayer(profile.academyId, team, state.sportForm, profile)
            } else {
                val playerId = state.player?.id ?: return@launch
                // Si queda como menor sin autorización (p. ej. pasa de mayor a menor), se guarda también
                val authorizedBy = if (needsAuthorization) profile else null
                PlayerRepository.updateSportData(playerId, state.sportForm, authorizedBy).map { playerId }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) {
                        if (state.editingNew) "¡Listo! $name ya está en el plantel." else "Datos de $name guardados."
                    } else null
                )
            }
            result.getOrNull()?.let(onSaved)
        }
    }

    // ---------- Códigos JUG / PAD (DT o ADM) ----------
    fun createInvitation(onCreated: () -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val player = state.player ?: return
        val role = state.invitationRole ?: return
        if (state.isLoading) return   // evita el doble toque
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = PlayerRepository.createPlayerInvitation(player, role, profile)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    generatedCode = result.getOrNull() ?: it.generatedCode,
                    generatedRole = if (result.isSuccess) role else it.generatedRole,
                    generatedPlayerName = if (result.isSuccess) player.shortName else it.generatedPlayerName,
                    generatedTeamName = if (result.isSuccess) state.teamName(player.teamId) else it.generatedTeamName
                )
            }
            if (result.isSuccess) onCreated()
        }
    }

    fun revokeInvitation(code: String) {
        if (_uiState.value.isLoading) return   // evita el doble toque
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = AcademyRepository.revokeInvitation(code)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "Código $code anulado." else null
                )
            }
        }
    }

    // ---------- Datos privados (apoderado o jugador mayor) ----------
    fun startEditPrivate() {
        val data = _uiState.value.playerPrivate ?: return
        _uiState.update {
            it.copy(privateForm = data.toForm(), privateErrors = PlayerPrivateErrors(), errorMessage = null, infoMessage = null)
        }
    }

    fun onPrivateFormChange(form: PlayerPrivateForm) {
        _uiState.update { it.copy(privateForm = form, privateErrors = PlayerPrivateErrors(), errorMessage = null) }
    }

    // Comprime la foto elegida a 150 KB o menos (fuera del hilo principal), igual que el logo de la US-002
    fun onPhotoPicked(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(photoLoading = true, errorMessage = null) }
            try {
                val base64 = compressImageToBase64(resolver, uri)
                _uiState.update { it.copy(photoLoading = false, privateForm = it.privateForm.copy(photoBase64 = base64)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(photoLoading = false, errorMessage = "No pudimos usar esa imagen. Prueba con otra.") }
            }
        }
    }

    fun savePrivate(onSaved: () -> Unit) {
        val state = _uiState.value
        val player = state.player ?: return
        if (state.isLoading || state.photoLoading) return   // evita el doble toque
        val errors = validatePlayerPrivateForm(state.privateForm)
        _uiState.update { it.copy(privateErrors = errors) }
        if (errors.hasErrors) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = PlayerRepository.savePrivateData(player.id, state.privateForm)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "¡Listo! Datos de ${player.name.split(" ").first()} guardados." else null
                )
            }
            if (result.isSuccess) onSaved()
        }
    }

    // ---------- "Vincular otro jugador" (apoderado que ya tiene cuenta) ----------
    fun startLink() {
        _uiState.update { it.copy(linkCode = "", errorMessage = null, infoMessage = null) }
    }

    fun onLinkCodeChange(code: String) {
        _uiState.update { it.copy(linkCode = code, errorMessage = null) }
    }

    // Valida el código (como en el registro de la US-001) y, en una transacción, lo marca como usado
    // y agrega al apoderado al jugador
    fun linkPlayer(onLinked: () -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.isLoading) return
        // Las reglas exigen una cuenta activa (correo verificado)
        if (profile.status != AccountStatus.ACTIVE) {
            _uiState.update { it.copy(errorMessage = "Verifica tu correo para vincular a otro jugador.") }
            return
        }
        if (state.linkCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Escribe el código que te dio el DT.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = InvitationRepository.findValidInvitation(state.linkCode).fold(
                onSuccess = { invitation ->
                    PlayerRepository.linkAnotherPlayer(invitation, profile).map { invitation.playerName }
                },
                onFailure = { Result.failure(it) }
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = result.getOrNull()?.let { name -> "¡Te encontramos! Ahora también eres ${withFinalPeriod("apoderado de $name")}" }
                )
            }
            if (result.isSuccess) onLinked()
        }
    }

    // ---------- Trasladar, retirar y reactivar (DT o ADM) ----------

    // onDone: el DT ya no gestiona al jugador trasladado, así que se vuelve al plantel
    fun transfer(toTeam: Team, onDone: () -> Unit) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val player = state.player ?: return
        val fromTeam = state.teams.firstOrNull { it.id == player.teamId } ?: return
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = PlayerRepository.transferPlayer(player, fromTeam, toTeam, profile)
            if (result.isSuccess) clearSelection()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) "${player.name} pasó a ${toTeam.name}. Su historial se conserva." else null
                )
            }
            if (result.isSuccess) onDone()
        }
    }

    fun setActive(active: Boolean) {
        val state = _uiState.value
        val profile = state.profile ?: return
        val player = state.player ?: return
        val team = state.teams.firstOrNull { it.id == player.teamId } ?: return
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            val result = PlayerRepository.setPlayerActive(player, team, active, profile)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message,
                    infoMessage = if (result.isSuccess) {
                        if (active) "${player.name} vuelve a estar activo." else "${player.name} quedó Inactivo. Su historial se conserva."
                    } else null
                )
            }
        }
    }
}
