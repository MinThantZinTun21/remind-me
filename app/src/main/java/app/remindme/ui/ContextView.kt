package app.remindme.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.remindme.agent.ContextRecord
import java.text.NumberFormat
import java.util.Locale

/**
 * What the model read and wrote for one message: a collapsed line under the exchange that opens to Input,
 * each part of the context with its size and full text, and Output, the raw reply. A long press copies a block.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContextView(record: ContextRecord) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            summary(record) + if (open) "  ▴" else "  ▾",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .combinedClickable(onClick = { open = !open }, onLongClick = { copy(context, summary(record)) })
                .padding(vertical = 6.dp),
        )
        if (!open) return@Column
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading("Input")
            for (part in record.parts) Block(part.name, "≈ ${count(part.tokens)}", part.text)
            record.leftOut?.let {
                Text("Left out: $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
            }
            Heading("Output")
            Block("Reply", "${count(record.written)} written", record.output.ifEmpty { "(no reply)" })
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
}

/** One part: its name and size; tapping opens its full text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Block(name: String, size: String, text: String) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .combinedClickable(onClick = { open = !open }, onLongClick = { copy(context, text) })
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(name, style = MaterialTheme.typography.bodyMedium)
                Text(size, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (open) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurfaceVariant,
                    softWrap = false,
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** "Read 1,412 / 2,048 tokens · wrote 96". */
private fun summary(record: ContextRecord): String {
    val read = if (record.read >= 0) count(record.read) else "≈ " + count(record.parts.sumOf { it.tokens })
    return "Read $read / ${count(record.size)} tokens · wrote ${count(record.written)}"
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.ENGLISH).format(n)

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Remind Me context", text))
    // Android 13 and later show their own notice when an app copies.
    if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
