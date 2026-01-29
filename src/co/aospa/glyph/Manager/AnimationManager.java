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

package co.aospa.glyph.Manager;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Utils.FileUtils;
import co.aospa.glyph.Utils.ResourceUtils;

public final class AnimationManager {

    private static final String TAG = "GlyphAnimationManager";
    private static final boolean DEBUG = true;
    private static PowerManager.WakeLock sWakeLock;

    private static void acquireWakeLock(Context context) {
        if (sWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            sWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
            sWakeLock.acquire();
            if (DEBUG) Log.d(TAG, "Acquired wakelock");
        }
    }

    private static void releaseWakeLock() {
        if (sWakeLock != null) {
            sWakeLock.release();
            sWakeLock = null;
            if (DEBUG) Log.d(TAG, "Released wakelock");
        }
    }

    private static Future<?> submit(Runnable runnable) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        return executorService.submit(runnable);
    }

    private static boolean check(String name, boolean wait) {
        if (DEBUG) Log.d(TAG, "Playing animation | name: " + name + " | waiting: " + Boolean.toString(wait));

        if (StatusManager.isAllLedActive()) {
            if (DEBUG) Log.d(TAG, "All LEDs are active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isCallLedActive()) {
            if (DEBUG) Log.d(TAG, "Call animation is currently active, exiting animation | name: " + name);
            return false;
        }

        if (StatusManager.isAnimationActive()) {
            long start = System.currentTimeMillis();
            if (wait) {
                if (DEBUG) Log.d(TAG, "There is already an animation playing, wait | name: " + name);
                while (StatusManager.isAnimationActive()) {
                    if (System.currentTimeMillis() - start >= 2500) return false;
                }
            } else {
                if (DEBUG) Log.d(TAG, "There is already an animation playing, exiting | name: " + name);
                return false;
            }
        }

        return true;
    }

    private static boolean checkInterruption(String name) {
        return StatusManager.isAllLedActive()
                || (!Objects.equals(name, "call") && StatusManager.isCallLedEnabled())
                || (Objects.equals(name, "call") && !StatusManager.isCallLedEnabled())
                || (Objects.equals(name, "progress") && StatusManager.isVolumeAnimationActive());
    }

    public static void playCsv(Context context, String name) {
        playCsv(context, name, false, false);
    }

    public static void playCsv(Context context, String name, boolean wait) {
        playCsv(context, name, wait, false);
    }

    public static void playCsvReverse(Context context, String name) {
        playCsv(context, name, false, true);
    }

    public static void playCsvReverse(Context context, String name, boolean wait) {
        playCsv(context, name, wait, true);
    }

    public static void playCsv(Context context, String name, boolean wait, boolean reverse) {
        if (!check(name, wait))
                return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ResourceUtils.getAnimation(name)))) {
            Iterator<String> it = ResourceUtils.iterateCsvLines(reader, reverse);
            while (it.hasNext()) {
                if (checkInterruption("csv")) throw new InterruptedException();
                String[] pattern = it.next().split(",");
                if (ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                    updateLedFrame(pattern);
                } else {
                    if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + it.next());
                    throw new InterruptedException();
                }
                Thread.sleep(16, 666000);
            }
        } catch (Exception e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
        } finally {
            if (Constants.getDevice().equals("phone3a")) {
                updateLedFrame(new float[36]);
            } else {
                updateLedFrame(new float[5]);
            }
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: " + name);
            releaseWakeLock();
        }
    }

    public static void playCharging(int batteryLevel, boolean wait) {
        if (!check("charging", wait))
            return;

        StatusManager.setAnimationActive(true);
        StatusManager.setChargingAnimationActive(true);

        int[] batteryArray = StatusManager.getBatteryArray();
        int amount = (int) Math.floor((batteryLevel / 100D) * batteryArray.length);
        int last = StatusManager.getChargingLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    if (Constants.getDevice().equals("phone3a")) {
                        updateLedFrame(ResourceUtils.buildPatternArray(new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                    } else {
                    updateLedFrame(batteryArray);
                    }
                    Thread.sleep(16, 666000);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("charging")) throw new InterruptedException();
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = 0;
                    if (Constants.getDevice().equals("phone3a")) {
                        updateLedFrame(ResourceUtils.buildPatternArray(new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                    } else {
                    updateLedFrame(batteryArray);
                    }
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: charging");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setChargingLedLast(0);
                batteryArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
                if (Constants.getDevice().equals("phone3a")) {
                    updateLedFrame(ResourceUtils.buildPatternArray(new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                } else {
                updateLedFrame(batteryArray);
                }
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setBatteryArray(batteryArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: charging");
        }
    }

    public static void dismissCharging() {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_battery_levels_num")];
        int[] batteryArray = StatusManager.getBatteryArray();

        if (Arrays.equals(emptyArray, batteryArray))
            return;

        if (!check("Dismiss charging", false))
            return;

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss charging")) throw new InterruptedException();
            for (int i = batteryArray.length - 1; i >= 0; i--) {
                if (checkInterruption("Dismiss charging")) throw new InterruptedException();
                if (batteryArray[i] != 0) {
                    StatusManager.setChargingLedLast(i);
                    batteryArray[i] = 0;
                    if (Constants.getDevice().equals("phone3a")) {
                        updateLedFrame(ResourceUtils.buildPatternArray(new int[20], ResourceUtils.reverseFrameArray(batteryArray), new int[5]));
                    } else {
                    updateLedFrame(batteryArray);
                    }
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss charging");
            if (!StatusManager.isAllLedActive())
                updateLedFrame(new int[batteryArray.length]);
        } finally {
            StatusManager.setChargingLedLast(0);
            StatusManager.setChargingAnimationActive(false);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss charging");
        }
    }

    public static void playVolume(Context context, int volumeLevel, boolean wait) {
        if (!check("volume", wait))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);
        StatusManager.setVolumeAnimationActive(true);

        int[] volumeArray = StatusManager.getVolumeArray();
        if (volumeArray == null) {
            if (DEBUG) Log.d(TAG, "Volume array is null, cannot play animation");
            return;
        }

        int amount = (int) Math.round((volumeLevel / 100D) * volumeArray.length);
        int last = StatusManager.getVolumeLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    updateLedFrame(volumeArray);
                    Thread.sleep(16, 666000);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = 0;
                    updateLedFrame(volumeArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: volume");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setVolumeLedLast(0);
                volumeArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
                updateLedFrame(volumeArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setVolumeArray(volumeArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: volume");
            releaseWakeLock();
        }
    }

    public static void dismissVolume(Context context) {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
        int[] volumeArray = StatusManager.getVolumeArray();

        if (Arrays.equals(emptyArray, volumeArray)) {
            StatusManager.setVolumeAnimationActive(false);
            return;
        }

        if (!check("Dismiss volume", false))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss volume")) throw new InterruptedException();
            for (int i = volumeArray.length - 1; i >= 0; i--) {
                if (volumeArray[i] != 0) {
                    if (checkInterruption("Dismiss volume")) throw new InterruptedException();
                    StatusManager.setVolumeLedLast(i);
                    volumeArray[i] = 0;
                    updateLedFrame(volumeArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss volume");
            if (Constants.getDevice().equals("phone3a")) {
                updateLedFrame(new int[36]);
            } else {
                updateLedFrame(new int[volumeArray.length]);
            }
        } finally {
            StatusManager.setVolumeLedLast(0);
            StatusManager.setVolumeAnimationActive(false);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss volume");
            releaseWakeLock();
        }
    }

    public static void playCall() {
        playCall(-1);
    }

    public static void playCall(int slotIndex) {
        StatusManager.setCallLedEnabled(true);
        String name = SettingsManager.getGlyphCallAnimation(slotIndex);

        if (!check("call: " + name, true))
            return;

        StatusManager.setCallLedActive(true);

        while (StatusManager.isCallLedEnabled()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    ResourceUtils.getCallAnimation(name)))) {
                Iterator<String> it = ResourceUtils.iterateCsvLines(reader,
                        SettingsManager.isGlyphCallAnimationReversed(slotIndex));
                while (it.hasNext()) {
                    if (checkInterruption("call")) throw new InterruptedException();
                    String[] pattern = it.next().split(",");
                    if (ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), pattern.length)) {
                        updateLedFrame(pattern);
                    } else {
                        if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + name + " | line: " + it.next());
                        throw new InterruptedException();
                    }
                    Thread.sleep(16, 666000);
                }
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "Exception while playing animation | name: " + name + " | exception: " + e);
            } finally {
                if (StatusManager.isAllLedActive()) {
                    if (DEBUG) Log.d(TAG, "All LED active, pause playing animation | name: " + name);
                    while (StatusManager.isAllLedActive()) {}
                }
            }
        }
    }

    public static void stopCall() {
        if (DEBUG) Log.d(TAG, "Disabling Call Animation");
        StatusManager.setCallLedEnabled(false);
        if (Constants.getDevice().equals("phone3a")) {
            updateLedFrame(new float[36]);
        } else {
            updateLedFrame(new float[5]);
        }
        StatusManager.setCallLedActive(false);
        if (DEBUG) Log.d(TAG, "Done playing Call Animation");
    }

    public static void playEssential() {
        if (DEBUG) Log.d(TAG, "Playing Essential Animation");
        if (!StatusManager.isEssentialLedActive()) {
            submit(() -> {
                if (!check("essential", true))
                    return;

                StatusManager.setAnimationActive(true);

                try {
                    if (checkInterruption("essential")) throw new InterruptedException();
                    int[] steps = {12, 24, 36, 48, 60};
                    if (Constants.getDevice().equals("phone3a")) {
                        int[] essentialPattern = new int[11];
                        for (int i : steps) {
                            if (checkInterruption("essential")) throw new InterruptedException();
                            int patternBrightness = Constants.MAX_PATTERN_BRIGHTNESS / 100 * i;
                            Arrays.fill(essentialPattern, patternBrightness);
                            updateLedFrame(ResourceUtils.buildPatternArray(new int[20], essentialPattern, new int[5]));
                            Thread.sleep(16, 666000);
                        }
                    } else {
                        int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                        for (int i : steps) {
                            if (checkInterruption("essential")) throw new InterruptedException();
                            updateLedSingle(led, Constants.MAX_PATTERN_BRIGHTNESS / 100 * i);
                            Thread.sleep(16, 666000);
                        }
                    }
                } catch (InterruptedException ignored) {}
                StatusManager.setAnimationActive(false);
                StatusManager.setEssentialLedActive(true);
                if (DEBUG) Log.d(TAG, "Done playing animation | name: essential");
            });
        } else {
            if (Constants.getDevice().equals("phone3a")) {
                int[] essentialPattern = new int[11];
                int patternBrightness = Constants.MAX_PATTERN_BRIGHTNESS / 100 * 60;
                Arrays.fill(essentialPattern, patternBrightness);
                updateLedFrame(ResourceUtils.buildPatternArray(new int[20], essentialPattern, new int[5]));
            } else {
                int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                updateLedSingle(led, Constants.MAX_PATTERN_BRIGHTNESS / 100 * 60);
            }
        }
    }

    public static void stopEssential() {
        if (DEBUG) Log.d(TAG, "Disabling Essential Animation");
        StatusManager.setEssentialLedActive(false);
        if (!StatusManager.isAnimationActive() && !StatusManager.isAllLedActive()) {
            if (Constants.getDevice().equals("phone3a")) {
                updateLedFrame(new int[36]);
            } else {
                int led = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
                updateLedSingle(led, 0);
            }
        }
    }

    public static void playMusic(Set<String> snapshot) {
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;

        float[] pattern;

        if (Constants.getDevice().equals("phone3a")) {

            float[] zone1 = new float[20]; // largest (left 2)
            float[] zone2 = new float[11]; // medium (right 1)
            float[] zone3 = new float[5]; // smallest (left)

            if (snapshot.contains("low") || snapshot.contains("mid_low")) {
                Arrays.fill(zone1, maxPatternBrightness);
            }
            if (snapshot.contains("mid") || snapshot.contains("mid_high")) {
                Arrays.fill(zone2, maxPatternBrightness);
            }
            if (snapshot.contains("high")) {
                Arrays.fill(zone3, maxPatternBrightness);
            }

            pattern = ResourceUtils.buildPatternArray(zone1, zone2, zone3); // pattern = float[zone1.length + zone2.length + zone3.length] fill with zone values

        } else {
            pattern = new float[5];

            if (snapshot.contains("low")) {
                pattern[4] = maxPatternBrightness;
            }
            if (snapshot.contains("mid_low")) {
                pattern[3] = maxPatternBrightness;
            }
            if (snapshot.contains("mid")) {
                pattern[2] = maxPatternBrightness;
            }
            if (snapshot.contains("mid_high")) {
                pattern[0] = maxPatternBrightness;
            }
            if (snapshot.contains("high")) {
                pattern[1] = maxPatternBrightness;
            }
        }

        try {
            if (StatusManager.isGlyphIdle()) {
                updateLedFrame(pattern);
                Thread.sleep(106);
            }
        } catch (Exception e) {
            if (DEBUG)
                Log.d(TAG, "Exception while playing animation | name: music: " + snapshot + " | exception: " + e);
        } finally {
            if (StatusManager.isGlyphIdle()) {
                if (Constants.getDevice().equals("phone3a")) {
                    updateLedFrame(new float[36]);
                } else {
                    updateLedFrame(new float[5]);
                }
                if (DEBUG) Log.d(TAG, "Done playing animation | name: music " + snapshot);
            }
        }
    }

    private static void updateLedFrame(String[] pattern) {
        updateLedFrame(Arrays.stream(pattern)
                .mapToInt(Integer::parseInt)
                .toArray());
    }

    public static void updateLedFrame(int[] pattern) {
        float[] floatPattern = new float[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            floatPattern[i] = (float) pattern[i];
        }
        updateLedFrame(floatPattern);
    }

    private static void updateLedFrame(float[] pattern) {
        //if (DEBUG) Log.d(TAG, "Updating pattern: " + pattern);
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();

        if (StatusManager.isEssentialLedActive()) {
            if (pattern.length == 5 && !Constants.getDevice().equals("phone3a")) { // Phone (1) pattern
                if (pattern[1] < (maxPatternBrightness / 100 * 60)) {
                    pattern[1] = maxPatternBrightness / 100 * 60;
                } 
            } else if (pattern.length == 33) { // Phone (2) pattern
                if (pattern[2] < (maxPatternBrightness / 100 * 60)) {
                    pattern[2] = maxPatternBrightness / 100 * 60;
                }
            } else if (pattern.length == 36) { // Phone (3a) / Phone (3a) Pro pattern
                    if (pattern[21] < (maxPatternBrightness / 100 * 60)) {
                    Arrays.fill(pattern, 20, 31, maxPatternBrightness / 100 * 60);
                }
            }
        }

        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = pattern[i] / maxPatternBrightness * currentBrightness;
        }
        FileUtils.writeFrameLed(pattern);
    }

    private static void updateLedSingle(int led, String brightness) {
        updateLedSingle(led, Float.parseFloat(brightness));
    }

    private static void updateLedSingle(int led, int brightness) {
        updateLedSingle(led, (float) brightness);
    }

    private static void updateLedSingle(int led, float brightness) {
        //if (DEBUG) Log.d(TAG, "Updating led | led: " + led + " | brightness: " + brightness);
        float maxPatternBrightness = (float) Constants.MAX_PATTERN_BRIGHTNESS;
        float currentBrightness = (float) Constants.getBrightness();

        if (Constants.getDevice().equals("phone3a") && StatusManager.isEssentialLedActive()) {
            int[] ledArray = ResourceUtils.getIntArray("glyph_settings_notifs_essential_led_array");
            boolean essentialLedFound = Arrays.stream(ledArray).anyMatch(x -> x == led);
            if (essentialLedFound && brightness < (maxPatternBrightness / 100 * 60)) {
                brightness = maxPatternBrightness / 100 * 60;
            }
        } else {
            int essentialLed = ResourceUtils.getInteger("glyph_settings_notifs_essential_led");
            if (StatusManager.isEssentialLedActive()
                && led == essentialLed
                && brightness < (maxPatternBrightness / 100 * 60)) {
            brightness = maxPatternBrightness / 100 * 60;
            }
        }

        brightness = brightness / maxPatternBrightness * currentBrightness;

        FileUtils.writeSingleLed(led, brightness);
    }

    public static void playProgress(Context context, int progressPercent, int progressType, boolean wait) {
        if (!check("progress", wait))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);
        StatusManager.setProgressAnimationActive(true);
        StatusManager.setProgressType(progressType);

        int[] progressArray = StatusManager.getProgressArray();
        if (progressArray == null) {
            if (DEBUG) Log.d(TAG, "Progress array is null, cannot play animation");
            return;
        }

        int amount = (int) Math.round((progressPercent / 100D) * progressArray.length);
        int last = StatusManager.getProgressLedLast();
        int next = amount - 1;

        try {
            if (last <= next) {
                for (int i = last; i <= next; i++) {
                    if (checkInterruption("progress")) throw new InterruptedException();
                    StatusManager.setProgressLedLast(i);
                    progressArray[i] = Constants.MAX_PATTERN_BRIGHTNESS;
                    updateLedFrame(progressArray);
                    Thread.sleep(16, 666000);
                }
            } else if (last > next) {
                for (int i = last; i > next; i--) {
                    if (checkInterruption("progress")) throw new InterruptedException();
                    StatusManager.setProgressLedLast(i);
                    progressArray[i] = 0;
                    updateLedFrame(progressArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: progress");
            if (!StatusManager.isAllLedActive()) {
                StatusManager.setProgressLedLast(0);
                progressArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
                updateLedFrame(progressArray);
            }
        } finally {
            StatusManager.setAnimationActive(false);
            StatusManager.setProgressArray(progressArray);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: progress");
            releaseWakeLock();
        }
    }

    public static void dismissProgress(Context context) {
        int[] emptyArray = new int[ResourceUtils.getInteger("glyph_settings_volume_levels_num")];
        int[] progressArray = StatusManager.getProgressArray();

        if (Arrays.equals(emptyArray, progressArray))
            return;

        if (!check("Dismiss progress", false))
            return;

        acquireWakeLock(context);

        StatusManager.setAnimationActive(true);

        try {
            if (checkInterruption("Dismiss progress")) throw new InterruptedException();
            for (int i = progressArray.length - 1; i >= 0; i--) {
                if (progressArray[i] != 0) {
                    if (checkInterruption("Dismiss progress")) throw new InterruptedException();
                    StatusManager.setProgressLedLast(i);
                    progressArray[i] = 0;
                    updateLedFrame(progressArray);
                    Thread.sleep(16, 666000);
                }
            }
        } catch (InterruptedException e) {
            if (DEBUG) Log.d(TAG, "Exception while playing animation, interrupted | name: Dismiss progress");
            if (!StatusManager.isAllLedActive()) {
                if (Constants.getDevice().equals("phone3a")) {
                    updateLedFrame(new int[36]);
                } else {
                    updateLedFrame(new int[progressArray.length]);
                }
            }
        } finally {
            StatusManager.setProgressLedLast(0);
            StatusManager.setProgressAnimationActive(false);
            StatusManager.setProgressType(0);
            StatusManager.setAnimationActive(false);
            if (DEBUG) Log.d(TAG, "Done playing animation | name: Dismiss progress");
            releaseWakeLock();
        }
    }
}
