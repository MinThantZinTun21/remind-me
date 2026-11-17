package app.remindme

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.remindme.ui.RemindMeScreens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Until the set-up checklist exists, ask for the one permission a notification needs.
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        setContent { RemindMeScreens(RemindMeApp.from(this)) }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is one more chance to put back anything the phone dropped.
        RemindMeApp.from(this).tools.reschedule()
    }
}
