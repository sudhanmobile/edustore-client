package org.edustore.app

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import mu.KotlinLogging
import org.acra.util.versionCodeLong
import org.fdroid.database.DbUpdateChecker
import org.fdroid.database.Repository
import org.fdroid.database.UpdatableApp
import org.fdroid.download.DownloaderFactory
import org.edustore.app.AppUpdateStatusManager.Status.Downloading
import org.edustore.app.data.Apk
import org.edustore.app.data.App
import org.edustore.app.installer.ApkCache
import org.edustore.app.installer.InstallManagerService
import org.edustore.app.installer.InstallerFactory
import org.fdroid.index.RepoManager

class AppUpdateManager @JvmOverloads constructor(
    private val context: Context,
    private val repoManager: RepoManager,
    private val updateChecker: DbUpdateChecker,
    private val downloaderFactory: DownloaderFactory =
        org.edustore.app.net.DownloaderFactory.INSTANCE,
    private val statusManager: AppUpdateStatusManager = AppUpdateStatusManager.getInstance(context),
) {

    private val log = KotlinLogging.logger { }

    fun updateApps() {
        // get apps with updates pending
        val releaseChannels = Preferences.get().backendReleaseChannels
        val updatableApps = updateChecker.getUpdatableApps(releaseChannels, true)
            .sortedWith { app1, app2 ->
                // our own app will be last to update
                if (app1.packageName == context.packageName) return@sortedWith 1
                if (app2.packageName == context.packageName) return@sortedWith -1
                // other apps are sorted by name
                (app1.name ?: "").compareTo(app2.name ?: "", ignoreCase = true)
            }
        // inform the status manager of the available updates
        statusManager.addUpdatableApps(updatableApps, false)
        // update each individual app
        updatableApps.forEach { app ->
            val repo = repoManager.getRepository(app.repoId)
                ?: return // repo removed in the meantime?
            val listener = object : AppInstallListener {
                private val installManagerService = InstallManagerService.getInstance(context)
                private val legacyApp = App(app)
                private val legacyApk = Apk(app.update, repo)
                private val uri = legacyApk.canonicalUrl.toUri()
                private var lastProgress = 0L

                override fun onInstallProcessStarted() {
                    statusManager.addApk(legacyApp, legacyApk, Downloading, null)
                }

                override fun onDownloadProgress(downloadedBytes: Long, totalBytes: Long) {
                    val now = System.currentTimeMillis()
                    if (now - lastProgress > 1000) {
                        installManagerService.onDownloadProgress(
                            uri, legacyApp, legacyApk, downloadedBytes, totalBytes
                        )
                        lastProgress = now
                    }
                }

                override fun onDownloadFailed(e: Exception) {
                    installManagerService.onDownloadFailed(uri, e.message)
                }

                override fun onReadyToInstall() {
                    installManagerService.onDownloadComplete(uri)
                }
            }
            updateApp(app, repo, listener)
        }
    }

    private fun updateApp(app: UpdatableApp, repo: Repository, listener: AppInstallListener?) {
        listener?.onInstallProcessStarted()
        // legacy cruft
        val legacyApp = App(app)
        val legacyApk = Apk(app.update, repo)
        val uri = legacyApk.canonicalUrl.toUri()
        // check if app was already installed in the meantime
        try {
            val packageInfo = context.packageManager.getPackageInfo(app.packageName, 0)
            // bail out if app update was already installed
            if (packageInfo.versionCodeLong >= app.update.manifest.versionCode) return
        } catch (e: Exception) {
            log.error(e) { "Error getting package info for ${app.packageName}" }
        }
        // download file
        val file = ApkCache.getApkDownloadPath(context, uri)
        val downloader = downloaderFactory.create(repo, uri, app.update.file, file)
        downloader.setListener { bytesRead, totalBytes ->
            log.info { "${app.name} (${app.packageName}) $bytesRead/$totalBytes" }
            listener?.onDownloadProgress(bytesRead, totalBytes)
        }
        try {
            downloader.download()
        } catch (e: Exception) {
            log.error(e) { "Error downloading $uri" }
            listener?.onDownloadFailed(e)
            return
        }
        listener?.onReadyToInstall()
        // install file
        log.info { "Download of ${app.name} (${app.packageName}) complete, installing..." }
        val installer = InstallerFactory.create(context, legacyApp, legacyApk)
        installer.installPackage(Uri.fromFile(file), uri)
    }

}

interface AppInstallListener {
    fun onInstallProcessStarted()
    fun onDownloadProgress(downloadedBytes: Long, totalBytes: Long)
    fun onDownloadFailed(e: Exception)
    fun onReadyToInstall()
}
