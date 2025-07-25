package org.edustore.app.views.main;

import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.fdroid.database.AppOverviewItem;
import org.fdroid.database.FDroidDatabase;
import org.fdroid.database.Repository;
import org.edustore.app.FDroidApp;
import org.edustore.app.Preferences;
import org.edustore.app.Preferences.ChangeListener;
import org.edustore.app.R;
import org.edustore.app.Utils;
import org.edustore.app.data.DBHelper;
import org.edustore.app.panic.HidingManager;
import org.edustore.app.views.apps.AppListActivity;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Loads a list of newly added or recently updated apps and displays them to the user.
 */
class LatestViewBinder implements Observer<List<AppOverviewItem>>, ChangeListener {

    private final LatestAdapter latestAdapter;
    private final AppCompatActivity activity;
    private final CircularProgressIndicator progressBar;
    private final TextView emptyState;
    private final RecyclerView appList;
    private final FDroidDatabase db;

    LatestViewBinder(final AppCompatActivity activity, FrameLayout parent) {
        this.activity = activity;
        activity.getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onCreate(@NonNull LifecycleOwner owner) {
                Preferences.get().registerAppsRequiringAntiFeaturesChangeListener(LatestViewBinder.this);
                Preferences.get().registerShowIncompatibleListener(LatestViewBinder.this);
            }

            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                Preferences.get().unregisterAppsRequiringAntiFeaturesChangeListener(LatestViewBinder.this);
                Preferences.get().unregisterShowIncompatibleListener(LatestViewBinder.this);
            }
        });
        db = DBHelper.getDb(activity);
        Transformations.distinctUntilChanged(db.getAppDao().getAppOverviewItems(200)).observe(activity, this);

        View latestView = activity.getLayoutInflater().inflate(R.layout.main_tab_latest, parent, true);

        latestAdapter = new LatestAdapter(activity);

        GridLayoutManager layoutManager = new GridLayoutManager(activity, 2);
        layoutManager.setSpanSizeLookup(latestAdapter.getSpanSizeLookup());

        emptyState = latestView.findViewById(R.id.empty_state);
        progressBar = latestView.findViewById(R.id.progress_bar);

        appList = latestView.findViewById(R.id.app_list);
        appList.setHasFixedSize(true);
        appList.setLayoutManager(layoutManager);
        appList.setAdapter(latestAdapter);

        final SwipeRefreshLayout swipeToRefresh = latestView
                .findViewById(R.id.swipe_to_refresh);
        Utils.applySwipeLayoutColors(swipeToRefresh);
        swipeToRefresh.setOnRefreshListener(() -> {
            swipeToRefresh.setRefreshing(false);
            Utils.runOffUiThread(() -> FDroidApp.getRepoUpdateManager(activity).updateRepos());
        });

        FloatingActionButton searchFab = latestView.findViewById(R.id.fab_search);
        searchFab.setOnClickListener(v -> activity.startActivity(new Intent(activity, AppListActivity.class)));
        searchFab.setOnLongClickListener(view -> {
            if (Preferences.get().hideOnLongPressSearch()) {
                HidingManager.showHideDialog(activity);
                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    public void onChanged(List<AppOverviewItem> items) {
        // filter out anti-features first
        filterApps(items);
        latestAdapter.setApps(items);

        if (latestAdapter.getItemCount() == 0) {
            emptyState.setVisibility(View.VISIBLE);
            appList.setVisibility(View.GONE);
            explainEmptyStateToUser();
        } else {
            progressBar.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            appList.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPreferenceChange() {
        // reload and re-filter apps from DB when anti-feature settings change
        LiveData<List<AppOverviewItem>> liveData = db.getAppDao().getAppOverviewItems(200);
        liveData.observe(activity, new Observer<>() {
            @Override
            public void onChanged(List<AppOverviewItem> items) {
                LatestViewBinder.this.onChanged(items);
                liveData.removeObserver(this);
            }
        });
    }

    private void filterApps(List<AppOverviewItem> items) {
        List<String> antiFeatures = Arrays.asList(activity.getResources().getStringArray(R.array.antifeaturesValues));
        Set<String> shownAntiFeatures = Preferences.get().showAppsWithAntiFeatures();
        String otherAntiFeatures = activity.getResources().getString(R.string.antiothers_key);
        boolean showOtherAntiFeatures = shownAntiFeatures.contains(otherAntiFeatures);
        boolean hideIncompatibleVersions = !Preferences.get().showIncompatibleVersions();
        Iterator<AppOverviewItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            AppOverviewItem item = iterator.next();
            if (isFilteredByAntiFeature(item, antiFeatures, shownAntiFeatures, showOtherAntiFeatures)) {
                iterator.remove();
            } else if (hideIncompatibleVersions && !item.isCompatible()) {
                iterator.remove();
            }
        }
    }

    private boolean isFilteredByAntiFeature(AppOverviewItem item, List<String> antiFeatures,
                                            Set<String> showAntiFeatures, boolean showOther) {
        for (String antiFeature : item.getAntiFeatureKeys()) {
            // is it part of the known anti-features?
            if (antiFeatures.contains(antiFeature)) {
                // it gets filtered not part of the ones that we show
                if (!showAntiFeatures.contains(antiFeature)) return true;
            } else if (!showOther) {
                // gets filtered if we should no show unknown anti-features
                return true;
            }
        }
        return false;
    }

    private void explainEmptyStateToUser() {
        if (Preferences.get().isIndexNeverUpdated() &&
                FDroidApp.getRepoUpdateManager(activity).isUpdating().getValue()) {
            progressBar.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            appList.setVisibility(View.GONE);
            return;
        }
        progressBar.setVisibility(View.GONE);

        StringBuilder emptyStateText = new StringBuilder();
        emptyStateText.append(activity.getString(R.string.latest__empty_state__no_recent_apps));
        emptyStateText.append("\n\n");

        int repoCount = 0;
        Long lastUpdate = null;
        for (Repository repo : FDroidApp.getRepoManager(activity).getRepositories()) {
            if (repo.getEnabled()) {
                repoCount++;
                if (lastUpdate == null && repo.getLastUpdated() != null) {
                    lastUpdate = repo.getLastUpdated();
                } else if (lastUpdate != null && repo.getLastUpdated() != null
                        && repo.getLastUpdated() > lastUpdate) {
                    lastUpdate = repo.getLastUpdated();
                }
            }
        }
        if (repoCount == 0) {
            emptyStateText.append(activity.getString(R.string.latest__empty_state__no_enabled_repos));
        } else {
            if (lastUpdate == null) {
                emptyStateText.append(activity.getString(R.string.latest__empty_state__never_updated));
            } else {
                emptyStateText.append(Utils.formatLastUpdated(activity.getResources(), lastUpdate));
            }
        }

        emptyState.setText(emptyStateText.toString());
    }
}
