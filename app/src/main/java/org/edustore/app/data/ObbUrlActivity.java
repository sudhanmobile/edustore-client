package org.edustore.app.data;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import org.edustore.app.Utils;

/**
 * Replies with the public download URL for the OBB that belongs to the
 * requesting app/version.  If it doesn't know the OBB URL for the requesting
 * app, the {@code resultCode} will be {@link AppCompatActivity#RESULT_CANCELED}. The
 * request must be sent with {@link AppCompatActivity#startActivityForResult(Intent, int)}
 * in order to receive a reply, which will include an {@link Intent} with the
 * URL as data and the SHA-256 hash as a String {@code Intent} extra.
 */
public class ObbUrlActivity extends AppCompatActivity {
    public static final String TAG = "ObbUrlActivity";

    public static final String ACTION_GET_OBB_MAIN_URL = "org.edustore.app.action.GET_OBB_MAIN_URL";
    public static final String ACTION_GET_OBB_PATCH_URL = "org.edustore.app.action.GET_OBB_PATCH_URL";

    public static final String EXTRA_SHA256 = "org.edustore.app.extra.SHA256";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        ComponentName componentName = getCallingActivity();
        setResult(RESULT_CANCELED);
        if (intent != null && componentName != null) {
            String action = intent.getAction();
            String packageName = componentName.getPackageName();
            Apk apk = null;

            if (apk == null) {
                Utils.debugLog(TAG, "got null APK for " + packageName);
            } else if (ACTION_GET_OBB_MAIN_URL.equals(action)) {
                String url = apk.getMainObbUrl();
                if (url != null) {
                    intent.setData(Uri.parse(url));
                    intent.putExtra(EXTRA_SHA256, apk.obbMainFileSha256);
                }
                setResult(RESULT_OK, intent);
            } else if (ACTION_GET_OBB_PATCH_URL.equals(action)) {
                String url = apk.getPatchObbUrl();
                if (url != null) {
                    intent.setData(Uri.parse(url));
                    intent.putExtra(EXTRA_SHA256, apk.obbPatchFileSha256);
                }
                setResult(RESULT_OK, intent);
            }
        }
        finish();
    }
}
