package org.edustore.app.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.edustore.app.Preferences;
import org.edustore.app.data.Apk;
import org.edustore.app.data.App;
import org.fdroid.index.v2.FileV1;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
public class FileInstallerTest {

    private ContextWrapper context;

    @Before
    public final void setUp() {
        context = ApplicationProvider.getApplicationContext();
        Preferences.setupForTests(context);
        ShadowLog.stream = System.out;
    }

    @Test
    public void testInstallOtaZip() {
        App app = new App();
        app.packageName = "org.edustore.app.privileged.ota";
        Apk apk = new Apk();
        apk.apkFile = new FileV1("org.edustore.app.privileged.ota_2010.zip", "hash", null, null);
        apk.packageName = "org.edustore.app.privileged.ota";
        apk.versionCode = 2010;
        assertFalse(apk.isApk());
        Installer installer = InstallerFactory.create(context, app, apk);
        assertEquals("should be a FileInstaller",
                FileInstaller.class,
                installer.getClass());
    }
}
