package org.edustore.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.edustore.app.DeleteCacheService;
import org.edustore.app.Utils;
import org.edustore.app.work.CleanCacheWorker;

public class DeviceStorageReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        // FIXME apps are strongly encouraged to use the improved Context.getCacheDir() behavior
        //  so the system can automatically free up storage when needed.
        String action = intent.getAction();
        if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
            int percentageFree = Utils.getPercent(Utils.getImageCacheDirAvailableMemory(context),
                    Utils.getImageCacheDirTotalMemory(context));
            CleanCacheWorker.force(context);
            if (percentageFree <= 2) {
                DeleteCacheService.deleteAll(context);
            }
        }
    }
}
