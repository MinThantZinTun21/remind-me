package app.remindme.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.remindme.RemindMeApp
import app.remindme.wording.Times
import app.remindme.wording.label
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Every fire, when it was meant to come and how long after that it arrived: the user's check that the phone lets the app fire. */
@Composable
fun FireLogScreen(app: RemindMeApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val records = remember(app.revision.intValue) { app.fireLog.latest() }

    MenuScreen("Fire log", onBack = onClose) { padding ->
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
                                "${record.kind.label}, meant for ${Times.dateAndTime(context, record.scheduledFor)}\n" +
                                    "Arrived ${Times.dateAndTime(context, delivered)}",
                            )
                        },
                        trailingContent = { Text(behind(record.lateness) + if (record.caughtUp) "\ncaught up" else "") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** How long after its moment a fire arrived: "0.4 s behind", "3 min behind", "2 h 5 min behind". */
private fun behind(lateness: Duration): String {
    val seconds = lateness.toMillis() / 1000.0
    return when {
        seconds < 60 -> String.format(Locale.ENGLISH, "%.1f s behind", seconds)
        seconds < 3600 -> "${lateness.toMinutes()} min behind"
        else -> "${lateness.toHours()} h ${lateness.toMinutesPart()} min behind"
    }
}
