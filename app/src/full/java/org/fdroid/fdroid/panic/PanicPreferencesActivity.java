package org.edustore.app.panic;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.edustore.app.FDroidApp;
import org.edustore.app.R;

public class PanicPreferencesActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle bundle) {
        FDroidApp fdroidApp = (FDroidApp) getApplication();
        fdroidApp.setSecureWindow(this);

        fdroidApp.applyPureBlackBackgroundInDarkTheme(this);

        super.onCreate(bundle);
        setContentView(R.layout.activity_panic_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
}
