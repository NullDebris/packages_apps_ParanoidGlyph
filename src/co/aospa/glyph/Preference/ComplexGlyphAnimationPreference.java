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

package co.aospa.glyph.Preference;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import co.aospa.glyph.R;
import co.aospa.glyph.Constants.Constants;
import co.aospa.glyph.Utils.ResourceUtils;
import co.aospa.glyph.View.GlyphLEDView;

public class ComplexGlyphAnimationPreference extends Preference {

    private final String TAG = "ComplexGlyphAnimationPreference";
    private final boolean DEBUG = true;

    private Activity mActivity;

    private String animationName;
    private boolean animationTerminated;
    private volatile boolean animationPaused = true;
    private boolean animationReversed = false;
    private int animationTimeBetween = 0;
    private String[] animationSlugs;
    private int zoneCount;
    private int[] zoneLedCounts;
    private ImageView[] animationImgs;
    List<GlyphLEDView> glyphLEDViews;

    private View mRootView;
    private final View.OnClickListener mClickListener = v -> performClick(v);

    public ComplexGlyphAnimationPreference(Context context) {
        super(context);
        setActivity(context);
        setLayout(R.layout.glyph_settings_preview);
    }
    public ComplexGlyphAnimationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setActivity(context);
        setLayout(R.layout.glyph_settings_preview);
    }
    public ComplexGlyphAnimationPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setActivity(context);
        setLayout(R.layout.glyph_settings_preview);
    }
    public ComplexGlyphAnimationPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        setActivity(context);
        setLayout(defStyleRes);
    }

    private void setLayout(int layoutResource) {
        setLayoutResource(R.layout.glyph_settings_preview_frame);
        mRootView = LayoutInflater.from(getContext())
                .inflate(layoutResource, null, false);
        setShouldDisableView(false);
    }

    private void setActivity(Context context) {
        if (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                mActivity = (Activity) context;
            } else {
                setActivity(((ContextWrapper) context).getBaseContext());
            }
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        holder.itemView.setOnClickListener(mClickListener);

        final boolean selectable = isSelectable();
        holder.itemView.setFocusable(isSelectable());
        holder.itemView.setClickable(isSelectable());

        FrameLayout layout = (FrameLayout) holder.itemView;
        layout.removeAllViews();
        ViewGroup parent = (ViewGroup) mRootView.getParent();
        if (parent != null) {
            parent.removeView(mRootView);
        }
        layout.addView(mRootView);

        zoneCount = ResourceUtils.getStringArray("glyph_settings_animations_slugs").length;
        zoneLedCounts = ResourceUtils.getIntArray("glyph_settings_animations_slug_led_count");
        String[] zonePaths = ResourceUtils.getStringArray("zone_paths");


        glyphLEDViews = new ArrayList<>();
        for (int i = 0; i < zoneCount; i++) {
            GlyphLEDView gv = new GlyphLEDView(getContext());
            gv.setPathData(zonePaths[i]);
            gv.setLEDLayout(zoneLedCounts[i]);
            glyphLEDViews.add(gv);
            layout.addView(gv);
        }

    }

    @Override
    public void onAttached() {
        super.onAttached();
        if (DEBUG) Log.d(TAG, "onAttached");
        startAnimation();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (DEBUG) Log.d(TAG, "onDetached");
        stopAnimation();
    }

    private void startAnimation() {
        animationSlugs = ResourceUtils.getStringArray("glyph_settings_animations_slugs");
        animationThread.start();
    }

    private void stopAnimation() {
        animationTerminated = true;
        animationThread.interrupt();
    }

    public void updateAnimation(boolean play) {
        updateAnimation(play, animationName, 0);
    }

    public void updateAnimation(boolean play, String name) {
        updateAnimation(play, name, 0);
    }

    public void updateAnimation(boolean play, String name, int time) {
        animationTimeBetween = time;
        animationName = name;
        animationPaused = !play;
        animationThread.interrupt();
    }

    public void updateAnimation(boolean play, String name, boolean reverse) {
        updateAnimation(play, name, 0, reverse);
    }

    public void updateAnimation(boolean play, int time, boolean reverse) {
        updateAnimation(play, animationName, time, reverse);
    }

    public void updateAnimation(boolean play, String name, int time, boolean reverse) {
        animationTimeBetween = time;
        animationName = name;
        animationPaused = !play;
        animationReversed = reverse;
        animationThread.interrupt();
    }

    Thread animationThread = new Thread() {
        @Override
        public void run() {
            while (!animationTerminated) {
                while (animationPaused) {
                    Thread.onSpinWait();
                }
                String playMode = (animationReversed) ? "reverse" : "forwards";
                if (DEBUG) Log.d(TAG, "Displaying animation | name: " + animationName + " mode: " + playMode);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        ResourceUtils.getAnimation(animationName)))) {
                    Iterator<String> it = ResourceUtils.iterateCsvLines(reader, animationReversed);
                    List<int[]> zoneFrames = new ArrayList<>();
                    while (it.hasNext()) {
                        String[] split = it.next().split(",");
                        if (Constants.getDevice().equals("phone1") && split.length == 5) { // Phone (1) pattern on Phone (1)
                            mActivity.runOnUiThread(() -> {
                                for (int i = 0; i < animationSlugs.length; i++) {
                                    setGlyphsDrawable(animationImgs[i], Integer.parseInt(split[i]));
                                }
                            });
                        } else if (Constants.getDevice().equals("phone2") && split.length == 5) { // Phone (1) pattern on Phone (2)
                            mActivity.runOnUiThread(() -> {
                                setGlyphsDrawable(animationImgs[0], Integer.parseInt(split[0]));
                                setGlyphsDrawable(animationImgs[1], Integer.parseInt(split[0]));
                                setGlyphsDrawable(animationImgs[2], Integer.parseInt(split[1]));
                                setGlyphsDrawable(animationImgs[3], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[4], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[5], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[6], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[7], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[8], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[9], Integer.parseInt(split[3]));
                                setGlyphsDrawable(animationImgs[10], Integer.parseInt(split[4]));
                            });
                        } else if (Constants.getDevice().equals("phone2") && split.length == 33) { // Phone (2) pattern on Phone (2)
                            mActivity.runOnUiThread(() -> {
                                setGlyphsDrawable(animationImgs[0], Integer.parseInt(split[0]));
                                setGlyphsDrawable(animationImgs[1], Integer.parseInt(split[1]));
                                setGlyphsDrawable(animationImgs[2], Integer.parseInt(split[2]));
                                setGlyphsDrawable(animationImgs[3], Integer.parseInt(split[3]));
                                setGlyphsDrawable(animationImgs[4], Integer.parseInt(split[19]));
                                setGlyphsDrawable(animationImgs[5], Integer.parseInt(split[20]));
                                setGlyphsDrawable(animationImgs[6], Integer.parseInt(split[21]));
                                setGlyphsDrawable(animationImgs[7], Integer.parseInt(split[22]));
                                setGlyphsDrawable(animationImgs[8], Integer.parseInt(split[23]));
                                setGlyphsDrawable(animationImgs[9], Integer.parseInt(split[25]));
                                setGlyphsDrawable(animationImgs[10], Integer.parseInt(split[24]));
                            });
                        } else if (Constants.getDevice().equals("phone3a") && split.length == 36) { // Phone (3a) / (3a) Pro pattern on Phone (3a) / (3a) Pro
                            mActivity.runOnUiThread(() -> {
//                                setGlyphsDrawable(animationImgs[0], Integer.parseInt(split[0]));
//                                setGlyphsDrawable(animationImgs[1], Integer.parseInt(split[20]));
//                                setGlyphsDrawable(animationImgs[2], Integer.parseInt(split[31]));
                                int start = 0;
                                zoneFrames.clear();
                                for (int i = 0; i < zoneCount; i++) {
                                    int count = zoneLedCounts[i];
                                    int[] zoneFrame = new int[count];
                                    int end = start + count;
                                    for (int k = start; k < end; k++) {
                                        zoneFrame[k - start] = Integer.parseInt(split[k].trim());
                                    }
                                    start = end;
                                    zoneFrames.add(zoneFrame);
                                    glyphLEDViews.get(i).updateLEDs(zoneFrames.get(i));
                                }
                            });
                        } else {
                            if (DEBUG) Log.d(TAG, "Animation line length mismatch | name: " + animationName + " | line: " + it.next());
                            updateAnimation(false);
                        }
                        Thread.sleep(16, 666000);
                    }
                    Thread.sleep(animationTimeBetween);
                } catch (Exception e) {
                    if (DEBUG) Log.d(TAG, "Exception while displaying animation | name: " + animationName + " | exception: " + e);
                } finally {
                    if (animationPaused) {
                        if (DEBUG) Log.d(TAG, "Pause displaying animation | name: " + animationName);
                        mActivity.runOnUiThread(() -> {
                            for (int i = 0; i < zoneCount; i++) {
                                // setGlyphsDrawable(animationImgs[i], 0);
                                glyphLEDViews.get(i).setAllLEDs(0);
                            }
                        });
                    }
                }
            }
        }

        private void setGlyphsDrawable(ImageView imageView, int brightness) {
            if (brightness <= 0) {
                imageView.setAlpha(0.3f);
            } else {
                float brightnessFactor = (float) (0.4 + 0.6 * (brightness / (double) Constants.getMaxBrightness()));
                imageView.setAlpha(brightnessFactor);
            }
        }

        private void setGlyphView(int index, int brightness) {

        }
    };
}
