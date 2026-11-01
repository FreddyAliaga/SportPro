package pe.edu.esan.sportpro.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Listeners de Firestore convertidos en Flow (mismo estilo que AcademyRepository y PlayerRepository).
// Los usan los repositorios de la US-007 y la US-006: la lista se actualiza sola cuando cambia en Firestore.

// Una consulta: cada cambio llega como la lista completa
internal fun <T> queryFlow(query: Query, map: (DocumentSnapshot) -> T): Flow<Result<List<T>>> = callbackFlow {
    val registration = query.addSnapshotListener { snapshot, error ->
        when {
            error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
            snapshot != null -> trySend(Result.success(snapshot.documents.map(map)))
        }
    }
    awaitClose { registration.remove() }
}

// Un documento. Si no existe se avisa con notFoundMessage (en vez de quedarse cargando para siempre)
internal fun <T> documentFlow(
    ref: DocumentReference,
    notFoundMessage: String,
    map: (DocumentSnapshot) -> T
): Flow<Result<T>> = callbackFlow {
    val registration = ref.addSnapshotListener { snapshot, error ->
        when {
            error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
            snapshot == null -> Unit
            snapshot.exists() -> trySend(Result.success(map(snapshot)))
            else -> trySend(Result.failure(Exception(notFoundMessage)))
        }
    }
    awaitClose { registration.remove() }
}

// Un documento que puede no existir todavía (null), por ejemplo la alineación antes de armarla
internal fun <T> optionalDocumentFlow(ref: DocumentReference, map: (DocumentSnapshot) -> T): Flow<Result<T?>> = callbackFlow {
    val registration = ref.addSnapshotListener { snapshot, error ->
        when {
            error != null -> trySend(Result.failure(Exception(FirebaseAuthManager.friendlyMessage(error))))
            snapshot != null -> trySend(Result.success(if (snapshot.exists()) map(snapshot) else null))
        }
    }
    awaitClose { registration.remove() }
}
