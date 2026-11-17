package app.remindme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.remindme.RemindMeApp

sealed interface Screen {
    data object Home : Screen
    data object NewTask : Screen
    data object FireLog : Screen
}

@Composable
fun RemindMeScreens(app: RemindMeApp) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    MaterialTheme(colorScheme = colors) {
        BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
        when (screen) {
            Screen.Home -> HomeScreen(app, onNewTask = { screen = Screen.NewTask }, onFireLog = { screen = Screen.FireLog })
            Screen.NewTask -> TaskFormScreen(app, onClose = { screen = Screen.Home })
            Screen.FireLog -> FireLogScreen(app, onClose = { screen = Screen.Home })
        }
    }
}
