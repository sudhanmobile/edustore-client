package org.edustore.app.views.updates.items;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.hannesdorfmann.adapterdelegates4.AdapterDelegate;

import org.edustore.app.R;
import org.edustore.app.data.Apk;
import org.edustore.app.data.App;

import java.util.List;

/**
 * List of all apps which can be updated, but have not yet been downloaded.
 *
 * @see KnownVulnApp The data that is bound to this view.
 * @see R.layout#known_vuln_app_list_item The view that this binds to.
 * @see KnownVulnAppListItemController Used for binding the {@link App} to
 * the {@link R.layout#known_vuln_app_list_item}
 */
public class KnownVulnApp extends AppUpdateData {

    public final App app;
    public final Apk apk;

    public KnownVulnApp(AppCompatActivity activity, App app, Apk apk) {
        super(activity);
        this.app = app;
        this.apk = apk;
    }

    public static class Delegate extends AdapterDelegate<List<AppUpdateData>> {

        private final AppCompatActivity activity;
        private final Runnable refreshApps;

        public Delegate(AppCompatActivity activity, Runnable refreshApps) {
            this.activity = activity;
            this.refreshApps = refreshApps;
        }

        @Override
        protected boolean isForViewType(@NonNull List<AppUpdateData> items, int position) {
            return items.get(position) instanceof KnownVulnApp;
        }

        @NonNull
        @Override
        protected RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
            return new KnownVulnAppListItemController(activity, refreshApps, activity.getLayoutInflater()
                    .inflate(R.layout.known_vuln_app_list_item, parent, false));
        }

        @Override
        protected void onBindViewHolder(@NonNull List<AppUpdateData> items, int position,
                                        @NonNull RecyclerView.ViewHolder holder, @NonNull List<Object> payloads) {
            KnownVulnApp app = (KnownVulnApp) items.get(position);
            ((KnownVulnAppListItemController) holder).bindModel(app.app, app.apk, null);
        }
    }
}
