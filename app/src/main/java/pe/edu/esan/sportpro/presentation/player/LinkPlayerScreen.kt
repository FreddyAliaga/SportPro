package pe.edu.esan.sportpro.presentation.player

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import pe.edu.esan.sportpro.presentation.components.Illustration
import pe.edu.esan.sportpro.presentation.components.PrimaryButton
import pe.edu.esan.sportpro.presentation.components.SportProCard
import pe.edu.esan.sportpro.presentation.components.SportProScreen
import pe.edu.esan.sportpro.presentation.components.SportProTextField
import pe.edu.esan.sportpro.presentation.components.Subtitle
import pe.edu.esan.sportpro.presentation.components.Title
import pe.edu.esan.sportpro.presentation.components.TopBarWithSteps

// US-003 · "Vincular otro jugador": un apoderado que ya tiene cuenta ingresa un nuevo código PAD
@Composable
fun LinkPlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit, onLinked: () -> Unit) {
    val state by viewModel.uiState.collectAsState()

    SportProScreen {
        TopBarWithSteps(onBack = onBack)
        Spacer(Modifier.height(20.dp))
        Illustration(icon = Icons.Outlined.FamilyRestroom, height = 170.dp)
        Spacer(Modifier.height(24.dp))
        Title("Vincular otro jugador")
        Spacer(Modifier.height(8.dp))
        Subtitle("Escribe el código de apoderado que te dio el DT. Solo sirve para jugadores de tu academia.")
        Spacer(Modifier.height(20.dp))

        SportProCard {
            SportProTextField(
                value = state.linkCode,
                onValueChange = { viewModel.onLinkCodeChange(it.uppercase().take(11)) },   // PREFIJO-XXXXXX
                label = "Código de apoderado",
                placeholder = "HALC-7K2P9Q",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )
        }

        PlayerMessages(state)
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Vincular", onClick = { viewModel.linkPlayer(onLinked) }, loading = state.isLoading)
    }
}
