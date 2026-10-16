package pe.edu.esan.sportpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pe.edu.esan.sportpro.presentation.navigation.AppNavGraph
import pe.edu.esan.sportpro.ui.theme.SportProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportProTheme {
                AppNavGraph()
            }
        }
    }
}
