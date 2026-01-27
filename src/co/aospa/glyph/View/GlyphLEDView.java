package co.aospa.glyph.View;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

public class GlyphLEDView extends View {

    private Path zonePath;             // The path for this zone
    private Paint ledPaint;
    private float[] ledFractions;      // Positions along the path (0..1)
    private int[] ledBrightness;     // Brightness per LED (0..255)

    private int ledCount;
    private float ledRadius = 8f;
    private float fadeFraction = 0.2f;

    private float viewportWidth;
    private float viewportHeight;

    public GlyphLEDView(Context context) {
        super(context);
        init(context);
    }


    public GlyphLEDView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public GlyphLEDView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ledPaint.setStyle(Paint.Style.FILL);

        if (attrs == null) return;

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GlyphView);

        viewportWidth = a.getFloat(
                R.styleable.GlyphView_viewportWidth, 0f);

        viewportHeight = a.getFloat(
                R.styleable.GlyphView_viewportHeight, 0f);


        a.recycle();

    }

    /** Set the path for this zone from a pathData string */
    public void setPathData(String pathData) {
        this.zonePath = PathParser.createPathFromPathData(pathData);
        invalidate();
    }

    /** Set LED layout along the path */
    public void setLEDLayout(int ledAmount) {
        ledFractions = new float[ledAmount];
        ledBrightness = new int[ledAmount];
        for (int i = 0; i < ledAmount; i++) {
            ledFractions[i] = (i + 0.5f) / ledAmount;
            ledBrightness[i] = 0;
        }
        ledCount = ledAmount;
        invalidate();
    }

    /** Update per-LED brightness dynamically */
    public void updateLEDs(int[] brightness) {
        if (brightness.length != ledBrightness.length) return;
        System.arraycopy(brightness, 0, ledBrightness, 0, ledBrightness.length);
        invalidate();
    }

    public void setAllLEDs(int brightness) {
        ledBrightness = new int[ledCount];
        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (zonePath == null) return;

        // Scale path to view size
        RectF bounds = new RectF();
        zonePath.computeBounds(bounds, true);
        float scaleX = getWidth() / viewportWidth;
        float scaleY = getHeight() / viewportHeight;
        canvas.save();
        canvas.scale(scaleX, scaleY);

        // Draw LEDs
        PathMeasure measure = new PathMeasure(zonePath, false);
        float[] pos = new float[2];
        for (int i = 0; i < ledFractions.length; i++) {
            float fraction = ledFractions[i];
            float brightness = ledBrightness[i];

            // Optional fade with neighbors
            if (i > 0) brightness += ledBrightness[i - 1] * fadeFraction;
            if (i < ledFractions.length - 1) brightness += ledBrightness[i + 1] * fadeFraction;
            brightness = Math.min(1f, brightness);

            int alpha = (int) (brightness * 255);
            ledPaint.setColor(Color.argb(alpha, 255, 255, 255));

            measure.getPosTan(fraction * measure.getLength(), pos, null);
            canvas.drawCircle(pos[0], pos[1], ledRadius, ledPaint);
        }

        canvas.restore();
    }
}

