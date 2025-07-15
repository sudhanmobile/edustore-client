package org.edustore.app.views.repos

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Alignment.Companion.End
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat.getDrawable
import androidx.core.os.LocaleListCompat
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import org.fdroid.database.MinimalApp
import org.fdroid.database.Repository
import org.edustore.app.FDroidApp
import org.edustore.app.R
import org.edustore.app.Utils
import org.edustore.app.Utils.getGlideModel
import org.edustore.app.compose.ComposeUtils.FDroidButton
import org.edustore.app.compose.colorAttribute
import org.edustore.app.ui.theme.FDroidContent
import org.fdroid.index.v2.FileV2
import org.fdroid.repo.FetchResult.IsExistingMirror
import org.fdroid.repo.FetchResult.IsExistingRepository
import org.fdroid.repo.FetchResult.IsNewMirror
import org.fdroid.repo.FetchResult.IsNewRepoAndNewMirror
import org.fdroid.repo.FetchResult.IsNewRepository
import org.fdroid.repo.Fetching

@Composable
fun RepoPreviewScreen(
    state: Fetching,
    modifier: Modifier = Modifier,
    onAddRepo: () -> Unit,
) {
    val localeList = LocaleListCompat.getDefault()
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        item {
            RepoPreviewHeader(state, onAddRepo, localeList)
        }
        if (state.fetchResult == null ||
            state.fetchResult is IsNewRepository ||
            state.fetchResult is IsNewRepoAndNewMirror
        ) {
            item {
                Row(
                    verticalAlignment = CenterVertically,
                    horizontalArrangement = spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.repo_preview_included_apps),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = state.apps.size.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (!state.done) LinearProgressIndicator(modifier = Modifier.weight(1f))
                }
            }
            items(items = state.apps, key = { it.packageName }) { app ->
                RepoPreviewApp(state.receivedRepo ?: error("no repo"), app, localeList)
            }
            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    }
}

@Composable
fun RepoPreviewHeader(
    state: Fetching,
    onAddRepo: () -> Unit,
    localeList: LocaleListCompat,
) {
    val repo = state.receivedRepo ?: error("repo was null")
    val isDevPreview = LocalInspectionMode.current
    val context = LocalContext.current

    val buttonText = when (state.fetchResult) {
        is IsNewRepository -> stringResource(R.string.repo_add_new_title)
        is IsNewRepoAndNewMirror -> stringResource(R.string.repo_add_repo_and_mirror)
        is IsNewMirror -> stringResource(R.string.repo_add_mirror)
        is IsExistingRepository, is IsExistingMirror -> stringResource(R.string.repo_view_repo)
        else -> error("Unexpected fetch state: ${state.fetchResult}")
    }
    val buttonAction: () -> Unit = when (val res = state.fetchResult) {
        is IsNewRepository, is IsNewRepoAndNewMirror, is IsNewMirror -> onAddRepo
        // unfortunately we need to duplicate these functions
        is IsExistingRepository -> {
            {
                val repoId = res.existingRepoId
                RepoDetailsActivity.launch(context, repoId)
            }
        }

        is IsExistingMirror -> {
            {
                val repoId = res.existingRepoId
                RepoDetailsActivity.launch(context, repoId)
            }
        }

        else -> error("Unexpected fetch state: ${state.fetchResult}")
    }

    val warningText: String? = when (state.fetchResult) {
        is IsNewRepository -> null
        is IsNewRepoAndNewMirror -> stringResource(
            R.string.repo_and_mirror_add_both_info,
            state.fetchUrl
        )

        is IsNewMirror -> stringResource(R.string.repo_mirror_add_info, state.fetchUrl)
        is IsExistingRepository -> stringResource(R.string.repo_exists)
        is IsExistingMirror -> stringResource(R.string.repo_mirror_exists, state.fetchUrl)
        else -> error("Unexpected fetch state: ${state.fetchResult}")
    }

    Column(
        verticalArrangement = spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = spacedBy(16.dp),
            verticalAlignment = CenterVertically,
        ) {
            RepoIcon(repo, Modifier.size(48.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = repo.getName(localeList) ?: "Unknown Repository",
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = repo.address.replaceFirst("https://", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Utils.formatLastUpdated(LocalContext.current.resources, repo.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (warningText != null) Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorAttribute(R.attr.warning)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier = Modifier
                    .padding(8.dp),
                text = warningText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = colorResource(android.R.color.white),
            )
        }

        FDroidButton(
            text = buttonText,
            onClick = buttonAction,
            modifier = Modifier.align(End),
        )

        val description = if (isDevPreview) {
            LoremIpsum(42).values.joinToString(" ")
        } else {
            repo.getDescription(localeList)
        }
        if (description != null) Text(
            // repos are still messing up their line breaks
            text = description.replace("\n", " "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
@OptIn(ExperimentalGlideComposeApi::class)
fun LazyItemScope.RepoPreviewApp(
    repo: Repository,
    app: MinimalApp,
    localeList: LocaleListCompat,
) {
    val isDevPreview = LocalInspectionMode.current
    ElevatedCard(
        modifier = Modifier
            .animateItem()
            .fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = spacedBy(8.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            if (isDevPreview) Image(
                painter = rememberDrawablePainter(
                    getDrawable(LocalContext.current.resources, R.drawable.ic_launcher, null)
                ),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            ) else GlideImage(
                model = getGlideModel(repo, app.getIcon(localeList)),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            ) {
                it.fallback(R.drawable.ic_repo_app_default).error(R.drawable.ic_repo_app_default)
            }
            Column {
                Text(
                    app.name ?: "Unknown app",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    app.summary ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Preview
@Composable
fun RepoPreviewScreenFetchingPreview() {
    val address = "https://example.org"
    val repo = FDroidApp.createSwapRepo(address, "foo bar")
    val app1 = object : MinimalApp {
        override val repoId = 0L
        override val packageName = "org.example"
        override val name: String = "App 1 with a long name"
        override val summary: String = "Summary of App1 which can also be a bit longer"
        override fun getIcon(localeList: LocaleListCompat): FileV2? = null
    }
    val app2 = object : MinimalApp {
        override val repoId = 0L
        override val packageName = "com.example"
        override val name: String = "App 2 with a name that is even longer than the first app"
        override val summary: String =
            "Summary of App2 which can also be a bit longer, even longer than other apps."

        override fun getIcon(localeList: LocaleListCompat): FileV2? = null
    }
    val app3 = object : MinimalApp {
        override val repoId = 0L
        override val packageName = "net.example"
        override val name: String = "App 3"
        override val summary: String = "short summary"

        override fun getIcon(localeList: LocaleListCompat): FileV2? = null
    }
    FDroidContent(pureBlack = true) {
        RepoPreviewScreen(
            Fetching(address, repo, listOf(app1, app2, app3), IsNewRepository)
        ) {}
    }
}

@Composable
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 720, heightDp = 360)
fun RepoPreviewScreenNewMirrorPreview() {
    val repo = FDroidApp.createSwapRepo("https://example.org", "foo bar")
    FDroidContent(pureBlack = true) {
        RepoPreviewScreen(
            Fetching("https://mirror.example.org", repo, emptyList(), IsNewMirror(0L))
        ) {}
    }
}

@Composable
@Preview
fun RepoPreviewScreenNewRepoAndNewMirrorPreview() {
    val repo = FDroidApp.createSwapRepo("https://example.org", "foo bar")
    FDroidContent(pureBlack = true) {
        RepoPreviewScreen(
            Fetching("https://mirror.example.org", repo, emptyList(), IsNewRepoAndNewMirror)
        ) {}
    }
}

@Preview
@Composable
fun RepoPreviewScreenExistingRepoPreview() {
    val address = "https://example.org"
    val repo = FDroidApp.createSwapRepo(address, "foo bar")
    FDroidContent(pureBlack = true) {
        RepoPreviewScreen(
            Fetching(address, repo, emptyList(), IsExistingRepository(0L))
        ) {}
    }
}

@Preview
@Composable
fun RepoPreviewScreenExistingMirrorPreview() {
    val repo = FDroidApp.createSwapRepo("https://example.org", "foo bar")
    FDroidContent(pureBlack = true) {
        RepoPreviewScreen(
            Fetching("https://mirror.example.org", repo, emptyList(), IsExistingMirror(0L))
        ) {}
    }
}
