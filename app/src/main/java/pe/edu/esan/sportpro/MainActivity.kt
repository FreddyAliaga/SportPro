package pe.edu.esan.sportpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import pe.edu.esan.sportpro.data.repository.AcademyRepositoryImpl
import pe.edu.esan.sportpro.ui.navigation.SportProNavigation
import pe.edu.esan.sportpro.ui.theme.SportProTheme
import pe.edu.esan.sportpro.ui.viewmodel.AcademyViewModel

class MainActivity : ComponentActivity() {

    private val academyViewModel: AcademyViewModel by viewModels {
        AcademyViewModel.Factory(AcademyRepositoryImpl())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SportProNavigation(viewModel = academyViewModel)
                }
            }
        }
    }
}
