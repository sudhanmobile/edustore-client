package org.edustore.app.net;

import android.content.Context;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.core.os.LocaleListCompat;

import org.fdroid.download.MirrorParameterManager;
import org.edustore.app.FDroidApp;
import org.edustore.app.Preferences;
import org.edustore.app.data.App;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FDroidMirrorParameterManager implements MirrorParameterManager {

    private final ConcurrentHashMap<String, Integer> errorCache;
    private static final int DELAY_TIME = 5;
    private static final TimeUnit DELAY_UNIT = TimeUnit.SECONDS;
    private final AtomicBoolean writeErrorScheduled = new AtomicBoolean(false);
    private final Runnable delayedErrorWrite;
    private final ScheduledExecutorService writeErrorExecutor = Executors.newSingleThreadScheduledExecutor();

    public FDroidMirrorParameterManager() {
        Preferences prefs = Preferences.get();
        errorCache = new ConcurrentHashMap<String, Integer>(prefs.getMirrorErrorData());
        delayedErrorWrite = () -> {
            if (writeErrorScheduled.compareAndSet(true, false)) {
                Map<String, Integer> snapshot = Collections.unmodifiableMap(new HashMap<String, Integer>(errorCache));
                Preferences writePrefs = Preferences.get();
                writePrefs.setMirrorErrorData(snapshot);
            }
        };
    }

    public void updateErrorCacheAndPrefs(@NonNull String url, @NonNull Integer errorCount) {
        errorCache.put(url, errorCount);
        if (writeErrorScheduled.compareAndSet(false, true)) {
            writeErrorExecutor.schedule(delayedErrorWrite, DELAY_TIME, DELAY_UNIT);
        }
    }

    @Override
    public void incrementMirrorErrorCount(@NonNull String mirrorUrl) {
        if (errorCache.containsKey(mirrorUrl)) {
            updateErrorCacheAndPrefs(mirrorUrl, errorCache.get(mirrorUrl) + 1);
        } else {
            updateErrorCacheAndPrefs(mirrorUrl, 1);
        }
    }

    @Override
    public int getMirrorErrorCount(@NonNull String mirrorUrl) {
        if (errorCache.containsKey(mirrorUrl)) {
            return errorCache.get(mirrorUrl);
        } else {
            return 0;
        }
    }

    @Override
    public boolean preferForeignMirrors() {
        Preferences prefs = Preferences.get();
        return prefs.isPreferForeignSet();
    }

    @NonNull
    @Override
    public String getCurrentLocation() {
        TelephonyManager tm = (TelephonyManager) FDroidApp.getInstance().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getSimCountryIso() != null) {
            return tm.getSimCountryIso();
        } else if (tm.getNetworkCountryIso() != null) {
            return tm.getNetworkCountryIso();
        } else {
            LocaleListCompat localeList = App.getLocales();
            if (localeList != null && localeList.size() > 0) {
                return localeList.get(0).getCountry();
            } else {
                return "";
            }
        }
    }
}
