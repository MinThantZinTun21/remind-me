package app.remindme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.remindme.ui.RemindMeScreens

class MainActivity : ComponentActivity() {
    /** The task a tapped notification asked to open, until the screens have shown it. */
    private val taskToOpen = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Until the set-up checklist exists, ask for the one permission a notification needs.
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        if (savedInstanceState == null) taskToOpen.value = taskIn(intent)
        setContent { RemindMeScreens(RemindMeApp.from(this), taskToOpen) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskToOpen.value = taskIn(intent)
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is one more chance to put back anything the phone dropped.
        RemindMeApp.from(this).tools.reschedule()
    }

    private fun taskIn(intent: Intent): Long? =
        intent.data?.takeIf { it.host == TASK_HOST }?.lastPathSegment?.toLongOrNull()

    companion object {
        private const val TASK_HOST = "task"

        /** Opens the app at one task. The task is in the data so each task's notification gets its own intent. */
        fun openTask(context: Context, taskId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("remindme://$TASK_HOST/$taskId"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
