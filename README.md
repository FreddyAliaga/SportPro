# SportPro

App Android para la gestión de academias de fútbol (Desarrollo de Soluciones Móviles – ESAN 2026-02).

- Kotlin + Jetpack Compose (Material 3), MVVM con ViewModel + StateFlow, Navigation Compose con menú lateral.
- Firebase (plan gratuito Spark): Authentication y Cloud Firestore (con caché sin conexión y listeners en tiempo real).
- minSdk 34 · targetSdk 37.

## Estructura
- `data/model` – modelos de datos y reglas de negocio (por ejemplo, el cálculo del marcador y del minuto)
- `data/remote` – acceso a Firebase (repositorios)
- `presentation/<módulo>` – pantallas y ViewModels
- `presentation/components` – componentes reutilizables con el estilo de SportPro
- `presentation/navigation` – navegación
- `ui/theme` – colores y tipografía (Nunito)

## Historias implementadas
- **US-001 · Registro e inicio de sesión por rol:** bienvenida, código de invitación, crear cuenta (con términos de uso
  y bloqueo de menores de 18), verificación de correo, iniciar sesión, recuperar contraseña e inicio según el rol.
- **US-002 · Academia y equipos:** registro de la academia por su ADM, equipos, usuarios e invitaciones de DT.
- **US-003 · Plantel y perfil del jugador:** jugadores (datos deportivos y privados), códigos para apoderados y
  jugadores mayores, historial de altas, traslados y retiros.
- **US-006 · Convocatoria y alineación:** partidos, convocatoria con respuestas en tiempo real y alineación.
- **US-007 · Catálogo de eventos:** catálogo configurable por academia (íconos y colores).
- **US-012 (base) · Notificaciones dentro de la app:** bandeja de avisos sin datos sensibles.
- **US-008 · Registro del partido en vivo:** operadores designados por el DT, cronómetro (iniciar, pausar, reanudar,
  finalizar), registro en tres toques, funciona sin conexión ("X eventos pendientes de sincronizar"), eventos
  pendientes de identificar, registrados tarde e inserción posterior.
- **US-010 · Marcador y cronología en tiempo real:** vista "En vivo" para todos los miembros, marcador calculado en
  cada teléfono, aviso "Sin conexión" con el último estado conocido y marcador en la lista de partidos.

## Reglas de seguridad de Firestore
Las reglas viven en `firestore.rules`. Para publicarlas:
1. Abre la consola de Firebase → proyecto **sportpro-841f9** → **Firestore Database** → pestaña **Reglas**.
2. Borra todo el contenido del editor y pega el archivo `firestore.rules` **completo** (no solo una parte).
3. Toca **Publicar**. Si hay un error de sintaxis, la consola lo marca con la línea.
4. Espera un minuto antes de probar la app (las reglas tardan un poco en aplicarse).

## Trabajo en el repositorio
- Una rama por historia: `feature/US-XXX-descripcion`, con Pull Request hacia `main`.
- Solo Freddy Aliaga sube cambios a este repositorio. Después de unir un Pull Request, su rama se borra.
- Las historias y su avance están en el GitHub Project **"SportPro – Historias de Usuario"**.
