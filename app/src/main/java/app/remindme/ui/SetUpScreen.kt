package app.remindme.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.remindme.setup.SetUp

/**
 * The set-up checklist. On first run it stands in front of the app until notifications are allowed;
 * later it opens from the home menu. [resumes] changes each time the user comes back to the app,
 * which is when a setting may have changed on a system screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetUpScreen(setUp: SetUp, resumes: Int, firstRun: Boolean, onClose: () -> Unit) {
    val activity = LocalActivity.current ?: return
    var notificationsOn by remember(resumes) { mutableStateOf(setUp.notificationsOn()) }
    val batteryExempt = remember(resumes) { setUp.batteryExempt() }
    var autoStartTicked by remember { mutableStateOf(setUp.autoStartTicked) }
    var lockedTicked by remember { mutableStateOf(setUp.lockedInRecentsTicked) }

    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = setUp.notificationsOn()
        // After a second refusal Android stops asking, and only the settings page can turn notifications on.
        val stillAsks = activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (!granted && !stillAsks) setUp.openNotificationSettings(activity)
    }

    fun allowNotifications() {
        val granted = activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) setUp.openNotificationSettings(activity) else askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set-up") },
                navigationIcon = { if (!firstRun) TextButton(onClick = onClose) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Four settings, made once, so this phone lets Remind Me tell you on time.")
            SetUpItem(
                title = "1. Notifications",
                state = if (notificationsOn) "Allowed" else "Not allowed",
                good = notificationsOn,
                needed = true,
                why = "Remind Me tells you a task is due with a notification or an alarm. Without this it cannot tell you anything.",
            ) {
                if (!notificationsOn) Button(onClick = ::allowNotifications) { Text("Allow notifications") }
            }
            SetUpItem(
                title = "2. Auto-start",
                state = if (autoStartTicked) "You did this" else "Not ticked",
                good = autoStartTicked,
                why = "Switch Remind Me on in the Auto-start list, so the phone lets it start again after a restart.",
            ) {
                OutlinedButton(onClick = { setUp.openAutoStart(activity) }) { Text("Open Auto-start") }
                UserTick(autoStartTicked) { autoStartTicked = it; setUp.autoStartTicked = it }
            }
            SetUpItem(
                title = "3. Battery",
                state = if (batteryExempt) "No restrictions" else "Restricted",
                good = batteryExempt,
                why = "Let Remind Me run without battery restrictions, so the phone does not hold a task back to save power.",
            ) {
                if (!batteryExempt) OutlinedButton(onClick = { setUp.askForBatteryExemption(activity) }) { Text("Remove restrictions") }
            }
            SetUpItem(
                title = "4. Lock in recent apps",
                state = if (lockedTicked) "You did this" else "Not ticked",
                good = lockedTicked,
                why = "Open recent apps, press and hold the Remind Me card and choose Lock, so clearing recent apps does not close it.",
            ) {
                UserTick(lockedTicked) { lockedTicked = it; setUp.lockedInRecentsTicked = it }
            }
            if (firstRun) {
                Button(onClick = onClose, enabled = notificationsOn, modifier = Modifier.fillMaxWidth()) {
                    Text("Start using Remind Me")
                }
                Text(
                    if (notificationsOn) "You can finish the rest later from the menu, under Set-up." else "Allow notifications to go on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetUpItem(
    title: String,
    state: String,
    good: Boolean,
    why: String,
    /** Only an item the app cannot work without shows its missing state as an error. */
    needed: Boolean = false,
    actions: @Composable () -> Unit,
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    state,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        good -> MaterialTheme.colorScheme.primary
                        needed -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(why, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

/** For a setting the app cannot read: the user says it is done. */
@Composable
private fun UserTick(ticked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.toggleable(value = ticked, role = Role.Checkbox, onValueChange = onChange).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = ticked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Text("I did it")
    }
}
