package org.edustore.app.views.updates.items;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import org.edustore.app.AppUpdateStatusManager;
import org.edustore.app.AppUpdateStatusManager.AppUpdateStatus;
import org.edustore.app.R;
import org.edustore.app.data.App;
import org.edustore.app.views.apps.AppListItemController;
import org.edustore.app.views.apps.AppListItemState;
import org.edustore.app.views.updates.UpdatesAdapter;

/**
 * Shows apps which are:
 * * In the process of being downloaded.
 * * Downloaded and ready to install.
 * * Recently installed and ready to run.
 */
public class AppStatusListItemController extends AppListItemController {
    AppStatusListItemController(AppCompatActivity activity, View itemView) {
        super(activity, itemView);
    }

    @NonNull
    @Override
    protected AppListItemState getCurrentViewState(@NonNull App app, @Nullable AppUpdateStatus appStatus) {

        return super.getCurrentViewState(app, appStatus)
                .setStatusText(getStatusText(appStatus));
    }

    @Nullable
    private CharSequence getStatusText(@Nullable AppUpdateStatus appStatus) {
        if (appStatus != null) {
            switch (appStatus.status) {
                case ReadyToInstall:
                    return activity.getString(R.string.app_list_download_ready);

                case Installed:
                    return activity.getString(R.string.notification_content_single_installed);
            }
        }

        return null;
    }

    @Override
    public boolean canDismiss() {
        return true;
    }

    @Override
    protected void onDismissApp(@NonNull final App app, final UpdatesAdapter adapter) {
        AppUpdateStatus status = getCurrentStatus();
        if (status != null) {
            final AppUpdateStatusManager manager = AppUpdateStatusManager.getInstance(activity);
            final AppUpdateStatus appUpdateStatus = manager.get(status.getCanonicalUrl());
            manager.removeApk(status.getCanonicalUrl());

            switch (status.status) {
                case Downloading:
                    cancelDownload();
                    Snackbar.make(itemView, R.string.app_list__dismiss_downloading_app, Snackbar.LENGTH_SHORT).show();
                    break;

                case ReadyToInstall:
                    if (appUpdateStatus != null) {
                        Snackbar.make(
                                itemView,
                                R.string.app_list__dismiss_installing_app,
                                Snackbar.LENGTH_LONG
                        ).setAction(R.string.undo, view -> {
                            manager.addApk(appUpdateStatus.app, appUpdateStatus.apk, appUpdateStatus.status,
                                    appUpdateStatus.intent);
                            adapter.refreshItems();
                        }).show();
                        break;
                    }
            }
        }

        adapter.refreshItems();
    }
}
