/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.edustore.app;

import android.util.Log;

import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("MemberName")
class UiWatchers {
    private static final String LOG_TAG = UiWatchers.class.getSimpleName();
    private final List<String> mErrors = new ArrayList<>();

    /**
     * We can use the UiDevice registerWatcher to register a small script to be executed when the
     * framework is waiting for a control to appear. Waiting may be the cause of an unexpected
     * dialog on the screen and it is the time when the framework runs the registered watchers.
     * This is a sample watcher looking for ANR and crashes. it closes it and moves on. You should
     * create your own watchers and handle error logging properly for your type of tests.
     */
    void registerAnrAndCrashWatchers() {
        UiDevice.getInstance().registerWatcher("ANR", () -> {
            UiObject window = new UiObject(new UiSelector().className(
                    "com.android.server.am.AppNotRespondingDialog"));
            String errorText = null;
            if (window.exists()) {
                try {
                    errorText = window.getText();
                } catch (UiObjectNotFoundException e) {
                    Log.e(LOG_TAG, "dialog gone?", e);
                }
                onAnrDetected(errorText);
                postHandler("Wait");
                return true; // triggered
            }
            return false; // no trigger
        });
        // class names may have changed
        UiDevice.getInstance().registerWatcher("ANR2", () -> {
            UiObject window = new UiObject(new UiSelector().packageName("android")
                    .textContains("isn't responding."));
            if (window.exists()) {
                String errorText = null;
                try {
                    errorText = window.getText();
                } catch (UiObjectNotFoundException e) {
                    Log.e(LOG_TAG, "dialog gone?", e);
                }
                onAnrDetected(errorText);
                postHandler("Wait");
                return true; // triggered
            }
            return false; // no trigger
        });
        UiDevice.getInstance().registerWatcher("CRASH", () -> {
            UiObject window = new UiObject(new UiSelector().className(
                    "com.android.server.am.AppErrorDialog"));
            if (window.exists()) {
                String errorText = null;
                try {
                    errorText = window.getText();
                } catch (UiObjectNotFoundException e) {
                    Log.e(LOG_TAG, "dialog gone?", e);
                }
                onCrashDetected(errorText);
                postHandler("OK");
                return true; // triggered
            }
            return false; // no trigger
        });
        UiDevice.getInstance().registerWatcher("CRASH2", () -> {
            UiObject window = new UiObject(new UiSelector().packageName("android")
                    .textContains("has stopped"));
            if (window.exists()) {
                String errorText = null;
                try {
                    errorText = window.getText();
                } catch (UiObjectNotFoundException e) {
                    Log.e(LOG_TAG, "dialog gone?", e);
                }
                onCrashDetected(errorText);
                postHandler("OK");
                return true; // triggered
            }
            return false; // no trigger
        });
        Log.i(LOG_TAG, "Registered GUI Exception watchers");
    }

    private void onAnrDetected(String errorText) {
        mErrors.add(errorText);
    }

    private void onCrashDetected(String errorText) {
        mErrors.add(errorText);
    }

    /**
     * Current implementation ignores the exception and continues.
     */
    private void postHandler(String buttonText) {
        // TODO: Add custom error logging here
        String formatedOutput = String.format("UI Exception Message: %-20s\n", UiDevice
                .getInstance().getCurrentPackageName());
        Log.e(LOG_TAG, formatedOutput);
        UiObject buttonOK = new UiObject(new UiSelector().text(buttonText).enabled(true));
        // sometimes it takes a while for the OK button to become enabled
        buttonOK.waitForExists(5000);
        try {
            buttonOK.click();
        } catch (UiObjectNotFoundException e) {
            e.printStackTrace();
        }
    }
}