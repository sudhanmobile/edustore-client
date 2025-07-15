package org.edustore.app.views.updates;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hannesdorfmann.adapterdelegates4.AdapterDelegatesManager;

import org.fdroid.database.DbUpdateChecker;
import org.fdroid.database.FDroidDatabase;
import org.fdroid.database.Repository;
import org.fdroid.database.UpdatableApp;
import org.edustore.app.AppUpdateStatusManager;
import org.edustore.app.FDroidApp;
import org.edustore.app.Preferences;
import org.edustore.app.Utils;
import org.edustore.app.data.Apk;
import org.edustore.app.data.App;
import org.edustore.app.data.DBHelper;
import org.edustore.app.views.updates.items.AppStatus;
import org.edustore.app.views.updates.items.AppUpdateData;
import org.edustore.app.views.updates.items.KnownVulnApp;
import org.edustore.app.views.updates.items.UpdateableApp;
import org.edustore.app.views.updates.items.UpdateableAppsHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.reactivex.rxjava3.disposables.Disposable;

/**
 * Manages the following types of information:
 * <ul>
 * <li>Apps marked for downloading (while the user is offline)</li>
 * <li>Currently downloading apps</li>
 * <li>Apps which have been downloaded (and need further action to install)</li>
 * </ul>
 * This includes new installs and updates.
 * <ul>
 * <li>Reminders to users that they can donate to apps (only shown infrequently after several updates)</li>
 * <li>A list of apps which are eligible to be updated (for when the "Automatic Updates" option is disabled),
 * including:
 * + A summary of all apps to update including an "Update all" button and a "Show apps" button.
 * + Once "Show apps" is expanded then each app is shown along with its own download button.</li>
 * </ul>
 * It does this by maintaining several different lists of interesting apps. Each list contains wrappers
 * around the piece of data it wants to render ({@link AppStatus}, {@link UpdateableApp}).
 * Instead of juggling the various viewTypes
 * to find out which position in the adapter corresponds to which view type, this is handled by
 * the {@link UpdatesAdapter#delegatesManager}.
 * <p>
 * There are a series of type-safe lists which hold the specific data this adapter is interested in.
 * This data is then collated into a single list (see {@link UpdatesAdapter#populateItems()}) which
 * is the actual thing the adapter binds too. At any point it is safe to clear the single list and
 * repopulate it from the original source lists of data. When this is done, the adapter will notify
 * the recycler view that its data has changed. Sometimes it will also ask the recycler view to
 * scroll to the newly added item (if attached to the recycler view).
 */
public class UpdatesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final AdapterDelegatesManager<List<AppUpdateData>> delegatesManager = new AdapterDelegatesManager<>();

    private final AppCompatActivity activity;
    private final DbUpdateChecker updateChecker;
    private final SharedPreferences preferences;

    private final List<AppUpdateData> items = new ArrayList<>();
    private final List<AppStatus> appsToShowStatus = new ArrayList<>();
    private final List<UpdateableApp> updateableApps = new ArrayList<>();
    private final List<KnownVulnApp> knownVulnApps = new ArrayList<>();

    private static final String PREF_SHOW_ALL_UPDATEABLE_APPS = "showAllUpdateableApps";
    private static final boolean DEFAULT_SHOW_ALL_UPDATEABLE_APPS = false;

    private boolean showAllUpdateableApps;

    @Nullable
    private Disposable disposable;

    UpdatesAdapter(AppCompatActivity activity) {
        this.activity = activity;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(activity);

        showAllUpdateableApps = preferences.getBoolean(
                PREF_SHOW_ALL_UPDATEABLE_APPS,
                DEFAULT_SHOW_ALL_UPDATEABLE_APPS);

        delegatesManager.addDelegate(new AppStatus.Delegate(activity))
                .addDelegate(new UpdateableApp.Delegate(activity))
                .addDelegate(new UpdateableAppsHeader.Delegate(activity))
                .addDelegate(new KnownVulnApp.Delegate(activity, this::loadUpdatableApps));

        FDroidDatabase db = DBHelper.getDb(activity);
        updateChecker = new DbUpdateChecker(db, activity.getPackageManager());
        loadUpdatableApps();
    }

    private void loadUpdatableApps() {
        List<String> releaseChannels = Preferences.get().getBackendReleaseChannels();
        if (disposable != null) disposable.dispose();
        disposable = Utils.runOffUiThread(() -> updateChecker.getUpdatableApps(releaseChannels, true, true),
                this::onCanUpdateLoadFinished);
    }

    public boolean canViewAllUpdateableApps() {
        return showAllUpdateableApps;
    }

    public void toggleAllUpdateableApps() {
        showAllUpdateableApps = !showAllUpdateableApps;
        preferences.edit().putBoolean(PREF_SHOW_ALL_UPDATEABLE_APPS, showAllUpdateableApps).apply();
        refreshItems();
    }

    /**
     * There are some statuses managed by {@link AppUpdateStatusManager} which we don't care about
     * for the "Updates" view. For example Also, although this
     * adapter does know about apps with updates available, it does so by querying the database not
     * by querying the app update status manager. As such, apps with the status
     * {@link org.edustore.app.AppUpdateStatusManager.Status#UpdateAvailable} are not interesting here.
     */
    private boolean shouldShowStatus(AppUpdateStatusManager.AppUpdateStatus status) {
        return status.status == AppUpdateStatusManager.Status.PendingInstall ||
                status.status == AppUpdateStatusManager.Status.Downloading ||
                status.status == AppUpdateStatusManager.Status.Installing ||
                status.status == AppUpdateStatusManager.Status.Installed ||
                status.status == AppUpdateStatusManager.Status.ReadyToInstall;
    }

    private void onCanUpdateLoadFinished(List<UpdatableApp> apps) {
        updateableApps.clear();
        knownVulnApps.clear();

        for (UpdatableApp updatableApp : apps) {
            App app = new App(updatableApp);
            Repository repo = FDroidApp.getRepoManager(activity).getRepository(updatableApp.getUpdate().getRepoId());
            Apk apk = new Apk(updatableApp.getUpdate(), repo);
            if (updatableApp.getHasKnownVulnerability()) {
                app.installedApk = apk;
                knownVulnApps.add(new KnownVulnApp(activity, app, apk));
            } else {
                updateableApps.add(new UpdateableApp(activity, app, apk));
            }
        }
        // sort update items by name
        Collections.sort(updateableApps, (app1, app2) -> {
            String name1 = app1.app.name.toLowerCase(Locale.getDefault());
            String name2 = app2.app.name.toLowerCase(Locale.getDefault());
            return name1.compareTo(name2);
        });
        refreshItems();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refreshItems() {
        populateAppStatuses();
        populateItems();
        notifyDataSetChanged();
    }

    /**
     * Adds items from the {@link AppUpdateStatusManager} to {@link UpdatesAdapter#appsToShowStatus}.
     */
    private void populateAppStatuses() {
        appsToShowStatus.clear();
        for (AppUpdateStatusManager.AppUpdateStatus status : AppUpdateStatusManager.getInstance(activity).getAll()) {
            if (shouldShowStatus(status)) {
                appsToShowStatus.add(new AppStatus(activity, status));
            }
        }
        // sort app status by name
        Collections.sort(appsToShowStatus, (app1, app2) -> {
            String name1 = app1.status.app.name.toLowerCase(Locale.getDefault());
            String name2 = app2.status.app.name.toLowerCase(Locale.getDefault());
            return name1.compareTo(name2);
        });
    }

    /**
     * Completely rebuilds the underlying data structure used by this adapter.
     */
    private void populateItems() {
        items.clear();

        Set<String> toShowStatusUrls = new HashSet<>(appsToShowStatus.size());
        for (AppStatus app : appsToShowStatus) {
            // Show status
            items.add(app);
            toShowStatusUrls.add(app.status.getCanonicalUrl());
        }
        // Show apps that are in state UpdateAvailable below the statuses
        List<UpdateableApp> updateableAppsToShow = new ArrayList<>(updateableApps.size());
        for (UpdateableApp app : updateableApps) {
            if (!toShowStatusUrls.contains(app.apk.getCanonicalUrl())) {
                updateableAppsToShow.add(app);
            }
        }
        if (updateableAppsToShow.size() > 0) {
            // show header, if there's apps to update
            items.add(new UpdateableAppsHeader(activity, this, updateableAppsToShow));
            // show all items, if "Show All" was clicked
            if (showAllUpdateableApps) {
                items.addAll(updateableAppsToShow);
            }
        }
        // add vulnerable apps at the bottom
        items.addAll(knownVulnApps);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return delegatesManager.getItemViewType(items, position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return delegatesManager.onCreateViewHolder(parent, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        delegatesManager.onBindViewHolder(items, position, holder);
    }

    /**
     * If this adapter is "active" then it is part of the current UI that the user is looking to.
     * Under those circumstances, we want to make sure it is up to date, and also listen to the
     * correct set of broadcasts.
     */
    void setIsActive() {
        loadUpdatableApps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_ADDED);
        filter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_REMOVED);
        filter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_CHANGED);
        filter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_LIST_CHANGED);

        LocalBroadcastManager.getInstance(activity).registerReceiver(receiverAppStatusChanges, filter);
    }

    void stopListeningForStatusUpdates() {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(receiverAppStatusChanges);
    }

    private void onManyAppStatusesChanged(String reasonForChange) {
        switch (reasonForChange) {
            case AppUpdateStatusManager.REASON_UPDATES_AVAILABLE:
                onUpdateableAppsChanged();
                break;

            case AppUpdateStatusManager.REASON_READY_TO_INSTALL:
                onFoundAppsReadyToInstall();
                break;
        }
    }

    /**
     * Apps have been made available for update which were not available for update before.
     * We need to rerun our database query to get a list of apps to update.
     */
    private void onUpdateableAppsChanged() {
        loadUpdatableApps();
    }

    /**
     * We have completed a scan of .apk files in the cache, and identified there are
     * some which are ready to install.
     */
    private void onFoundAppsReadyToInstall() {
        refreshItems();
    }

    private void onAppStatusAdded() {
        refreshItems();
    }

    private void onAppStatusRemoved() {
        loadUpdatableApps();
    }

    private final BroadcastReceiver receiverAppStatusChanges = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            switch (intent.getAction()) {
                case AppUpdateStatusManager.BROADCAST_APPSTATUS_CHANGED:
                    if (intent.getBooleanExtra(AppUpdateStatusManager.EXTRA_IS_STATUS_UPDATE, false)) {
                        refreshItems();
                    }
                    break;

                case AppUpdateStatusManager.BROADCAST_APPSTATUS_LIST_CHANGED:
                    onManyAppStatusesChanged(intent.getStringExtra(AppUpdateStatusManager.EXTRA_REASON_FOR_CHANGE));
                    break;

                case AppUpdateStatusManager.BROADCAST_APPSTATUS_ADDED:
                    onAppStatusAdded();
                    break;

                case AppUpdateStatusManager.BROADCAST_APPSTATUS_REMOVED:
                    onAppStatusRemoved();
                    break;
            }
        }
    };

}
