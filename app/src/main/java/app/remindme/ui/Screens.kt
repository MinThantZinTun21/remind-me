package app.remindme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.remindme.RemindMeApp
import app.remindme.setup.SetUp

sealed interface Screen {
    data object Home : Screen
    data object NewTask : Screen
    /** [draft] is a sentence the user picked to try, already in the text field. */
    data class Agent(val draft: String = "") : Screen
    data class EditTask(val taskId: Long) : Screen
    data object History : Screen
    data object SetUp : Screen
    data object FireLog : Screen
}

/** The way back to the task list, the same on every screen. */
@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

/** The app's look: Material 3 in the phone's own colours, light or dark as the phone is. */
@Composable
fun RemindMeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** A screen reached from the menu: its title, the way back when there is one, and what it shows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(title: String, onBack: (() -> Unit)?, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { if (onBack != null) BackButton(onBack) },
            )
        },
        content = content,
    )
}

/**
 * [taskToOpen] is set when the user taps a notification; the screens show that task and clear it.
 * [resumes] changes each time the user comes back to the app.
 */
@Composable
fun RemindMeScreens(app: RemindMeApp, taskToOpen: MutableState<Long?>, resumes: IntState) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val setUp = remember { SetUp(app) }
    var setUpFinished by remember { mutableStateOf(setUp.finished) }

    LaunchedEffect(taskToOpen.value) {
        taskToOpen.value?.let { screen = Screen.EditTask(it) }
        taskToOpen.value = null
    }

    RemindMeTheme {
        if (!setUpFinished) {
            // First run: nothing else is reachable until notifications are allowed.
            SetUpScreen(setUp, resumes.intValue, firstRun = true, onClose = { setUp.finished = true; setUpFinished = true })
            return@RemindMeTheme
        }
        BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
        when (val current = screen) {
            Screen.Home -> HomeScreen(
                app,
                notificationsOff = remember(resumes.intValue) { !setUp.notificationsOn() },
                onNewTask = { screen = Screen.NewTask },
                onAgent = { draft -> screen = Screen.Agent(draft) },
                onEditTask = { screen = Screen.EditTask(it) },
                onHistory = { screen = Screen.History },
                onSetUp = { screen = Screen.SetUp },
                onFireLog = { screen = Screen.FireLog },
            )
            Screen.NewTask -> TaskFormScreen(app, taskId = null, onClose = { screen = Screen.Home })
            is Screen.Agent -> AgentScreen(app, draft = current.draft, onEditTask = { screen = Screen.EditTask(it) }, onClose = { screen = Screen.Home })
            is Screen.EditTask -> TaskFormScreen(app, taskId = current.taskId, onClose = { screen = Screen.Home })
            Screen.History -> HistoryScreen(app, onClose = { screen = Screen.Home })
            Screen.SetUp -> SetUpScreen(setUp, resumes.intValue, firstRun = false, onClose = { screen = Screen.Home })
            Screen.FireLog -> FireLogScreen(app, onClose = { screen = Screen.Home })
        }
    }
}
