package app.remindme

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.remindme.agent.ModelDownloadService
import app.remindme.ui.RemindMeScreens

class MainActivity : ComponentActivity() {
    /** The task a tapped notification asked to open, until the screens have shown it. */
    private val taskToOpen = mutableStateOf<Long?>(null)

    /** True when a download notification was tapped, until the screens have shown the Models screen. */
    private val modelsToOpen = mutableStateOf(false)

    /** Counts the user's returns to the app, so the screens look again at settings that are changed elsewhere. */
    private val resumes = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            taskToOpen.value = taskIn(intent)
            modelsToOpen.value = isModels(intent)
        }
        setContent { RemindMeScreens(RemindMeApp.from(this), taskToOpen, modelsToOpen, resumes) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskToOpen.value = taskIn(intent)
        if (isModels(intent)) modelsToOpen.value = true
    }

    override fun onResume() {
        super.onResume()
        resumes.intValue++
        // A download may have outlived the live notification (the phone stopped it): show it again.
        Thread { ModelDownloadService.resume(this) }.start()
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is one more chance to put back anything the phone dropped.
        RemindMeApp.from(this).fires.reschedule()
    }

    private fun taskIn(intent: Intent): Long? =
        intent.data?.takeIf { it.host == TASK_HOST }?.lastPathSegment?.toLongOrNull()

    private fun isModels(intent: Intent): Boolean = intent.data?.host == MODELS_HOST

    companion object {
        private const val TASK_HOST = "task"
        private const val MODELS_HOST = "models"

        /** Opens the app at the Models screen, from a download notification. */
        fun openModels(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("remindme://$MODELS_HOST"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Opens the app at one task. The task is in the data so each task's notification gets its own intent. */
        fun openTask(context: Context, taskId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("remindme://$TASK_HOST/$taskId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
