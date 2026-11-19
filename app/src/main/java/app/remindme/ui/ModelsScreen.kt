package app.remindme.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.remindme.agent.ModelOffer
import app.remindme.agent.ModelOffers
import app.remindme.agent.Models
import app.remindme.wording.gigabytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the owner downloads, chooses and deletes the model the agent uses. Downloads go through Android's
 * download service over Wi-Fi only, so they carry on with this screen, or the app, closed.
 */
@Composable
fun ModelsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val models = remember { Models(context) }
    val scope = rememberCoroutineScope()
    // Bumped to read the files and downloads again.
    var looked by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf<Map<String, Models.Progress>>(emptyMap()) }
    var hub by remember { mutableStateOf<HubState>(HubState.NotAsked) }
    var deleting by remember { mutableStateOf<Models.Installed?>(null) }
    var cancelling by remember { mutableStateOf<String?>(null) }

    // While the screen is open, keep the progress bars current and put finished downloads into place.
    LaunchedEffect(Unit) {
        while (true) {
            progress = withContext(Dispatchers.IO) {
                models.finishDownloads()
                models.progress()
            }
            looked++
            delay(1_000)
        }
    }

    val installed = remember(looked) { models.installed() }
    val inUse = remember(looked) { models.inUse()?.name }
    val free = remember(looked) { models.freeBytes() }
    val onPhone = installed.map { it.file.name }.toSet()
    fun available(offers: List<ModelOffer>) = offers.filter { it.file !in onPhone && it.file !in progress.keys }

    MenuScreen("Models", onBack = onClose) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "The agent uses one model at a time. Changing it starts a new conversation; your tasks stay as they are. " +
                        "Downloads use Wi-Fi only and carry on with the app closed. ${gigabytes(free)} free.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { Heading("On this phone") }
            if (installed.isEmpty()) item { Note("No model yet. Download one below.") }
            items(installed, key = { "on-" + it.file.name }) { model ->
                InstalledRow(
                    model,
                    offer = offerFor(model.file.name, hub),
                    inUse = model.file.name == inUse,
                    onUse = { models.use(model.file.name); looked++ },
                    onDelete = { deleting = model },
                )
            }

            if (progress.isNotEmpty()) {
                item { Heading("Downloading") }
                items(progress.entries.toList(), key = { "dl-" + it.key }) { (file, state) ->
                    DownloadRow(
                        offerFor(file, hub)?.name ?: file,
                        state,
                        // A failed download is only removed from the list; a live one is asked about first.
                        onCancel = { if (state is Models.Progress.Failed) { models.cancel(file); looked++ } else cancelling = file },
                    )
                }
            }

            item { Heading("Known to work on this phone's runtime") }
            items(available(ModelOffers.BUILT_IN), key = { "built-" + it.file }) { offer ->
                OfferRow(offer, builtIn = true, free = free, onDownload = { models.download(offer); looked++ })
            }

            item { Heading("More on Hugging Face") }
            when (val state = hub) {
                HubState.NotAsked, is HubState.Failed -> item {
                    Column {
                        if (state is HubState.Failed) Note(state.reason)
                        OutlinedButton(onClick = {
                            hub = HubState.Looking
                            scope.launch {
                                hub = withContext(Dispatchers.IO) {
                                    runCatching { HubState.Found(ModelOffers.searchHub()) }
                                        .getOrElse { HubState.Failed("Could not reach Hugging Face: ${it.message}") }
                                }
                            }
                        }) { Text("Look on Hugging Face") }
                    }
                }
                HubState.Looking -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Looking…", modifier = Modifier.padding(start = 12.dp))
                    }
                }
                is HubState.Found -> {
                    val offers = available(state.offers)
                    if (offers.isEmpty()) item { Note("Nothing else passes the rule today.") }
                    items(offers, key = { "hub-" + it.repo + "/" + it.file }) { offer ->
                        OfferRow(offer, builtIn = false, free = free, onDownload = { models.download(offer); looked++ })
                    }
                }
            }
        }
    }

    cancelling?.let { file ->
        val name = offerFor(file, hub)?.name ?: file
        AlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text("Cancel the download of $name?") },
            text = { Text("What it has downloaded so far is thrown away.") },
            confirmButton = { TextButton(onClick = { models.cancel(file); cancelling = null; looked++ }) { Text("Cancel download") } },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep downloading") } },
        )
    }

    deleting?.let { model ->
        val name = offerFor(model.file.name, hub)?.name ?: model.file.name
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = {
                Text(
                    buildString {
                        append("It frees ${gigabytes(model.bytes)}. ")
                        if (!model.downloaded) append("It was copied over USB, so getting it back means copying it again or downloading it. ")
                        if (model.file.name == inUse) append("It is the model in use: the agent will have none until you choose another.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { models.delete(model.file.name); deleting = null; looked++ }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

private sealed interface HubState {
    data object NotAsked : HubState
    data object Looking : HubState
    data class Found(val offers: List<ModelOffer>) : HubState
    data class Failed(val reason: String) : HubState
}

/** What the app knows about a file on the phone, from the built-in list or the Hugging Face results. */
private fun offerFor(file: String, hub: HubState): ModelOffer? =
    ModelOffers.BUILT_IN.firstOrNull { it.file == file } ?: (hub as? HubState.Found)?.offers?.firstOrNull { it.file == file }

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ModelCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun InstalledRow(model: Models.Installed, offer: ModelOffer?, inUse: Boolean, onUse: () -> Unit, onDelete: () -> Unit) {
    ModelCard {
        Text(offer?.name ?: model.file.name, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(gigabytes(model.bytes), offer?.context?.let(::contextInWords), if (model.downloaded) "Downloaded" else "Copied over USB").joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (offer == null || !offer.tried) Note("Not tried with Remind Me: the agent may not understand its replies.")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            if (inUse) {
                Text("In use", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onUse) { Text("Use this model") }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun DownloadRow(name: String, state: Models.Progress, onCancel: () -> Unit) {
    ModelCard {
        Text(name, style = MaterialTheme.typography.titleMedium)
        val (done, total, words) = when (state) {
            is Models.Progress.Running -> Triple(state.done, state.total, "Downloading")
            is Models.Progress.Waiting -> Triple(state.done, state.total, state.reason)
            is Models.Progress.Failed -> Triple(0L, 0L, "Failed: ${state.reason}")
            Models.Progress.Finished -> Triple(1L, 1L, "Finishing")
        }
        if (total > 0) {
            LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        } else if (state !is Models.Progress.Failed) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                if (total > 0) "$words  ·  ${gigabytes(done)} of ${gigabytes(total)}" else words,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state is Models.Progress.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(if (state is Models.Progress.Failed) "Remove" else "Cancel") }
        }
    }
}

@Composable
private fun OfferRow(offer: ModelOffer, builtIn: Boolean, free: Long, onDownload: () -> Unit) {
    ModelCard {
        Text(offer.name, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(gigabytes(offer.bytes), offer.context?.let(::contextInWords) ?: "Context not stated", offer.licence).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            when {
                offer.tried -> "Tool calls: yes, and Remind Me reads them"
                builtIn -> "Tool calls: the model says yes  ·  Not tried with Remind Me"
                else -> "Tool calls: not checked  ·  Not tried with Remind Me"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (offer.tried) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
        val fits = offer.bytes < free
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Button(onClick = onDownload, enabled = fits) { Text("Download") }
            if (!fits) {
                Spacer(Modifier.width(12.dp))
                Text("Needs ${gigabytes(offer.bytes)}, ${gigabytes(free)} free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}


private fun contextInWords(tokens: Int): String = "${tokens / 1024}K context"
