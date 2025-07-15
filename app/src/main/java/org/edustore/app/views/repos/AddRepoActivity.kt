package org.edustore.app.views.repos

import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.guardianproject.netcipher.NetCipher
import kotlinx.coroutines.launch
import org.edustore.app.FDroidApp
import org.edustore.app.Preferences
import org.edustore.app.R
import org.edustore.app.nearby.SwapService
import org.edustore.app.ui.theme.FDroidContent
import org.edustore.app.views.apps.AppListActivity
import org.edustore.app.views.apps.AppListActivity.EXTRA_REPO_ID
import org.edustore.app.work.RepoUpdateWorker
import org.fdroid.index.RepoManager
import org.fdroid.repo.AddRepoError
import org.fdroid.repo.Added
import kotlin.text.RegexOption.IGNORE_CASE
import kotlin.text.RegexOption.MULTILINE

class AddRepoActivity : AppCompatActivity() {

    // Use a getter here, otherwise this tries to access Context too early causing NPE
    private val repoManager: RepoManager get() = FDroidApp.getRepoManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                repoManager.addRepoState.collect { state ->
                    if (state is Added) {
                        // update newly added repo
                        RepoUpdateWorker.updateNow(applicationContext, state.repo.repoId)
                        // show repo list and close this activity
                        val i = Intent(this@AddRepoActivity, AppListActivity::class.java).apply {
                            putExtra(EXTRA_REPO_ID, state.repo.repoId)
                        }
                        startActivity(i)
                        finish()
                    }
                }
            }
        }
        setContent {
            FDroidContent {
                val state = repoManager.addRepoState.collectAsState().value
                BackHandler(state is AddRepoError) {
                    // reset state when going back on error screen
                    repoManager.abortAddingRepository()
                }
                AddRepoIntroScreen(
                    state = state,
                    onFetchRepo = this::onFetchRepo,
                    onAddRepo = { repoManager.addFetchedRepository() },
                    onBackClicked = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
        addOnNewIntentListener { intent ->
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    intent.dataString?.let { uri ->
                        onFetchRepo(uri)
                    }
                }

                Intent.ACTION_SEND -> {
                    intent.getStringExtra(EXTRA_TEXT)?.let {
                        fetchIfRepoUri(it)
                    }
                }

                else -> {}
            }
        }
        intent?.let {
            onNewIntent(it)
            // avoid this intent getting re-processed
            it.setData(null)
            it.replaceExtras(Bundle())
        }
    }

    override fun onResume() {
        super.onResume()
        FDroidApp.checkStartTor(this, Preferences.get())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) repoManager.abortAddingRepository()
    }

    private fun onFetchRepo(uriStr: String) {
        val uri = Uri.parse(uriStr.trim())
        if (repoManager.isSwapUri(uri)) {
            val i = Intent(this, SwapService::class.java).apply {
                data = uri
            }
            ContextCompat.startForegroundService(this, i)
        } else {
            repoManager.abortAddingRepository()
            repoManager.fetchRepositoryPreview(uri.toString(), proxy = NetCipher.getProxy())
        }
    }

    private fun fetchIfRepoUri(str: String) {
        // try direct https/fdroidrepos URIs first
        val repoUriMatch = Regex(
            pattern = "^.*((https|fdroidrepos)://.+/repo(\\?fingerprint=[A-F0-9]+)?) ?.*$",
            options = setOf(IGNORE_CASE, MULTILINE),
        ).find(str)?.groups?.get(1)?.value
        if (repoUriMatch != null) {
            Log.d(this::class.simpleName, "Found match: $repoUriMatch")
            onFetchRepo(repoUriMatch)
            return // found, no need to continue
        }
        // now try fdroid.link URIs
        val repoLinkMatch = Regex(
            pattern = "^.*(https://fdroid.link/.+) ?.*$",
            options = setOf(IGNORE_CASE, MULTILINE),
        ).find(str)?.groups?.get(1)?.value
        if (repoLinkMatch != null) {
            Log.d(this::class.simpleName, "Found match: $repoLinkMatch")
            onFetchRepo(repoLinkMatch)
            return // found, no need to continue
        }
        // no URI found
        Toast.makeText(this, R.string.repo_share_not_found, Toast.LENGTH_LONG).show()
        finishAfterTransition()
    }
}
