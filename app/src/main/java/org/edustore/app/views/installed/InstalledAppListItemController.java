package org.edustore.app.views.installed;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.fdroid.database.AppPrefs;
import org.edustore.app.AppUpdateStatusManager;
import org.edustore.app.R;
import org.edustore.app.data.App;
import org.edustore.app.views.apps.AppListItemController;
import org.edustore.app.views.apps.AppListItemState;

/**
 * Shows the currently installed version name, and whether or not it is the recommended version.
 * Also shows whether the user has previously asked to ignore updates for this app entirely, or for
 * a specific version of this app.
 */
public class InstalledAppListItemController extends AppListItemController {
    public InstalledAppListItemController(AppCompatActivity activity, View itemView) {
        super(activity, itemView);
    }

    @NonNull
    @Override
    protected AppListItemState getCurrentViewState(
            @NonNull App app, @Nullable AppUpdateStatusManager.AppUpdateStatus appStatus) {
        return new AppListItemState(app)
                .setStatusText(getInstalledVersion(app))
                .setSecondaryStatusText(getIgnoreStatus(app));
    }

    /**
     * Either "Version X" or "Version Y (Recommended)", depending on the installed version.
     */
    private CharSequence getInstalledVersion(@NonNull App app) {
        int statusStringRes = (app.autoInstallVersionCode == app.installedVersionCode)
                ? R.string.app_recommended_version_installed
                : R.string.app_version_x_installed;

        return activity.getString(statusStringRes, app.installedVersionName);
    }

    /**
     * Show whether the user has ignored a specific version ("Updates ignored for Version X"), or
     * all versions ("Updates ignored").
     */
    @Nullable
    private CharSequence getIgnoreStatus(@NonNull App app) {
        AppPrefs prefs = app.prefs;
        if (prefs != null && prefs.shouldIgnoreUpdate(app.autoInstallVersionCode)) {
            return activity.getString(R.string.installed_app__updates_ignored);
        }
        return null;
    }
}
