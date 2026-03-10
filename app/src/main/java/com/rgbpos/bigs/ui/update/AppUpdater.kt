package com.rgbpos.bigs.ui.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.rgbpos.bigs.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL

@Serializable
data class VersionInfo(
    val version_code: Int,
    val version_name: String,
    val apk_url: String,
    val changelog: String = "",
)

object AppUpdater {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Check if a newer version is available.
     * Returns VersionInfo if update available, null otherwise.
     */
    suspend fun checkForUpdate(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val response = URL(BuildConfig.UPDATE_URL).readText()
            val info = json.decodeFromString(VersionInfo.serializer(), response)
            if (info.version_code > BuildConfig.VERSION_CODE) info else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Download APK using DownloadManager and trigger install when complete.
     */
    fun downloadAndInstall(context: Context, info: VersionInfo) {
        val apkName = "bigs-pos-${info.version_name}.apk"

        // Delete old APK if exists
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, apkName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(info.apk_url))
            .setTitle("Big's POS Update v${info.version_name}")
            .setDescription("Downloading update...")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, apkName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Listen for download complete
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    installApk(ctx, file)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
