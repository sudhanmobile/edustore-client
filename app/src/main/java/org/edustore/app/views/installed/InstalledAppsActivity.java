/*
 * Copyright (C) 2010-12  Ciaran Gultnieks, ciaran@ciarang.com
 * Copyright (C) 2009  Roberto Jacinto, roberto.jacinto@caixamagica.pt
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.edustore.app.views.installed;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ShareCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.fdroid.database.AppListItem;
import org.fdroid.database.AppPrefsDao;
import org.fdroid.database.FDroidDatabase;
import org.edustore.app.FDroidApp;
import org.edustore.app.R;
import org.edustore.app.UiUtils;
import org.edustore.app.Utils;
import org.edustore.app.data.App;
import org.edustore.app.data.DBHelper;

import java.util.List;

public class InstalledAppsActivity extends AppCompatActivity {

    private FDroidDatabase db;
    private InstalledAppListAdapter adapter;
    private RecyclerView appList;
    private TextView emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FDroidApp fdroidApp = (FDroidApp) getApplication();
        fdroidApp.setSecureWindow(this);

        fdroidApp.applyPureBlackBackgroundInDarkTheme(this);
        EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.installed_apps_layout);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        adapter = new InstalledAppListAdapter(this);

        appList = findViewById(R.id.app_list);
        appList.setHasFixedSize(true);
        appList.setLayoutManager(new LinearLayoutManager(this));
        appList.setAdapter(adapter);

        emptyState = findViewById(R.id.empty_state);

        db = DBHelper.getDb(this);
        db.getAppDao().getInstalledAppListItems(getPackageManager()).observe(this, this::onLoadFinished);

        UiUtils.setupEdgeToEdge(appList, false, true);
    }

    private void onLoadFinished(List<AppListItem> items) {
        adapter.setApps(items);

        if (adapter.getItemCount() == 0) {
            appList.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            appList.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }

        // load app prefs for each app off the UiThread and update item if updates are ignored
        AppPrefsDao appPrefsDao = db.getAppPrefsDao();
        for (AppListItem item : items) {
            Utils.observeOnce(appPrefsDao.getAppPrefs(item.getPackageName()), this, appPrefs -> {
                if (appPrefs.getIgnoreVersionCodeUpdate() > 0) adapter.updateItem(item, appPrefs);
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.installed_apps, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.menu_share) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("packageName,versionCode,versionName\n");
            for (int i = 0; i < adapter.getItemCount(); i++) {
                App app = adapter.getItem(i);
                if (app != null) {
                    stringBuilder.append(app.packageName).append(',')
                            .append(app.installedVersionCode).append(',')
                            .append(app.installedVersionName).append('\n');
                }
            }
            String title = getString(R.string.send_installed_apps);
            ShareCompat.IntentBuilder intentBuilder = new ShareCompat.IntentBuilder(this)
                    .setSubject(title)
                    .setChooserTitle(title)
                    .setText(stringBuilder.toString())
                    .setType("text/csv");
            try {
                Intent chooserIntent = Intent.createChooser(intentBuilder.getIntent(), title);
                startActivity(chooserIntent);
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.no_handler_app_generic, Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }
}
