package pe.edu.esan.sportpro.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import pe.edu.esan.sportpro.data.model.DEFAULT_EVENT_TYPES
import pe.edu.esan.sportpro.data.model.DUPLICATE_EVENT_NAME_MESSAGE
import pe.edu.esan.sportpro.data.model.EventType
import pe.edu.esan.sportpro.data.model.EventTypeForm
import pe.edu.esan.sportpro.data.model.eventNameKey

// US-007 · Catálogo configurable de eventos: academies/{academyId}/eventTypes/{typeId}
// - Lectura: listener (los cambios llegan en tiempo real, también durante un partido en curso).
// - Escrituras: solo el ADM, con await() y Result. Nunca se borra: desactivar = active false.
object EventTypeRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun typesRef(academyId: String) =
        firestore.collection("academies").document(academyId).collection("eventTypes")

    // ---------------------------------------------------------------- Lectura

    fun eventTypesFlow(academyId: String): Flow<Result<List<EventType>>> =
        queryFlow(typesRef(academyId)) { it.toEventType() }

    // ---------------------------------------------------------------- Catálogo de fábrica (solo el ADM)

    // Si la academia aún no tiene catálogo, crea los 12 tipos de fábrica en un solo lote (ID fijo = clave).
    // Se consulta el servidor: sin internet falla aquí, antes de escribir nada.
    // Devuelve true si los creó y false si la academia ya tenía catálogo.
    suspend fun ensureDefaults(academyId: String): Result<Boolean> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val existing = typesRef(academyId).limit(1).get(Source.SERVER).await()
            if (!existing.isEmpty) return Result.success(false)
            val batch = firestore.batch()
            DEFAULT_EVENT_TYPES.forEach { type ->
                batch.set(
                    typesRef(academyId).document(type.id),
                    typeData(type) + mapOf(
                        "active" to true,
                        "system" to true,
                        "order" to type.order,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // ---------------------------------------------------------------- Crear y editar (ADM)

    // "Nuevo / Editar tipo de evento". Antes de guardar se revisa EN EL SERVIDOR que el nombre no se
    // repita en la academia (las reglas no pueden comprobarlo). nextOrder = posición de un tipo nuevo.
    // Usar solo con el formulario ya validado.
    suspend fun saveEventType(academyId: String, form: EventTypeForm, nextOrder: Int): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            val all = typesRef(academyId).get(Source.SERVER).await().documents
            val nameTaken = all.any { it.id != form.id && eventNameKey(it.getString("name") ?: "") == eventNameKey(form.name) }
            if (nameTaken) throw IllegalStateException(DUPLICATE_EVENT_NAME_MESSAGE)

            val type = EventType(
                id = form.id,
                name = form.name.trim().replace(Regex("\\s+"), " "),
                icon = form.icon,
                color = form.color,
                // Inicio y Fin: nunca afectan el marcador ni piden jugador o equipo
                affectsScore = form.affectsScore && !form.alwaysActive,
                requiresPlayer = form.requiresPlayer && !form.alwaysActive,
                requiresTeam = form.requiresTeam && !form.alwaysActive,
                allowsNote = form.allowsNote
            )
            // Transacción: sin internet falla de inmediato (no queda pendiente en el teléfono)
            val ref = if (form.id.isEmpty()) typesRef(academyId).document() else typesRef(academyId).document(form.id)
            firestore.runTransaction { transaction ->
                if (form.id.isEmpty()) {
                    transaction.set(
                        ref,
                        typeData(type) + mapOf(
                            "active" to true,
                            "system" to false,
                            "order" to nextOrder,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    if (!transaction.get(ref).exists()) throw IllegalStateException("No encontramos este tipo de evento.")
                    transaction.update(ref, typeData(type) + mapOf("updatedAt" to FieldValue.serverTimestamp()))
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            // Los errores propios (nombre repetido, tipo no encontrado) ya están en español
            val own = e as? IllegalStateException ?: e.cause as? IllegalStateException
            Result.failure(own ?: Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // "Desactivar / Reactivar". Inicio y Fin nunca se desactivan (la regla también lo impide).
    suspend fun setActive(academyId: String, typeId: String, active: Boolean): Result<Unit> {
        if (!ConnectionChecker.isOnline()) return ConnectionChecker.noConnection()
        return try {
            // Transacción: sin internet falla de inmediato (no queda pendiente en el teléfono)
            val ref = typesRef(academyId).document(typeId)
            firestore.runTransaction { transaction ->
                transaction.get(ref)
                transaction.update(ref, mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(FirebaseAuthManager.friendlyMessage(e)))
        }
    }

    // Campos que el ADM puede editar (los mismos para crear y actualizar).
    // Una propiedad coherente: afectar el marcador o pedir jugador exige saber de qué equipo es.
    private fun typeData(type: EventType): Map<String, Any> = mapOf(
        "name" to type.name,
        "icon" to type.icon,
        "color" to type.color,
        "affectsScore" to type.affectsScore,
        "requiresPlayer" to type.requiresPlayer,
        "requiresTeam" to (type.requiresTeam || type.requiresPlayer || type.affectsScore),
        "allowsNote" to type.allowsNote
    )

    // ---------------------------------------------------------------- Conversión

    private fun DocumentSnapshot.toEventType() = EventType(
        id = id,
        name = getString("name") ?: "",
        icon = getString("icon") ?: "",
        color = getString("color") ?: "",
        affectsScore = getBoolean("affectsScore") ?: false,
        requiresPlayer = getBoolean("requiresPlayer") ?: false,
        requiresTeam = getBoolean("requiresTeam") ?: false,
        allowsNote = getBoolean("allowsNote") ?: true,
        active = getBoolean("active") ?: true,
        system = getBoolean("system") ?: false,
        order = getLong("order")?.toInt() ?: 0
    )
}
