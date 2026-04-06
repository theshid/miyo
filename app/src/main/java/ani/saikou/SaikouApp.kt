package ani.saikou

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ani.saikou.components.SaikouBottomBar
import ani.saikou.di.AppModule
import ani.saikou.navigation.SaikouNavHost
import ani.saikou.navigation.Screen
import ani.saikou.navigation.bottomBarScreens

@Composable
fun SaikouApp() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in bottomBarScreens.map { it.route }

    // Determine start destination based on login state
    val isLoggedIn = AppModule.repository().isLoggedIn()
    val startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route

    Scaffold(
        containerColor = ani.saikou.ui.theme.Background,
        bottomBar = {
            if (showBottomBar) {
                SaikouBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        SaikouNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
