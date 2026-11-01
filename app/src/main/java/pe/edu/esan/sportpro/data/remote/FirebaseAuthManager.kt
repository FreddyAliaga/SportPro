package pe.edu.esan.sportpro.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.Academy
import pe.edu.esan.sportpro.data.model.Invitation
import pe.edu.esan.sportpro.data.model.PrivacyTexts
import pe.edu.esan.sportpro.data.model.RegisterForm
import pe.edu.esan.sportpro.data.model.TERMS_VERSION
import pe.edu.esan.sportpro.data.model.UserProfile
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Date

// Resultado de iniciar sesión
enum class SignInOutcome { ACTIVE, NOT_VERIFIED }

object FirebaseAuthManager {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // ---------------------------------------------------------------- Registro (US-001)

    // Crea la cuenta con el rol del código de invitación.
    // El código se marca como usado en la MISMA operación (transacción) que crea el perfil,
    // así nadie puede reutilizarlo (RN-002).
    suspend fun registerWithInvitation(form: RegisterForm, invitation: Invitation): Result<Unit> {
        var account: AuthAccount? = null
        return try {
            val birthDate = toTimestamp(form.birthDate)

            // Antes de crear el usuario se vuelve a revisar el código EN EL SERVIDOR:
            // si ya no sirve (o no hay internet) no se crea nada y no quedan cuentas huérfanas.
            val freshInvitation = firestore.collection("invitations").document(invitation.code)
                .get(Source.SERVER).await()
            if (!InvitationRepository.isUsable(freshInvitation)) {
                throw IllegalStateException(InvitationRepository.INVALID_CODE_MESSAGE)
            }

            account = createOrRecoverUser(form.email.trim(), form.password)
            val user = account.user
            val uid = user.uid

            val invitationRef = firestore.collection("invitations").document(invitation.code)
            val userRef = firestore.collection("users").document(uid)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(invitationRef)
                if (!InvitationRepository.isUsable(snapshot)) {
                    throw IllegalStateException(InvitationRepository.INVALID_CODE_MESSAGE)
                }

                transaction.update(
                    invitationRef,
                    mapOf(
                        "used" to true,
                        "usedBy" to uid,
                        "usedAt" to FieldValue.serverTimestamp()
                    )
                )

                val userData = hashMapOf(
                    "name" to form.fullName.trim(),
                    "email" to form.email.trim(),
                    "phoneNumber" to form.phone.trim(),
                    "birthDate" to birthDate,   // Timestamp: las reglas comprueban que tenga 18 años o más
                    "invitationCode" to invitation.code,
                    "role" to invitation.role,
                    "academyId" to invitation.academyId,
                    // El nombre leído en ESTA transacción (si la academia se renombró después del paso 1,
                    // el código ya tiene el nombre nuevo y la regla exige el nombre real)
                    "academyName" to (snapshot.getString("academyName") ?: invitation.academyName),
                    "teamId" to invitation.teamId,
                    "playerId" to invitation.playerId,
                    "status" to AccountStatus.PENDING_VERIFICATION,
                    // Aceptación de los términos de uso (casilla de "Crea tu cuenta")
                    "termsAccepted" to mapOf(
                        "version" to TERMS_VERSION,
                        "acceptedAt" to FieldValue.serverTimestamp()
                    ),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(userRef, userData)

                // US-003: en esta MISMA operación, un código PAD agrega al apoderado al jugador
                // y un código JUG le asigna su cuenta al jugador mayor (y retira a sus apoderados)
                PlayerRepository.applyInvitationToPlayer(transaction, invitation, uid)
            }.await()

            // El perfil ya existe y el código quedó usado: desde aquí ya no se borra la cuenta
            account = null
            // Firebase envía el correo de verificación; si falla, la persona puede usar "Reenviar enlace"
            try { user.sendEmailVerification().await() } catch (_: Exception) { }
            Result.success(Unit)

        } catch (e: Exception) {
            undoAccount(account)
            Result.failure(Exception(codeUseMessage(e, invitation)))
        }
    }

    // Usuario de Auth listo para registrarse; isNew = se creó recién en este intento
    private class AuthAccount(val user: FirebaseUser, val isNew: Boolean)

    // Crea el usuario de Auth. Si el correo ya existe, puede ser un registro que quedó a medias
    // (se creó el usuario de Auth pero falló la transacción y no se pudo borrar, por ejemplo al
    // cortarse el internet): si la contraseña coincide y NO tiene perfil, se continúa con ese uid.
    // Si ya tiene perfil, se informa que el correo ya tiene una cuenta.
    private suspend fun createOrRecoverUser(email: String, password: String): AuthAccount {
        try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
                ?: throw Exception("No se pudo crear la cuenta.")
            return AuthAccount(user, isNew = true)
        } catch (collision: FirebaseAuthUserCollisionException) {
            // Contraseña distinta (o cualquier error al entrar): es la cuenta de otra persona
            val user = try {
                auth.signInWithEmailAndPassword(email, password).await().user
            } catch (_: Exception) {
                null
            } ?: throw collision
            val hasProfile = try {
                firestore.collection("users").document(user.uid).get(Source.SERVER).await().exists()
            } catch (e: Exception) {
                auth.signOut()
                throw e
            }
            if (hasProfile) {
                auth.signOut()
                throw collision   // "Ese correo ya tiene una cuenta en SportPro."
            }
            return AuthAccount(user, isNew = false)
        }
    }

    // Si el registro falla: la cuenta recién creada se borra para no dejarla a medias;
    // una cuenta recuperada (de un registro incompleto anterior) solo se cierra.
    private suspend fun undoAccount(account: AuthAccount?) {
        if (account == null) return
        if (account.isNew) {
            try { account.user.delete().await() } catch (_: Exception) { }
        } else {
            auth.signOut()
        }
    }

    // ---------------------------------------------------------------- Registro del ADM (US-002)

    // "Registrar mi academia": crea la cuenta del administrador SIN código de invitación.
    // La academia y el perfil ADM se crean en la MISMA operación (transacción): no puede quedar
    // una academia sin administrador ni un ADM sin academia.
    suspend fun registerAcademyAdmin(form: RegisterForm, academy: Academy): Result<Unit> {
        var account: AuthAccount? = null
        return try {
            val birthDate = toTimestamp(form.birthDate)

            // COMPROBACIÓN DE CONEXIÓN antes de crear el usuario de Auth.
            // ¿Por qué? Si se crea el usuario de Auth y luego la transacción falla por falta de internet,
            // tampoco se podría borrar ese usuario (borrar también necesita internet) y quedaría una
            // cuenta huérfana: correo "ocupado" pero sin perfil ni academia.
            // ¿Por qué este documento? Quien se registra aún no tiene sesión y lo único que las reglas
            // dejan leer sin sesión es un código de invitación (allow get: if true). "CONEXION" no es un
            // código real: no importa que no exista; solo interesa que el SERVIDOR responda.
            // Source.SERVER obliga a preguntar al servidor (sin usar la copia guardada en el teléfono):
            // sin internet lanza UNAVAILABLE y se muestra "No hay conexión…" sin haber creado nada.
            firestore.collection("invitations").document("CONEXION").get(Source.SERVER).await()

            account = createOrRecoverUser(form.email.trim(), form.password)
            val user = account.user
            val uid = user.uid

            val academyRef = firestore.collection("academies").document()   // ID automático
            val userRef = firestore.collection("users").document(uid)

            val academyData = hashMapOf(
                "name" to academy.name.trim(),
                "city" to academy.city.trim(),
                "institutionalEmail" to academy.institutionalEmail.trim(),
                "logoBase64" to academy.logoBase64,
                "callupNoticeHours" to academy.callupNoticeHours,
                "callupConfirmHours" to academy.callupConfirmHours,
                "verified" to false,   // la verifica el equipo del proyecto desde la consola
                "adminUid" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val userData = hashMapOf(
                "name" to form.fullName.trim(),
                "email" to form.email.trim(),
                "phoneNumber" to form.phone.trim(),
                "birthDate" to birthDate,   // Timestamp: las reglas comprueban que tenga 18 años o más
                "invitationCode" to "",
                "role" to "ADM",
                "academyId" to academyRef.id,
                "academyName" to academy.name.trim(),
                "teamId" to "",
                "playerId" to "",
                "status" to AccountStatus.PENDING_VERIFICATION,
                "termsAccepted" to mapOf(
                    "version" to TERMS_VERSION,
                    "acceptedAt" to FieldValue.serverTimestamp()
                ),
                "createdAt" to FieldValue.serverTimestamp()
            )

            // Una transacción (y no un batch) porque falla de inmediato si se corta el internet
            firestore.runTransaction { transaction ->
                transaction.set(academyRef, academyData)
                transaction.set(userRef, userData)
            }.await()

            account = null
            try { user.sendEmailVerification().await() } catch (_: Exception) { }
            Result.success(Unit)

        } catch (e: Exception) {
            undoAccount(account)
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw Exception("No hay una sesión activa.")
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    // Vuelve a consultar a Firebase si el correo ya fue verificado
    suspend fun reloadAndCheckVerified(): Result<Boolean> {
        return try {
            val user = auth.currentUser ?: throw Exception("No hay una sesión activa.")
            user.reload().await()
            val verified = auth.currentUser?.isEmailVerified == true
            // Se renueva el token para que las reglas de Firestore vean email_verified = true
            if (verified) auth.currentUser?.getIdToken(true)?.await()
            Result.success(verified)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    // Tras verificar el correo, la cuenta pasa de "pendiente de verificación" a "activa".
    suspend fun completeVerification(): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("No hay una sesión activa.")
            val userRef = firestore.collection("users").document(uid)
            val status = userRef.get().await().getString("status") ?: AccountStatus.PENDING_VERIFICATION
            if (status == AccountStatus.PENDING_VERIFICATION) {
                userRef.update("status", AccountStatus.ACTIVE).await()
                Result.success(AccountStatus.ACTIVE)
            } else {
                Result.success(status)
            }
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Inicio de sesión (US-001)

    // Inicia sesión con correo y contraseña.
    // - Si el correo no está verificado, la sesión queda abierta para poder reenviar el enlace.
    // - Si la cuenta está bloqueada o desactivada, se cierra la sesión y se informa el motivo.
    suspend fun signIn(email: String, password: String): Result<SignInOutcome> {
        return try {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = auth.currentUser ?: throw Exception("No se pudo iniciar sesión.")
            user.reload().await()

            // Primero se revisa que exista el perfil (antes que el correo verificado): un registro
            // incompleto nunca recibió el correo de verificación, así que debe ver este mensaje
            // y no la pantalla "Revisa tu correo".
            val userRef = firestore.collection("users").document(user.uid)
            val doc = userRef.get().await()
            if (!doc.exists()) {
                auth.signOut()
                throw Exception(INCOMPLETE_REGISTRATION_MESSAGE)
            }

            if (auth.currentUser?.isEmailVerified != true) {
                return Result.success(SignInOutcome.NOT_VERIFIED)
            }
            auth.currentUser?.getIdToken(true)?.await()

            when (doc.getString("status")) {
                AccountStatus.BLOCKED -> {
                    auth.signOut()
                    throw Exception("Tu cuenta está bloqueada. Comunícate con el administrador de tu academia.")
                }
                AccountStatus.INACTIVE -> {
                    auth.signOut()
                    throw Exception("Tu cuenta está desactivada. Comunícate con el administrador de tu academia.")
                }
                AccountStatus.PENDING_VERIFICATION -> userRef.update("status", AccountStatus.ACTIVE).await()
            }
            Result.success(SignInOutcome.ACTIVE)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    // "Recuperar contraseña": Firebase envía un enlace al correo
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            // Por privacidad no se revela si el correo existe: se responde igual que si se envió
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    // La sesión se mantiene al cerrar la app: si ya hay un usuario verificado, se entra directo
    fun hasVerifiedSession(): Boolean = auth.currentUser?.isEmailVerified == true

    suspend fun loadCurrentProfile(): Result<UserProfile> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("No hay una sesión activa.")
            val doc = firestore.collection("users").document(uid).get().await()
            if (!doc.exists()) throw Exception(PROFILE_NOT_FOUND_MESSAGE)
            Result.success(doc.toUserProfile())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    // US-002: escucha el perfil propio mientras hay sesión (el ADM puede desactivar la cuenta o
    // cambiar el equipo de un DT). Solo emite lo que confirma el servidor (no la copia guardada en el
    // teléfono); null = el perfil ya no existe. Sin conexión no emite nada y sigue escuchando.
    fun currentProfileFlow(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null && !snapshot.metadata.isFromCache) {
                trySend(if (snapshot.exists()) snapshot.toUserProfile() else null)
            }
        }
        awaitClose { registration.remove() }
    }

    fun currentEmail(): String = auth.currentUser?.email ?: ""

    fun signOut() = auth.signOut()

    // El formulario guarda solo dígitos ("14031988"); en Firestore se guarda como Timestamp.
    // Se usa las 12:00 (mediodía) UTC: a las 00:00 UTC, en Lima (UTC-5) se vería el día anterior.
    // Al mediodía UTC cae en el mismo día en cualquier zona horaria de América.
    // Si alguna vez se convierte de vuelta a fecha, usar también ZoneOffset.UTC.
    // También lo usa la US-003 para la fecha de nacimiento del jugador.
    fun toTimestamp(digits: String): Timestamp {
        val formatter = DateTimeFormatter.ofPattern("ddMMuuuu").withResolverStyle(ResolverStyle.STRICT)
        val date = LocalDate.parse(digits, formatter)
        return Timestamp(Date.from(date.atTime(12, 0).toInstant(ZoneOffset.UTC)))
    }

    const val NO_CONNECTION_MESSAGE = "No hay conexión a internet. Revísala e inténtalo de nuevo."

    const val PROFILE_NOT_FOUND_MESSAGE = "No encontramos tu perfil en SportPro."

    // US-002: la sesión sigue abierta pero el perfil ya no existe (o no hay sesión)
    const val ACCOUNT_NOT_FOUND_MESSAGE = "No encontramos tu cuenta. Inicia sesión de nuevo."

    // US-003: el jugador del código no existe (solo al usar un código: registro y "Vincular otro jugador")
    const val CODE_DATA_NOT_FOUND_MESSAGE =
        "No encontramos los datos de este código. Pide uno nuevo a tu academia o a tu entrenador."

    // US-003: con un código JUG, la regla exige que la fecha de nacimiento sea la del perfil del jugador.
    // La app no puede compararla antes (la fecha del perfil es un dato privado que aún no puede leer).
    const val JUG_BIRTH_DATE_MISMATCH_MESSAGE =
        "Tu fecha de nacimiento no coincide con la del perfil del jugador. Revisa la fecha o pide ayuda a tu DT."

    // Mensaje de un error al USAR un código (registro o "Vincular otro jugador")
    fun codeUseMessage(e: Exception, invitation: Invitation): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND ->
            CODE_DATA_NOT_FOUND_MESSAGE
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED &&
            invitation.role == "JUG" && invitation.playerId.isNotEmpty() -> JUG_BIRTH_DATE_MISMATCH_MESSAGE
        else -> friendlyMessage(e)
    }

    // Cuenta de Auth sin perfil (el registro se cortó a la mitad): se termina registrándose otra vez
    const val INCOMPLETE_REGISTRATION_MESSAGE =
        "Tu registro quedó incompleto. Vuelve a registrarte con el mismo correo y contraseña para terminarlo."

    // Mensajes claros en español para los errores de Firebase.
    // El orden importa: WeakPassword es un tipo de InvalidCredentials.
    fun friendlyMessage(e: Exception): String = when {
        e is FirebaseAuthUserCollisionException -> "Ese correo ya tiene una cuenta en SportPro."
        e is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil."
        e is FirebaseAuthInvalidUserException ||
            e is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        e is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
        e is FirebaseNetworkException -> NO_CONNECTION_MESSAGE
        // Firestore sin internet (y sin copia guardada en el teléfono)
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> NO_CONNECTION_MESSAGE
        // Las reglas de Firestore no permiten la operación
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            PrivacyTexts.PERMISSION_DENIED
        // Se quiso actualizar un documento que no existe
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND ->
            "No encontramos estos datos. Actualiza e inténtalo de nuevo."
        else -> e.message ?: "Ocurrió un error. Inténtalo de nuevo."
    }
}

// Convierte users/{uid} en UserProfile.
// Está fuera del object para que también la use AcademyRepository al listar los usuarios de la academia (US-002).
fun DocumentSnapshot.toUserProfile() = UserProfile(
    uid = id,
    name = getString("name") ?: "",
    email = getString("email") ?: "",
    role = getString("role") ?: "",
    academyId = getString("academyId") ?: "",
    academyName = getString("academyName") ?: "",
    teamId = getString("teamId") ?: "",
    playerId = getString("playerId") ?: "",
    status = getString("status") ?: ""
)
