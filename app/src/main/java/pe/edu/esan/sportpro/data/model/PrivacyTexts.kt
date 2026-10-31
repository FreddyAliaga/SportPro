package pe.edu.esan.sportpro.data.model

// Avisos de protección de datos personales (Ley N.° 29733). Se cambian solo aquí.
// Redacción: la regla de 18 años es decisión del proyecto (más estricta que la ley),
// por eso siempre "para proteger… conforme a la Ley N.° 29733" y nunca "la ley prohíbe…".
object PrivacyTexts {
    const val LAW_LABEL = "Ley N.° 29733"

    // Títulos
    const val TITLE_PERSONAL_DATA = "Protección de datos personales"
    const val TITLE_YOUR_DATA = "Tus datos están protegidos"
    const val TITLE_PRIVATE_DATA = "Datos privados protegidos"

    // US-001 · Menor bloqueado
    const val MINOR_BLOCKED =
        "Para proteger los datos personales de los menores, conforme a la Ley N.° 29733 de Protección de Datos " +
            "Personales, en SportPro solo las personas de 18 años o más tienen cuenta. El entrenador registra al menor y su " +
            "apoderado gestiona sus datos."

    // US-001 · Crea tu cuenta (también el registro del ADM)
    const val CREATE_ACCOUNT =
        "Tratamos tus datos conforme a la Ley N.° 29733. Usamos tu fecha de nacimiento solo para confirmar que eres " +
            "mayor de edad y tus datos solo los ve tu academia."

    // US-003 · Nuevo jugador menor de edad
    const val NEW_MINOR_PLAYER =
        "Conforme a la Ley N.° 29733, los menores no tienen cuenta propia: su apoderado autoriza y gestiona sus datos " +
            "privados."

    // US-003 · Perfil del jugador, sección "Datos privados"
    const val PLAYER_PRIVATE_DATA =
        "Conforme a la Ley N.° 29733, estos datos solo los ven la administración, el DT de su equipo, sus apoderados " +
            "y el propio jugador si es mayor de edad. La fecha de nacimiento solo la cambian el DT o la administración."

    // US-003 · Invitar apoderado / jugador
    const val INVITE_GUARDIAN =
        "Para proteger al menor (Ley N.° 29733), el código solo muestra su nombre corto. Compártelo solo con su apoderado."
    const val INVITE_PLAYER =
        "Por protección de datos (Ley N.° 29733), el código solo muestra el nombre corto del jugador. Compártelo solo con él."

    // US-003 · Datos privados que editan el apoderado o el jugador mayor
    const val BIRTH_DATE_LOCKED = "La fecha de nacimiento solo la cambian el DT o la administración (Ley N.° 29733)."

    // Reglas de Firestore que no permiten la operación
    const val PERMISSION_DENIED =
        "No tienes permiso para ver o cambiar estos datos. SportPro protege los datos personales conforme a la Ley N.° 29733. " +
            "Revisa los datos e inténtalo de nuevo."

    // US-003 · Casilla "Cuento con la autorización del apoderado" (Reglamento D.S. 016-2024-JUS, art. 22)
    const val GUARDIAN_AUTHORIZATION_BOLD = "Cuento con la autorización del apoderado"
    const val GUARDIAN_AUTHORIZATION_REST =
        " para registrar los datos de este jugador en SportPro (por ejemplo, en la ficha de inscripción de la academia)."
    const val GUARDIAN_AUTHORIZATION_ERROR =
        "Marca la casilla: necesitas la autorización del apoderado para registrar a un menor."
}
