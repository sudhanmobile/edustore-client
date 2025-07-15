/*
 * Copyright (C) 2016-17 Peter Serwylo,
 * Copyright (C) 2017-18 Hans-Christoph Steiner
 * Copyright (C) 2019 Michael Pöhn, michael.poehn@fsfe.org
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

package org.edustore.app.views.apps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.fdroid.database.AppListItem;
import org.fdroid.database.AppListSortOrder;
import org.fdroid.database.FDroidDatabase;
import org.fdroid.database.Repository;
import org.edustore.app.FDroidApp;
import org.edustore.app.Preferences;
import org.edustore.app.R;
import org.edustore.app.UiUtils;
import org.edustore.app.Utils;
import org.edustore.app.compat.LocaleCompat;
import org.edustore.app.data.DBHelper;
import org.edustore.app.views.main.MainActivity;

import java.util.Collections;
import java.util.List;

/**
 * Provides scrollable listing of apps for search and category views.
 */
public class AppListActivity extends AppCompatActivity implements FilterTextWatcher.SearchTermsChangedListener {

    public static final String TAG = "AppListActivity";

    public static final String EXTRA_CATEGORY
            = "org.edustore.app.views.apps.AppListActivity.EXTRA_CATEGORY";
    public static final String EXTRA_CATEGORY_NAME
            = "org.edustore.app.views.apps.AppListActivity.EXTRA_CATEGORY_NAME";
    public static final String EXTRA_SEARCH_TERMS
            = "org.edustore.app.views.apps.AppListActivity.EXTRA_SEARCH_TERMS";
    public static final String EXTRA_REPO_ID
            = "org.edustore.app.views.apps.AppListActivity.REPO_ID";
    public static final String EXTRA_AUTHOR_NAME
            = "org.edustore.app.views.apps.AppListActivity.EXTRA_AUTHOR_NAME";

    private static final String SEARCH_TERMS_KEY = "searchTerms";
    private static final String SORT_CLAUSE_KEY = "sortClauseSelected";
    private static SharedPreferences savedSearchSettings;

    private RecyclerView appView;
    private AppListAdapter appAdapter;
    private String categoryId;
    private String searchTerms;
    private long repoId;
    private String authorName;
    private String sortClauseSelected;
    private TextView emptyState;
    private EditText searchInput;
    private ImageView sortImage;
    private View hiddenAppNotice;
    private FDroidDatabase db;
    private Utils.KeyboardStateMonitor keyboardStateMonitor;
    private LiveData<List<AppListItem>> itemsLiveData;
    private FilterTextWatcher searchInputFilterWatcher;

    private interface SortClause {
        // these get used as settings keys, so changing them requires a migration
        String WORDS = "name";
        String LAST_UPDATED = "lastUpdated";
    }

    enum FilterType {
        AUTHOR,
        CATEGORY,
        REPO
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        FDroidApp fdroidApp = (FDroidApp) getApplication();
        fdroidApp.setSecureWindow(this);

        fdroidApp.applyPureBlackBackgroundInDarkTheme(this);
        EdgeToEdge.enable(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_app_list);

        db = DBHelper.getDb(this.getApplicationContext());
        keyboardStateMonitor = new Utils.KeyboardStateMonitor(findViewById(R.id.app_list_root));

        savedSearchSettings = getSavedSearchSettings(this);
        searchTerms = savedSearchSettings.getString(SEARCH_TERMS_KEY, null);
        sortClauseSelected = savedSearchSettings.getString(SORT_CLAUSE_KEY, SortClause.LAST_UPDATED);

        searchInput = findViewById(R.id.search);
        searchInput.setText(searchTerms);
        searchInputFilterWatcher = new FilterTextWatcher(this, searchInput, this);
        searchInput.addTextChangedListener(searchInputFilterWatcher);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Hide the keyboard (http://stackoverflow.com/a/1109108 (when pressing search)
                InputMethodManager inputManager = ContextCompat.getSystemService(AppListActivity.this,
                        InputMethodManager.class);
                inputManager.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);

                // Change focus from the search input to the app list.
                appView.requestFocus();
                return true;
            }
            return false;
        });

        sortImage = findViewById(R.id.sort);
        sortImage.setImageResource(
                SortClause.WORDS.equals(sortClauseSelected) ? R.drawable.ic_sort : R.drawable.ic_last_updated
        );
        sortImage.setOnClickListener(view -> {
            switch (sortClauseSelected) {
                case SortClause.WORDS:
                    sortClauseSelected = SortClause.LAST_UPDATED;
                    sortImage.setImageResource(R.drawable.ic_last_updated);
                    break;
                case SortClause.LAST_UPDATED:
                    sortClauseSelected = SortClause.WORDS;
                    sortImage.setImageResource(R.drawable.ic_sort);
                    break;
                default:
                    Log.e("AppListActivity", "Unknown sort clause: " + sortClauseSelected);
                    sortClauseSelected = SortClause.LAST_UPDATED;
            }
            putSavedSearchSettings(getApplicationContext(), SORT_CLAUSE_KEY, sortClauseSelected);
            loadItems();
            appView.scrollToPosition(0);
        });

        hiddenAppNotice = findViewById(R.id.hiddenAppNotice);
        hiddenAppNotice.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_VIEW_SETTINGS, true);
            startActivity(intent);
        });
        emptyState = findViewById(R.id.empty_state);

        View backButton = findViewById(R.id.back);
        backButton.setOnClickListener(v -> finish());

        View clearButton = findViewById(R.id.clear);
        clearButton.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
            if (!keyboardStateMonitor.isKeyboardVisible()) {
                InputMethodManager inputMethodManager =
                        ContextCompat.getSystemService(AppListActivity.this,
                                InputMethodManager.class);
                inputMethodManager.toggleSoftInputFromWindow(v.getApplicationWindowToken(),
                        InputMethodManager.SHOW_FORCED, 0);
            }
        });

        appAdapter = new AppListAdapter(this);

        appView = findViewById(R.id.app_list);
        appView.setHasFixedSize(true);
        appView.setLayoutManager(new LinearLayoutManager(this));
        appView.setAdapter(appAdapter);

        UiUtils.setupEdgeToEdge(appView, false, true);

        // this also causes a load as we set the search terms even for empty intents, thus the query changed
        parseIntentForSearchQuery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Glide.with(this).applyDefaultRequestOptions(new RequestOptions()
                .onlyRetrieveFromCache(!Preferences.get().isBackgroundDownloadAllowed()));
    }

    private void parseIntentForSearchQuery() {
        Intent intent = getIntent();
        categoryId = intent.hasExtra(EXTRA_CATEGORY) ? intent.getStringExtra(EXTRA_CATEGORY) : null;
        searchTerms = intent.hasExtra(EXTRA_SEARCH_TERMS) ? intent.getStringExtra(EXTRA_SEARCH_TERMS) : null;
        repoId = intent.hasExtra(EXTRA_REPO_ID) ? intent.getLongExtra(EXTRA_REPO_ID, -1) : -1;
        authorName = intent.hasExtra(EXTRA_AUTHOR_NAME) ? intent.getStringExtra(EXTRA_AUTHOR_NAME) : null;

        if (repoId > 0) {
            Repository repo = FDroidApp.getRepoManager(this).getRepository(repoId);
            if (repo != null) {
                LocaleListCompat locales = LocaleListCompat.getDefault();
                searchInputFilterWatcher.setFilterType(FilterType.REPO);
                searchInput.setText(getSearchText(repo.getName(locales), searchTerms));
            }
        } else if (authorName != null) {
            searchInputFilterWatcher.setFilterType(FilterType.AUTHOR);
            searchInput.setText(getSearchText(authorName, searchTerms));
        } else {
            String categoryName = intent.hasExtra(EXTRA_CATEGORY_NAME) ?
                    intent.getStringExtra(EXTRA_CATEGORY_NAME) : null;
            searchInputFilterWatcher.setFilterType(FilterType.CATEGORY);
            searchInput.setText(getSearchText(categoryName, searchTerms));
        }
        searchInput.setSelection(searchInput.getText().length());

        if (categoryId != null) {
            // Do this so that the search input does not get focus by default. This allows for a user
            // experience where the user scrolls through the apps in the category.
            appView.requestFocus();
        }
    }

    private void loadItems() {
        String search = searchTerms;
        if (itemsLiveData != null) {
            itemsLiveData.removeObservers(this);
        }
        AppListSortOrder sortOrder =
                SortClause.WORDS.equals(sortClauseSelected) ? AppListSortOrder.NAME : AppListSortOrder.LAST_UPDATED;
        if (repoId > 0) {
            itemsLiveData = db.getAppDao().getAppListItems(getPackageManager(), repoId, search, sortOrder);
        } else if (categoryId != null) {
            itemsLiveData = db.getAppDao().getAppListItems(getPackageManager(), categoryId, search, sortOrder);
        } else if (authorName != null) {
            itemsLiveData = db.getAppDao().getAppListItemsForAuthor(
                    getPackageManager(), authorName, search, sortOrder);
        } else {
            itemsLiveData = db.getAppDao().getAppListItems(getPackageManager(), search, sortOrder);
        }
        itemsLiveData.observe(this, this::onAppsLoaded);
    }

    private CharSequence getSearchText(@Nullable String category, @Nullable String searchTerms) {
        StringBuilder string = new StringBuilder();
        if (category != null) {
            string.append(category).append(FilterTextWatcher.FILTER_SEPARATOR);
        }

        if (searchTerms != null) {
            string.append(searchTerms);
        }

        return string.toString();
    }

    private void setShowHiddenAppsNotice(boolean show) {
        hiddenAppNotice.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void onAppsLoaded(List<AppListItem> items) {
        setShowHiddenAppsNotice(false);
        appAdapter.setHasHiddenAppsCallback(() -> setShowHiddenAppsNotice(true));
        if (searchTerms != null) {
            // DB doesn't support search result sorting, so do own sort if we searched
            Collections.sort(items, (o1, o2) -> {
                if (sortClauseSelected.equals(SortClause.LAST_UPDATED)) {
                    return Long.compare(o2.getLastUpdated(), o1.getLastUpdated());
                } else if (sortClauseSelected.equals(SortClause.WORDS)) {
                    String n1 = (o1.getName() == null ? "" : o1.getName())
                            .toLowerCase(LocaleCompat.getDefault());
                    String n2 = (o2.getName() == null ? "" : o2.getName())
                            .toLowerCase(LocaleCompat.getDefault());
                    return n1.compareTo(n2);
                }
                return 0;
            });
        }
        // Hide install button, if showing apps from a specific repo, because then we show repo versions
        // and do not respect the preferred repo.
        // The user may not be aware of this, so we force going through app details.
        appAdapter.setHideInstallButton(repoId > 0);
        appAdapter.setItems(items);
        if (items.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            appView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            appView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onSearchTermsChanged(@Nullable String categoryName, @NonNull String searchTerms) {
        if (categoryName == null) {
            // remove previous chip
            this.categoryId = null;
            this.repoId = -1;
            this.authorName = null;
            searchInputFilterWatcher.setFilterType(FilterType.CATEGORY); // defaults to category
        }
        this.searchTerms = searchTerms.isEmpty() ? null : searchTerms;
        appView.scrollToPosition(0);
        loadItems();
        if (TextUtils.isEmpty(searchTerms)) {
            removeSavedSearchSettings(this, SEARCH_TERMS_KEY);
        } else {
            putSavedSearchSettings(this, SEARCH_TERMS_KEY, searchTerms);
        }
    }

    private static void putSavedSearchSettings(Context context, String key, String searchTerms) {
        if (savedSearchSettings == null) {
            savedSearchSettings = getSavedSearchSettings(context);
        }
        savedSearchSettings.edit().putString(key, searchTerms).apply();
    }

    private static void removeSavedSearchSettings(Context context, String key) {
        if (savedSearchSettings == null) {
            savedSearchSettings = getSavedSearchSettings(context);
        }
        savedSearchSettings.edit().remove(key).apply();
    }

    private static SharedPreferences getSavedSearchSettings(Context context) {
        return context.getSharedPreferences("saved-search-settings", Context.MODE_PRIVATE);
    }
}
