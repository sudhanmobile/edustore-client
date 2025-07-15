package org.edustore.app.views.categories;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import org.fdroid.database.AppOverviewItem;
import org.fdroid.database.Category;
import org.fdroid.database.Repository;
import org.edustore.app.FDroidApp;
import org.edustore.app.R;
import org.edustore.app.Utils;
import org.edustore.app.views.apps.AppListActivity;
import org.fdroid.index.v2.FileV2;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public class CategoryController extends RecyclerView.ViewHolder {

    private static final String TAG = "CategoryController";

    private final Button viewAll;
    private final TextView heading;
    private final ImageView image;
    private final AppPreviewAdapter appCardsAdapter;
    private final FrameLayout background;

    private final AppCompatActivity activity;
    static final int NUM_OF_APPS_PER_CATEGORY_ON_OVERVIEW = 20;

    private Category currentCategory;

    CategoryController(final AppCompatActivity activity, View itemView) {
        super(itemView);

        this.activity = activity;

        appCardsAdapter = new AppPreviewAdapter(activity);

        viewAll = itemView.findViewById(R.id.view_all_button);
        viewAll.setOnClickListener(onViewAll);

        heading = itemView.findViewById(R.id.name);
        image = itemView.findViewById(R.id.category_image);
        background = itemView.findViewById(R.id.category_background);

        RecyclerView appCards = itemView.findViewById(R.id.app_cards);
        appCards.setAdapter(appCardsAdapter);
        appCards.addItemDecoration(new ItemDecorator(activity));
    }

    private static String translateCategory(Context context, String categoryName) {
        int categoryNameId = getCategoryResource(context, categoryName);
        return categoryNameId == 0 ? categoryName : context.getString(categoryNameId);
    }

    void bindModel(@NonNull CategoryItem item, LiveData<List<AppOverviewItem>> liveData) {
        loadAppItems(liveData);
        currentCategory = item.category;

        String categoryName = item.category.getName(LocaleListCompat.getDefault());
        if (categoryName == null) categoryName = translateCategory(activity, item.category.getId());
        heading.setText(categoryName);
        heading.setContentDescription(activity.getString(R.string.tts_category_name, categoryName));

        @ColorInt int backgroundColour = getBackgroundColour(activity, item.category.getId());
        background.setBackgroundColor(backgroundColour);

        // try to load image from repo first
        FileV2 iconFile = item.category.getIcon(LocaleListCompat.getDefault());
        Repository repo = FDroidApp.getRepoManager(activity).getRepository(item.category.getRepoId());
        if (iconFile != null && repo != null) {
            Log.i(TAG, "Loading remote image for: " + item.category.getId());
            Glide.with(activity)
                    .load(Utils.getGlideModel(repo, iconFile))
                    .apply(Utils.getAlwaysShowIconRequestOptions())
                    .into(image);
        } else {
            Glide.with(activity).clear(image);
        }
        Resources r = activity.getResources();
        viewAll.setText(r.getQuantityString(R.plurals.button_view_all_apps_in_category, item.numApps, item.numApps));
        viewAll.setContentDescription(r.getQuantityString(R.plurals.tts_view_all_in_category, item.numApps,
                item.numApps, currentCategory));
    }

    private void loadAppItems(LiveData<List<AppOverviewItem>> liveData) {
        setIsRecyclable(false);
        liveData.observe(activity, new Observer<>() {
            @Override
            public void onChanged(List<AppOverviewItem> items) {
                appCardsAdapter.setAppCursor(items);
                setIsRecyclable(true);
                liveData.removeObserver(this);
            }
        });
    }

    /**
     *
     */
    private static int getCategoryResource(Context context, @NonNull String categoryName) {
        String suffix = categoryName.replace(" & ", "_").replace(" ", "_").replace("'", "");
        return context.getResources().getIdentifier("category_" + suffix, "string", context.getPackageName());
    }

    public static int getBackgroundColour(Context context, @NonNull String categoryId) {
        // Seed based on the categoryName, so that each time we try to choose a colour for the same
        // category it will look the same for each different user, and each different session.
        Random random = new Random(categoryId.toLowerCase(Locale.ENGLISH).hashCode());

        float[] hsv = new float[3];
        hsv[0] = random.nextFloat() * 360;
        hsv[1] = 0.4f;
        hsv[2] = 0.5f;
        return Color.HSVToColor(hsv);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final View.OnClickListener onViewAll = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (currentCategory == null) {
                return;
            }

            Intent intent = new Intent(activity, AppListActivity.class);
            intent.putExtra(AppListActivity.EXTRA_CATEGORY, currentCategory.getId());
            intent.putExtra(AppListActivity.EXTRA_CATEGORY_NAME,
                    currentCategory.getName(LocaleListCompat.getDefault()));
            activity.startActivity(intent);
        }
    };

    /**
     * Applies excessive padding to the start of the first item. This is so that the category artwork
     * can peek out and make itself visible. This is RTL friendly.
     *
     * @see org.edustore.app.R.dimen#category_preview__app_list__padding__horizontal
     * @see org.edustore.app.R.dimen#category_preview__app_list__padding__horizontal__first
     */
    private static class ItemDecorator extends RecyclerView.ItemDecoration {
        private final Context context;

        ItemDecorator(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void getItemOffsets(Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {
            Resources r = context.getResources();
            int horizontalPadding = (int) r.getDimension(R.dimen.category_preview__app_list__padding__horizontal);
            int horizontalPaddingFirst = (int) r.getDimension(
                    R.dimen.category_preview__app_list__padding__horizontal__first);
            int horizontalPaddingLast = (int) r.getDimension(
                    R.dimen.category_preview__app_list__padding__horizontal__last);
            boolean isLtr = ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_LTR;
            int itemPosition = parent.getChildLayoutPosition(view);
            boolean first = itemPosition == 0;
            boolean end = itemPosition == NUM_OF_APPS_PER_CATEGORY_ON_OVERVIEW - 1;

            // Leave this "paddingEnd" local variable here for clarity when converting from
            // left/right to start/end for RTL friendly layout.
            int paddingEnd = end ? horizontalPaddingLast : horizontalPadding;
            int paddingStart = first ? horizontalPaddingFirst : horizontalPadding;

            int paddingLeft = isLtr ? paddingStart : paddingEnd;
            int paddingRight = isLtr ? paddingEnd : paddingStart;
            outRect.set(paddingLeft, 0, paddingRight, 0);
        }
    }
}
