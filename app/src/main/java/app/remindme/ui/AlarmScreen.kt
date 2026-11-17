package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.remindme.RemindMeApp
import app.remindme.fire.Ringing
import app.remindme.fire.RingingTask

/** Every alarm ringing now, each with its own Done, Snooze and Stop. */
@Composable
fun AlarmScreen(app: RemindMeApp, onNothingRinging: () -> Unit) {
    val ringing = Ringing.tasks
    if (ringing.isEmpty()) LaunchedEffect(Unit) { onNothingRinging() }

    RemindMeTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ringing.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider()
                    RingingAlarm(
                        task = task,
                        onDone = { app.tools.done(task.taskId) },
                        onSnooze = { app.tools.snooze(task.taskId) },
                        onStop = { app.tools.stop(task.taskId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingingAlarm(task: RingingTask, onDone: () -> Unit, onSnooze: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            Times.time(context, task.due),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(task.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done") }
        FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Snooze 10 min") }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stop") }
    }
}
