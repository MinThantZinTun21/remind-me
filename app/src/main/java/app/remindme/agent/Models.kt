package app.remindme.agent

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * The model files on the phone: which one the agent uses, the ones downloading through Android's own
 * download service, and the ones copied over USB. See GLOSSARY.md.
 *
 * A download is written under a `.part` name and renamed to its `.litertlm` name only once complete, so a
 * half-finished file is never offered to the agent. The download service keeps going with the app closed,
 * waits while there is no Wi-Fi and carries on where it stopped. Its own notification is hidden: the app shows
 * a live one instead, from [ModelDownloadService].
 */
class Models(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("models", Context.MODE_PRIVATE)
    private val downloads = this.context.getSystemService(DownloadManager::class.java)

    /** The app's own folder on shared storage: where downloads land and where a USB copy goes. */
    val folder: File? get() = context.getExternalFilesDir(null)

    /** A finished model file on the phone. */
    data class Installed(val file: File, val downloaded: Boolean) {
        val bytes: Long get() = file.length()
    }

    /** The finished model files, newest first. */
    fun installed(): List<Installed> {
        val fromDownloads = prefs.getStringSet(DOWNLOADED, emptySet()).orEmpty()
        return folder?.listFiles { file -> file.isFile && file.name.endsWith(".litertlm") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { Installed(it, downloaded = it.name in fromDownloads) }
    }

    /**
     * The file the agent loads, or null when there is none to use. Until the owner chooses, that is the newest
     * file on the phone, as in agent build 1. Once they have chosen, it is that file only: after they delete it,
     * the agent has none until they choose another.
     */
    fun inUse(): File? {
        val chosen = prefs.getString(IN_USE, null)
        val files = installed()
        return if (chosen == null) files.firstOrNull()?.file else files.firstOrNull { it.file.name == chosen }?.file
    }

    fun use(fileName: String) = prefs.edit().putString(IN_USE, fileName).apply()

    /** Removes a finished file. When it was the one in use, the agent has no model until another is chosen. */
    fun delete(fileName: String): Boolean {
        val wasInUse = inUse()?.name == fileName
        val file = File(folder ?: return false, fileName)
        val gone = !file.exists() || file.delete()
        if (gone) {
            if (wasInUse) prefs.edit().putString(IN_USE, "").apply()
            prefs.edit().putStringSet(DOWNLOADED, prefs.getStringSet(DOWNLOADED, emptySet()).orEmpty() - fileName).apply()
        }
        return gone
    }

    /** Free space where models go, in bytes. */
    fun freeBytes(): Long = folder?.let { runCatching { StatFs(it.path).availableBytes }.getOrNull() } ?: 0L

    /** Hands [offer] to the download service, Wi-Fi only, and shows its progress in the notification bar. */
    fun download(offer: ModelOffer) {
        val folder = folder ?: return
        // A part left behind by an earlier download the service no longer knows would block the new one.
        File(folder, offer.file + PART).delete()
        val request = DownloadManager.Request(Uri.parse(offer.url))
            .setTitle(offer.name)
            .setDescription("Model for Remind Me")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalFilesDir(context, null, offer.file + PART)
        val id = downloads.enqueue(request)
        prefs.edit().putLong(KEY + offer.file, id).putString(NAME + offer.file, offer.name).apply()
        ModelDownloadService.start(context)
    }

    /** The model's name as the owner saw it when downloading, or the file name. */
    fun nameOf(fileName: String): String = prefs.getString(NAME + fileName, null) ?: fileName

    /** True while a download is fetching or waiting to carry on. */
    fun downloading(): Boolean = progress().values.any { it is Progress.Running || it is Progress.Waiting }

    /** Where a download stands. */
    sealed interface Progress {
        data class Running(val done: Long, val total: Long) : Progress
        data class Waiting(val done: Long, val total: Long, val reason: String) : Progress
        data class Failed(val reason: String) : Progress
        data object Finished : Progress
    }

    /** Every download the app has started that is not yet renamed into place, by file name. */
    fun progress(): Map<String, Progress> {
        val tracked = trackedDownloads()
        if (tracked.isEmpty()) return emptyMap()
        val found = mutableMapOf<String, Progress>()
        downloads.query(DownloadManager.Query().setFilterById(*tracked.values.toLongArray()))?.use { row ->
            while (row.moveToNext()) {
                val id = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val name = tracked.entries.firstOrNull { it.value == id }?.key ?: continue
                val done = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                found[name] = when (row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> Progress.Finished
                    DownloadManager.STATUS_FAILED -> Progress.Failed(failure(reason))
                    DownloadManager.STATUS_PAUSED -> Progress.Waiting(done, total, pause(reason))
                    DownloadManager.STATUS_PENDING -> Progress.Waiting(done, total, "Waiting to start")
                    else -> Progress.Running(done, total)
                }
            }
        }
        // The service forgot a download (the owner cleared it from the Downloads app): stop tracking it.
        for (name in tracked.keys - found.keys) {
            Log.w(TAG, "The download service no longer knows the download of $name")
            prefs.edit().remove(KEY + name).apply()
        }
        return found
    }

    /**
     * Renames every completed download into place, so the agent can use it, and returns the files it renamed.
     * Safe to call any time, from several places: a file is renamed, and returned, only once.
     */
    fun finishDownloads(): List<String> = synchronized(Models::class.java) { renameFinished() }

    private fun renameFinished(): List<String> {
        val folder = folder ?: return emptyList()
        val renamed = mutableListOf<String>()
        for ((name, state) in progress()) {
            if (state != Progress.Finished) continue
            val part = File(folder, name + PART)
            val target = File(folder, name)
            if (part.exists() && (target.exists() && target.delete() || !target.exists()) && part.renameTo(target)) {
                prefs.edit()
                    .remove(KEY + name)
                    .putStringSet(DOWNLOADED, prefs.getStringSet(DOWNLOADED, emptySet()).orEmpty() + name)
                    .apply()
                renamed += name
            }
        }
        return renamed
    }

    /** Stops a download and throws away what it had fetched. Logged, so a download that vanished can be explained. */
    fun cancel(fileName: String) {
        Log.i(TAG, "Download of $fileName cancelled by the owner")
        trackedDownloads()[fileName]?.let { downloads.remove(it) }
        prefs.edit().remove(KEY + fileName).apply()
        folder?.let { File(it, fileName + PART).delete() }
    }

    private fun trackedDownloads(): Map<String, Long> =
        prefs.all.filterKeys { it.startsWith(KEY) }.mapNotNull { (key, value) -> (value as? Long)?.let { key.removePrefix(KEY) to it } }.toMap()

    private fun pause(reason: Int) = when (reason) {
        DownloadManager.PAUSED_QUEUED_FOR_WIFI, DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for Wi-Fi"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "Connection dropped, trying again"
        else -> "Paused"
    }

    private fun failure(reason: Int) = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_TOO_MANY_REDIRECTS, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Hugging Face did not send the file"
        DownloadManager.ERROR_CANNOT_RESUME -> "Could not carry on where it stopped"
        in 400..599 -> "Hugging Face answered $reason"
        else -> "It stopped (reason $reason)"
    }

    /** The download service says a download ended: put a completed one into place, even with the app closed. */
    class DownloadDone : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            Thread {
                try {
                    val models = Models(context)
                    for (file in models.finishDownloads()) DownloadNotices.ready(context, models.nameOf(file))
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }

    companion object {
        private const val TAG = "Models"
        private const val IN_USE = "in_use"
        private const val DOWNLOADED = "downloaded"
        private const val KEY = "download:"
        private const val NAME = "name:"
        private const val PART = ".part"
    }
}
