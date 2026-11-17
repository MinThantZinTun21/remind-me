package app.remindme.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.remindme.RemindMeApp
import app.remindme.core.HISTORY_KEPT
import app.remindme.wording.Times

/** The one-time tasks still in history. "Not done" puts one back on the list. */
@Composable
fun HistoryScreen(app: RemindMeApp, onClose: () -> Unit) {
    val context = LocalContext.current
    val done = remember(app.revision.intValue) { app.tools.history() }

    MenuScreen("History", onBack = onClose) { padding ->
        if (done.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Nothing here. A task you mark done stays here for ${HISTORY_KEPT.toDays()} days.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(done, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.title) },
                        supportingContent = {
                            Text(
                                "Due ${Times.dateAndTime(context, task.due)}\n" +
                                    "Done ${task.doneAt?.let { Times.dateAndTime(context, it) }}",
                            )
                        },
                        trailingContent = { TextButton(onClick = { app.tools.notDone(task.id) }) { Text("Not done") } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
