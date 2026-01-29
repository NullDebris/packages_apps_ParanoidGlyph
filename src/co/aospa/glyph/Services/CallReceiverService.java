/*
 * Copyright (C) 2022-2024 Paranoid Android
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.aospa.glyph.Manager.AnimationManager;
import co.aospa.glyph.Manager.SettingsManager;

public class CallReceiverService extends Service implements OnIncomingCallListener {

    private static final String TAG = "GlyphCallReceiverService";
    private static final boolean DEBUG = true;

    private final Map<Integer, CallStateCallback> mCallbacks = new HashMap<>();
    private final Map<Integer, TelephonyManager> mTelephonyManagers = new HashMap<>();
    private AudioManager mAudioManager;
    private TelephonyManager tm;
    private TelephonyManager tmForSub;

    private SubscriptionManager sm;

    private HandlerThread thread;
    private Handler mThreadHandler;

    int incomingSlotIndex = -1;

    private Runnable playCall = new Runnable() {
        @Override
        public void run() {
            AnimationManager.playCall(incomingSlotIndex);
        }
    };

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "Creating service");

        // Add a handler thread
        thread = new HandlerThread("CallReceiverService");
        thread.start();
        Looper looper = thread.getLooper();
        mThreadHandler = new Handler(looper);

        mAudioManager = getSystemService(AudioManager.class);
        mAudioManager.addOnModeChangedListener(cmd -> mThreadHandler.post(cmd), mAudioManagerOnModeChangedListener);
        mAudioManagerOnModeChangedListener.onModeChanged(mAudioManager.getMode());

//        IntentFilter callReceiver = new IntentFilter();
//        callReceiver.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
//        registerReceiver(mCallReceiver, callReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "Destroying service");
        unregisterCallbacks();
       // this.unregisterReceiver(mCallReceiver);
        mAudioManager.removeOnModeChangedListener(mAudioManagerOnModeChangedListener);
        disableCallAnimation();
        thread.quit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerCallbacks() {
        TelephonyManager baseTm = getSystemService(TelephonyManager.class);
        SubscriptionManager sm = getSystemService(SubscriptionManager.class);

        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        if (subs == null || subs.isEmpty()) {
            return;
        }

        for (SubscriptionInfo sub : subs) {
            int subId = sub.getSubscriptionId();
            int slotIndex = sub.getSimSlotIndex();

            TelephonyManager tmForSub =
                    baseTm.createForSubscriptionId(subId);

            CallStateCallback cb =
                    new CallStateCallback(subId, slotIndex, this);

            tmForSub.registerTelephonyCallback(
                    getMainExecutor(),
                    cb
            );

            mCallbacks.put(subId, cb);
            mTelephonyManagers.put(subId, tmForSub);
        }
    }

    private void unregisterCallbacks() {
        for (int subId : mCallbacks.keySet()) {
            TelephonyManager tm = mTelephonyManagers.get(subId);
            CallStateCallback cb = mCallbacks.get(subId);

            if (tm != null && cb != null) {
                tm.unregisterTelephonyCallback(cb);
            }
        }

        mCallbacks.clear();
        mTelephonyManagers.clear();
    }

private void enableCallAnimation() {
        if (DEBUG) Log.d(TAG, "enableCallAnimation");
        mThreadHandler.post(playCall);
    }

    private void disableCallAnimation() {
        if (DEBUG) Log.d(TAG, "disableCallAnimation");
        if (mThreadHandler.hasCallbacks(playCall))
            mThreadHandler.removeCallbacks(playCall);
        AnimationManager.stopCall();
    }

//    private final BroadcastReceiver mCallReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
//                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
//                if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
//                    if (DEBUG) Log.d(TAG, "EXTRA_STATE_RINGING");
//                    enableCallAnimation();
//                }
//
//                if ((state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))){
//                    if (DEBUG) Log.d(TAG, "EXTRA_STATE_OFFHOOK");
//                    disableCallAnimation();
//                }
//                if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)){
//                    if (DEBUG) Log.d(TAG, "EXTRA_STATE_IDLE");
//                    disableCallAnimation();
//                }
//            }
//        }
//    };

    private final AudioManager.OnModeChangedListener mAudioManagerOnModeChangedListener = new AudioManager.OnModeChangedListener() {
        @Override
        public void onModeChanged(int mode) {
            if (mode != AudioManager.MODE_RINGTONE) {
                if (DEBUG) Log.d(TAG, "mAudioManagerOnModeChangedListener: " + mode);
                disableCallAnimation();
            }
        }
    };

    @Override
    public void onIncomingCall(int slotIndex, int subId, int state) {
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            if (SettingsManager.isGlyphCallAnimationMerged()){
                incomingSlotIndex = -1;
            } else {
                incomingSlotIndex = slotIndex;
            }
            enableCallAnimation();
        }
    }
}
