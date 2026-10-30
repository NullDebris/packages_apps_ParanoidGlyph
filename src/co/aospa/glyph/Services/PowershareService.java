/*
 * Copyright (C) 2023-2024 Paranoid Android
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

package co.aospa.glyph.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.FileObserver;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public class PowershareService extends Service {

    private static final String TAG = "GlyphPowershareService";
    private static final boolean DEBUG = true;

    private String POWERSHARE_ACTIVE = null;
    private String POWERSHARE_ENABLED = null;

    private FileObserver mFileObserver;
    private PowershareActiveObserver mPowershareActiveObserver;
    private Context mContext;

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");
        mPowershareActiveObserver = new PowershareActiveObserver();
        mContext = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        getPaths();
        if (TextUtils.isEmpty(POWERSHARE_ACTIVE) || TextUtils.isEmpty(POWERSHARE_ENABLED)) {
            Log.w(TAG, "Unable to get one or more powershare paths. device is unsupported?");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (mFileObserver == null) {
            mFileObserver = new PowershareEnabledObserver(POWERSHARE_ENABLED);
        }
        mFileObserver.startWatching();
        mPowershareActiveObserver.startWatching();
        return START_STICKY;
    }

    private void getPaths() {
        try {
            POWERSHARE_ACTIVE =
                ResourceUtils.getString("glyph_settings_paths_powershare_active_absolute");
        } catch (Exception ignored) { }
        try {
            POWERSHARE_ENABLED =
                ResourceUtils.getString("glyph_settings_paths_powershare_enabled_absolute");
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        if (mFileObserver != null) mFileObserver.stopWatching();
        mPowershareActiveObserver.stopWatching();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onPowershareEnabled() {
        if (DEBUG) Log.d(TAG, "onPowershareEnabled");
        mPowershareActiveObserver.continueWatching();
    }

    private void onPowershareDisabled() {
        if (DEBUG) Log.d(TAG, "onPowershareDisabled");
        mPowershareActiveObserver.pauseWatching();
    }

    private class PowershareEnabledObserver extends FileObserver {

        PowershareEnabledObserver(String path) {
            super(path, FileObserver.MODIFY);
        }

        @Override
        public void onEvent(int event, String file) {
            checkIfPowerShareIsEnabled();
        }

        @Override
        public void startWatching() {
            if (DEBUG) Log.d(TAG, "FileObserver: startWatching");
            checkIfPowerShareIsEnabled();
            super.startWatching();
        }

        private void checkIfPowerShareIsEnabled() {
            int enabled = FileUtils.readLineInt(POWERSHARE_ENABLED);
            if (DEBUG) Log.d(TAG, "FileObserver: checkIfPowerShareIsEnabled: " + enabled);
            if (enabled == 1) {
                onPowershareEnabled();
            } else {
                onPowershareDisabled();
            }
        }
    }

    private class PowershareActiveObserver extends Thread {

        private volatile boolean lastState = false;
        private volatile boolean pause = true;
        private volatile boolean ended = false;

        private final Object mPowershareActiveObserverLock = new Object();

        public void startWatching() {
            if (DEBUG) Log.d(TAG, "PowershareActiveObserver: startWatching");
            if (super.isAlive()) return;
            super.start();
        }

        public void continueWatching() {
            if (DEBUG) Log.d(TAG, "PowershareActiveObserver: continueWatching");
            synchronized (mPowershareActiveObserverLock) {
                if (!pause) return;
                pause = false;
                mPowershareActiveObserverLock.notifyAll();
            }
        }

        public void pauseWatching() {
            if (DEBUG) Log.d(TAG, "PowershareActiveObserver: pauseWatching");
            synchronized (mPowershareActiveObserverLock) {
                if (pause) return;
                lastState = false;
                pause = true;
            }
        }

        public void stopWatching() {
            if (DEBUG) Log.d(TAG, "PowershareActiveObserver: stopWatching");
            synchronized (mPowershareActiveObserverLock) {
                ended = true;
                pause = false;
                mPowershareActiveObserverLock.notifyAll();
            }
            interrupt();
        }

        private void updatePowershareState() {
            int active = FileUtils.readLineInt(POWERSHARE_ACTIVE);
            if (DEBUG) Log.d(TAG, "updatePowershareState: " + active);
            if (active == 1) {
                if (lastState) return;
                lastState = true;
                AnimationManager.playCsv(mContext, "powershare", true);
            } else {
                lastState = false;
            }
        }

        @Override
        public void run() {
            if (DEBUG) Log.d(TAG, "PowershareActiveObserver: run");
            while (!ended) {
                synchronized (mPowershareActiveObserverLock) {
                    while (pause && !ended) {
                        try {
                            if (DEBUG) Log.d(TAG, "mPowershareActiveObserverLock.wait()");
                            mPowershareActiveObserverLock.wait();
                        } catch (InterruptedException e) {}
                    }
                }
                if (ended) break;
                updatePowershareState();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {}
            }
        }
    }
}
