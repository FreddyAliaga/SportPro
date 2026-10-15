package pe.edu.esan.sportpro.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.AccountStatus
import pe.edu.esan.sportpro.data.model.Invitation
import pe.edu.esan.sportpro.data.model.RegisterForm
import pe.edu.esan.sportpro.data.model.TERMS_VERSION
import pe.edu.esan.sportpro.data.model.UserProfile

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
        var accountCreated = false
        return try {
            val authResult = auth.createUserWithEmailAndPassword(form.email.trim(), form.password).await()
            val user = authResult.user ?: throw Exception("No se pudo crear la cuenta.")
            accountCreated = true
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
                    "birthDate" to form.birthDate.trim(),
                    "invitationCode" to invitation.code,
                    "role" to invitation.role,
                    "academyId" to invitation.academyId,
                    "academyName" to invitation.academyName,
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
            }.await()

            // El perfil ya existe y el código quedó usado: desde aquí ya no se borra la cuenta
            accountCreated = false
            // Firebase envía el correo de verificación; si falla, la persona puede usar "Reenviar enlace"
            try { user.sendEmailVerification().await() } catch (_: Exception) { }
            Result.success(Unit)

        } catch (e: Exception) {
            // Si la cuenta se creó pero falló la transacción, se borra para no dejarla a medias
            if (accountCreated) {
                try { auth.currentUser?.delete()?.await() } catch (_: Exception) { }
            }
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
            if (auth.currentUser?.isEmailVerified != true) {
                return Result.success(SignInOutcome.NOT_VERIFIED)
            }
            auth.currentUser?.getIdToken(true)?.await()

            val userRef = firestore.collection("users").document(user.uid)
            val doc = userRef.get().await()
            if (!doc.exists()) {
                auth.signOut()
                throw Exception("No encontramos tu perfil en SportPro. Pide ayuda a tu academia.")
            }
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
            if (!doc.exists()) throw Exception("No encontramos tu perfil en SportPro.")
            Result.success(doc.toUserProfile())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e)))
        }
    }

    fun currentEmail(): String = auth.currentUser?.email ?: ""

    fun signOut() = auth.signOut()

    private fun DocumentSnapshot.toUserProfile() = UserProfile(
        uid = id,
        name = getString("name") ?: "",
        email = getString("email") ?: "",
        role = getString("role") ?: "",
        academyId = getString("academyId") ?: "",
        academyName = getString("academyName") ?: "",
        teamId = getString("teamId") ?: "",
        status = getString("status") ?: ""
    )

    // Mensajes claros en español para los errores de Firebase.
    // El orden importa: WeakPassword es un tipo de InvalidCredentials.
    private fun friendlyMessage(e: Exception): String = when (e) {
        is FirebaseAuthUserCollisionException -> "Ese correo ya tiene una cuenta en SportPro."
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos."
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos e inténtalo de nuevo."
        is FirebaseNetworkException -> "No hay conexión a internet. Revísala e inténtalo de nuevo."
        else -> e.message ?: "Ocurrió un error. Inténtalo de nuevo."
    }
}
