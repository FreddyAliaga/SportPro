# SportPro

App Android para la gestión de academias de fútbol (Desarrollo de Soluciones Móviles – ESAN 2026-02).

- Kotlin + Jetpack Compose (Material 3), MVVM con ViewModel + StateFlow, Navigation Compose con menú lateral.
- Firebase (plan gratuito Spark): Authentication y Cloud Firestore.
- minSdk 34 · targetSdk 37.

## Estructura
- `data/model` – modelos de datos
- `data/remote` – acceso a Firebase (repositorios)
- `presentation/<módulo>` – pantallas y ViewModels
- `presentation/navigation` – navegación
- `ui/theme` – colores y tipografía (Nunito)

## Historias implementadas
- **US-001 · Registro e inicio de sesión por rol:** bienvenida, código de invitación, crear cuenta (con términos de uso y bloqueo de menores de 18), verificación de correo, iniciar sesión, recuperar contraseña e inicio según el rol con menú lateral.

## Trabajo en equipo
Una rama por historia: `feature/US-XXX-descripcion`, con Pull Request hacia `main` revisado por otro integrante.
Las historias y su avance están en el GitHub Project **"SportPro – Historias de Usuario"**.
