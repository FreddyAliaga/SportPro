# SportPro – Guía para agentes (Claude Code)

App Android de gestión de academias de fútbol. Proyecto del curso **Desarrollo de Soluciones Móviles – ESAN 2026-02** (docente: Luis Chang).
Responsable de este repositorio: **Freddy Aliaga** (FreddyAliaga). Equipo: Leopoldo (krackdian), Osmar (deigu0), Orlando (JavierYuen1).

## Cómo trabajar con Freddy
- Freddy está aprendiendo Android y Git. **Responde en español, con palabras simples y paso a paso.**
- **Explica qué vas a cambiar y por qué antes de hacerlo.** Cambios pequeños y revisables.
- **No hagas commit, push ni Pull Request sin que Freddy lo pida.** Los commits deben salir a nombre de Freddy (su Git local).
- Si un cambio afecta a otra historia de usuario, dilo en vez de hacerlo.

## Stack (igual que el profesor; NO actualizar versiones sin pedirlo)
- Kotlin 2.2.10 · AGP 9.4.0 · Gradle 9.6.0 · Java 11 · compileSdk/targetSdk 37 · **minSdk 34**
- Jetpack Compose (BoM 2026.02.01) + Material 3 + material-icons-extended · Navigation Compose 2.10.1 · Coil 2.7.0
- lifecycle-viewmodel-compose 2.11.0
- Firebase BoM 34.19.0: firebase-auth, firebase-firestore · plugin google-services 4.5.0
- Versiones en `gradle/libs.versions.toml`.

## Arquitectura (MVVM, lo exige el curso)
- `data/model` → modelos (data class)
- `data/remote` → acceso a Firebase (objetos/repositorios con `suspend` + `await()` y `Result`), como `FirebaseAuthManager` del profesor
- `presentation/<módulo>` → pantallas Compose + su `ViewModel` con `StateFlow` (`MutableStateFlow` privado, `asStateFlow()` público, `viewModelScope`)
- `presentation/components` → componentes reutilizables con el estilo de SportPro
- `presentation/navigation` → `AppNavGraph` con rutas string (estilo del profesor) y Navigation Drawer para pantallas con sesión
- `ui/theme` → colores y tipografía
- **No** lanzar corrutinas con `CoroutineScope(Dispatchers.Main)` dentro de composables: usar el ViewModel.

## Firebase: SOLO plan gratuito (Spark) – restricción del profesor
- Proyecto `sportpro-841f9`. Firestore en `southamerica-east1`, **modo producción** con reglas en `firestore.rules` (se publican desde la consola).
- **Disponible:** Authentication (correo/contraseña), Firestore (offline + listeners), FCM (recepción y envíos desde consola), App Check, Remote Config, Firebase AI Logic con **Gemini Developer API**.
- **NO disponible (no proponer):** Cloud Functions, Cloud Storage, Gemini de Agent Platform/Vertex, cualquier cosa que requiera Blaze.
- Alternativas acordadas: notificaciones como **bandeja dentro de la app** (la escribe la app que realiza la acción) + FCM con envíos desde la consola; moderación con contador de reportes en transacción (3 reportes = oculto); **fotos comprimidas (máx. 150 KB) en Firestore**; comunidad **solo texto**; el marcador lo calcula cada dispositivo a partir de los eventos vigentes.

## Privacidad y permisos (lo exige el curso)
- **Solo personas de 18 años o más tienen cuenta.** Los jugadores menores de 18 **no tienen cuenta**: el DT crea su perfil y su apoderado (PAD) lo gestiona y responde por ellos. Toda notificación dirigida a un menor la recibe su apoderado.
- Matriz de permisos por rol (sección 2.6 del documento de requisitos v3.0). Datos privados del jugador en `players/{id}/private/data`. El DT no ve mensualidades.
- Ninguna información de jugadores ni de partidos sale de la academia; la comunidad solo ve publicaciones y avisos. Los menores no participan en la comunidad.
- Notificaciones **sin datos sensibles**. Al modelo de IA **nunca** se envían nombres ni observaciones: los jugadores van como "Jugador #7" / "Rival #7".
- Eventos de partido: **nunca se borran físicamente** (correcciones con versión e historial).
- Al registrarse se acepta una casilla de **términos de uso** (no hay proceso de consentimiento ni derechos ARCO: se retiraron en la simplificación v3.0).
- Usar siempre **datos ficticios** para pruebas.

## Diseño (Opción G "Turquesa familiar")
- Primario `#0E6B6F`, fondo `#F2F8F8`, tarjetas blancas, superficie suave `#DDF0EF`, acento coral `#FF7A59` (solo decorativo), error `#C2410C`.
- Tipografía Nunito (res/font). Botones tipo píldora de 58 dp, tarjetas muy redondeadas (22–32 dp), áreas táctiles ≥ 44 dp.
- Tono cercano y familiar en español ("¡Hola!", "¡Te encontramos!").
- Usar los componentes de `presentation/components` en lugar de crear estilos nuevos.

## Git
- Una rama por historia: `feature/US-XXX-descripcion`. Pull Request hacia `main`, revisado por otro integrante.
- Mensajes de commit en español, claros: "US-001: valida código de invitación".
- No subir `.idea/`, `local.properties` ni claves. `google-services.json` sí va en el repo (como hace el profesor).

## Historias de usuario (documento de requisitos v3.0, simplificado)
16 historias (US-001 a US-016, 71 puntos). Cada comportamiento vive en una sola historia; las demás la citan con "(US-XXX)".
Están como issues #1 a #16 en el GitHub Project **"SportPro – Historias de Usuario"**.
- Freddy: US-001 Registro e inicio de sesión · US-002 Academia y equipos · US-003 Plantel y perfil · US-008 Registro del partido en vivo · US-009 Corrección y anulación con trazabilidad · US-015 Resumen con IA.
- Leopoldo: US-005 Entrenamientos y asistencia · US-010 Tiempo real · US-013 Comunidad y moderación.
- Orlando: US-007 Catálogo de eventos · US-012 Anuncios y notificaciones · US-016 Privacidad y permisos.
- La US-002 (partiendo del trabajo de Leopoldo) y la US-003 las desarrolla Freddy (acordado en octubre 2026).
- Osmar: US-004 Mensualidades · US-006 Convocatoria y alineación · US-011 Estadísticas · US-014 Avisos de pruebas.

## Datos de prueba en Firestore (`invitations`)
- `HALC-7K2P`: apoderado (PAD) de "Mateo R.", Academia Los Halcones, Sub-12 A.
- Campos de un código: academyId, academyName, teamId, teamName, city, role, playerId?, playerName?, invitedByName, used, usedBy?, usedAt?, expiresAt?, status? ("ANULADO" lo invalida).

## Agente revisor
Si te piden **revisar**, no edites archivos: compara la rama con `main` (`git diff main...HEAD`) y entrega una lista de problemas ordenada de más grave a menos grave, revisando criterios de aceptación de la historia, privacidad, reglas de Firestore, estilo del profesor (versiones, carpetas, MVVM), manejo de errores y textos en español.
