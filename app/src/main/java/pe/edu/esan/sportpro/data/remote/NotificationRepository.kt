package pe.edu.esan.sportpro.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.AppNotification
import pe.edu.esan.sportpro.data.model.NOTIFICATION_BODY_MAX
import pe.edu.esan.sportpro.data.model.NOTIFICATION_TITLE_MAX
import pe.edu.esan.sportpro.data.model.NoticeRecipient
import pe.edu.esan.sportpro.data.model.NotificationDraft
import pe.edu.esan.sportpro.data.model.UserProfile

// Base de la US-012 · Bandeja de notificaciones dentro de la app (plan gratuito: sin Cloud Functions).
// La app de quien hace la acción escribe una notificación por destinatario; cada persona solo lee las suyas.
object NotificationRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val notificationsRef = firestore.collection("notifications")

    // Notificaciones por transacción. La regla lee, compartidos, mi perfil, el equipo y el partido, y por cada
    // aviso el convocado: 3 + 10 = 13 de las 20 lecturas permitidas por operación.
    private const val CHUNK = 10

    // Mis notificaciones (un filtro de igualdad: sin índice compuesto; la app las ordena por fecha)
    fun myNotificationsFlow(uid: String): Flow<Result<List<AppNotification>>> =
        queryFlow(notificationsRef.whereEqualTo("recipientUid", uid)) { it.toNotification() }

    // Escribe la misma notificación para cada destinatario (sin repetir personas ni enviarse a sí mismo).
    // Cada una tiene un ID fijo (draft.idFor): dentro de la transacción se saltan las que ya existen,
    // así un reintento no duplica avisos. Sin internet falla de inmediato.
    suspend fun send(academyId: String, recipients: List<NoticeRecipient>, draft: NotificationDraft, by: UserProfile): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val unique = recipients.filter { it.uid.isNotBlank() && it.uid != by.uid }.distinctBy { it.uid }
            unique.chunked(CHUNK).forEach { chunk ->
                firestore.runTransaction { transaction ->
                    // En una transacción primero se lee todo y después se escribe
                    val pending = chunk.filter { !transaction.get(notificationsRef.document(draft.idFor(it.uid))).exists() }
                    pending.forEach { recipient ->
                        transaction.set(
                            notificationsRef.document(draft.idFor(recipient.uid)),
                            hashMapOf(
                                "recipientUid" to recipient.uid,
                                "academyId" to academyId,
                                "type" to draft.type,
                                "title" to draft.title.take(NOTIFICATION_TITLE_MAX),
                                "body" to draft.body.take(NOTIFICATION_BODY_MAX),
                                "matchId" to draft.matchId,
                                "playerId" to recipient.playerId,
                                "read" to false,
                                "createdBy" to by.uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }.await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Al tocarla se marca como leída (solo la puede marcar su destinatario).
    // Transacción: sin internet falla de inmediato (no queda pendiente en el teléfono).
    suspend fun markRead(notificationId: String): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val ref = notificationsRef.document(notificationId)
            firestore.runTransaction { transaction ->
                transaction.get(ref)
                transaction.update(ref, "read", true)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    private fun DocumentSnapshot.toNotification() = AppNotification(
        id = id,
        recipientUid = getString("recipientUid") ?: "",
        academyId = getString("academyId") ?: "",
        type = getString("type") ?: "",
        title = getString("title") ?: "",
        body = getString("body") ?: "",
        matchId = getString("matchId") ?: "",
        playerId = getString("playerId") ?: "",
        read = getBoolean("read") ?: false,
        // ESTIMATE: justo al llegar, la hora del servidor aún no está; se usa la estimada del teléfono
        createdAt = getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()
    )
}
