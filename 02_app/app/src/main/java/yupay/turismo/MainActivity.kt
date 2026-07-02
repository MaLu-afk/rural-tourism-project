package yupay.turismo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import yupay.turismo.notifications.NotificationHelper
import yupay.turismo.sync.SyncViewModel
import yupay.turismo.ui.MainViewModel
import yupay.turismo.ui.navigation.MainNavigation
import yupay.turismo.ui.theme.Final_projectTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private val syncViewModel: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Deep link inicial (app abierta desde una notificación con la app cerrada).
        handleNavIntent(intent)
        setContent {
            Final_projectTheme {
                MainNavigation(
                    viewModel = mainViewModel,
                    syncViewModel = syncViewModel
                )
            }
        }
    }

    /** La app ya estaba viva (singleTop): llega un nuevo Intent al tocar otra notificación. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavIntent(intent)
    }

    private fun handleNavIntent(intent: Intent?) {
        val target = intent?.getStringExtra(NotificationHelper.EXTRA_NAV_TARGET) ?: return
        mainViewModel.setNavTarget(target)
    }
}
