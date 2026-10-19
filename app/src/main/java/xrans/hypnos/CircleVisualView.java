package xrans.hypnos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CircleVisualView extends View {

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isConfigMode = false;
    private boolean isSelected = false;
    private String label = "C1";

    public CircleVisualView(Context context) {
        super(context);
        init();
    }

    public CircleVisualView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        borderPaint.setColor(Color.parseColor("#E53935"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2.5f));

        selectBorderPaint.setColor(Color.parseColor("#FFFFFF"));
        selectBorderPaint.setStyle(Paint.Style.STROKE);
        selectBorderPaint.setStrokeWidth(dpToPx(2.0f));
        selectBorderPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(6), dpToPx(4)}, 0));

        fillPaint.setColor(Color.parseColor("#14E53935"));
        fillPaint.setStyle(Paint.Style.FILL);

        crosshairPaint.setColor(Color.parseColor("#E53935"));
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(dpToPx(1.5f));

        centerDotPaint.setColor(Color.parseColor("#E53935"));
        centerDotPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.parseColor("#FFFFFF"));
        textPaint.setTextSize(dpToPx(12f));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        subTextPaint.setColor(Color.parseColor("#FF5252"));
        subTextPaint.setTextSize(dpToPx(9f));
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setFakeBoldText(true);
    }

    public void setConfigMode(boolean configMode) {
        this.isConfigMode = configMode;
        invalidate();
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
        invalidate();
    }

    public void setCircleLabel(String label) {
        this.label = label;
        invalidate();
    }

    public String getCircleLabel() {
        return label;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dpToPx(4f);

        if (radius <= 0) return;

        // Subtle translucent red fill
        fillPaint.setColor(isSelected ? Color.parseColor("#28E53935") : Color.parseColor("#10E53935"));
        canvas.drawCircle(cx, cy, radius, fillPaint);

        // Outer primary red border
        borderPaint.setColor(isSelected ? Color.parseColor("#FF5252") : Color.parseColor("#E53935"));
        borderPaint.setStrokeWidth(isSelected ? dpToPx(3f) : dpToPx(2f));
        canvas.drawCircle(cx, cy, radius, borderPaint);

        if (isConfigMode) {
            // In config mode: if selected, draw white dashed outer ring
            if (isSelected) {
                canvas.drawCircle(cx, cy, radius - dpToPx(3f), selectBorderPaint);
            }

            // Draw label and gesture hint
            float textY = cy - dpToPx(4f);
            canvas.drawText(label, cx, textY, textPaint);
            canvas.drawText(isSelected ? "GESER" : "TAP", cx, cy + dpToPx(10f), subTextPaint);
        } else {
            // In game mode: draw precision crosshair
            float gap = dpToPx(8f);

            // Horizontal crosshair
            canvas.drawLine(cx - radius + dpToPx(4f), cy, cx - gap, cy, crosshairPaint);
            canvas.drawLine(cx + gap, cy, cx + radius - dpToPx(4f), cy, crosshairPaint);

            // Vertical crosshair
            canvas.drawLine(cx, cy - radius + dpToPx(4f), cx, cy - gap, crosshairPaint);
            canvas.drawLine(cx, cy + gap, cx, cy + radius - dpToPx(4f), crosshairPaint);

            // Center aiming dot
            canvas.drawCircle(cx, cy, dpToPx(2.5f), centerDotPaint);
        }
    }
}
