package ani.saikou

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ani.saikou.components.SaikouBottomBar
import ani.saikou.data.local.ConnectivityObserver
import ani.saikou.navigation.SaikouNavHost
import ani.saikou.navigation.Screen
import ani.saikou.navigation.bottomBarScreens
import miyo.shared_ui.generated.resources.Res
import miyo.shared_ui.generated.resources.login_background
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@OptIn(ExperimentalResourceApi::class)
@Composable
fun SaikouApp() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val showBottomBar =
        currentRoute in bottomBarScreens.map { it.route } &&
            currentRoute != Screen.Splash.route

    // Always start at splash — it determines Login vs Home after the animation
    val startDestination = Screen.Splash.route

    // Observe connectivity for the non-blocking offline banner.
    // No longer auto-navigates to NoInternet — screens handle load failures
    // gracefully via their own empty/error states.
    val connectivity = koinInject<ConnectivityObserver>()
    val isConnected by connectivity.isConnected.collectAsState(initial = true)

    // Request notification permission on Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* granted or denied — worker handles SecurityException gracefully */ }
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Suppress the global backdrop while the splash MP4 is on screen — it
    // would otherwise flash through for one frame before the SplashScreen's
    // black Box composes on top of it. Also covers the brief null-route
    // window before NavHost wires up its start destination.
    val showAppBackdrop = currentRoute != null && currentRoute != Screen.Splash.route

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        // Global app backdrop. Renders once at the nav root so screens just
        // stop painting solid black; the same asset peeks through every
        // route. Fixed (not scrolled) — content layers compose on top.
        if (showAppBackdrop) {
            Image(
                painter = painterResource(Res.drawable.login_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Dark scrim for legibility over arbitrary image colours
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
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
                        },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                SaikouNavHost(
                    navController = navController,
                    startDestination = startDestination,
                )

                // Non-blocking offline banner — sits at the top of the current screen,
                // doesn't navigate away or interrupt the user.
                AnimatedVisibility(
                    visible = !isConnected,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFB71C1C))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            androidx.compose.foundation.layout.Arrangement
                                .spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "No internet connection",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
