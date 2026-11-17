package app.remindme.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.remindme.RemindMeApp
import app.remindme.core.FireRecord
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/** Every fire, when it was due and how late it arrived: the owner's check that the phone lets the app fire. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireLogScreen(app: RemindMeApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val records = remember(app.revision.intValue) { app.fireLog.latest() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fire log") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back") } },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing has fired yet.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(records) { record ->
                    ListItem(
                        headlineContent = { Text(record.title) },
                        supportingContent = {
                            val delivered = LocalDateTime.ofInstant(record.deliveredAt, ZoneId.systemDefault())
                            Text(
                                "Due ${Times.dateAndTime(context, record.scheduledFor)}\n" +
                                    "Arrived ${Times.dateAndTime(context, delivered)}",
                            )
                        },
                        trailingContent = { Text(lateness(record) + if (record.caughtUp) "\ncaught up" else "") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun lateness(record: FireRecord): String = FireLogWording.lateness(record.late)

object FireLogWording {
    fun lateness(late: Duration): String {
        val seconds = late.toMillis() / 1000.0
        return when {
            seconds < 60 -> String.format(java.util.Locale.ENGLISH, "%.1f s late", seconds)
            seconds < 3600 -> "${late.toMinutes()} min late"
            else -> "${late.toHours()} h ${late.toMinutesPart()} min late"
        }
    }
}
