package pro.onephone.callfilter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A glowing circular progress ring that shows a 0-100 "Protection Score".
 * Blue -> cyan gradient sweep with an animated fill. Pure-canvas, no deps.
 */
public class ScoreRingView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float strokeW = 0f;
    private int target = 0;       // 0..100 goal
    private float sweep = 0f;     // current animated 0..100

    private static final int COLOR_TRACK = 0xFF1C2233;
    private static final int COLOR_START = 0xFF4F8EF7; // accent blue
    private static final int COLOR_END   = 0xFF22D3EE; // cyan

    public ScoreRingView(Context c) { super(c); init(); }
    public ScoreRingView(Context c, AttributeSet a) { super(c, a); init(); }
    public ScoreRingView(Context c, AttributeSet a, int d) { super(c, a, d); init(); }

    private void init() {
        strokeW = dp(13);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeW);
        trackPaint.setColor(COLOR_TRACK);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeW);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(strokeW + dp(6));
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(0x3322D3EE);
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    /** Set the goal score (0-100) and animate the ring fill. */
    public void setScore(int score) {
        target = Math.max(0, Math.min(100, score));
        ValueAnimator anim = ValueAnimator.ofFloat(sweep, target);
        anim.setDuration(1400);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    public int getScore() { return target; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float pad = strokeW / 2f + dp(6);
        float size = Math.min(w, h);
        float left = (w - size) / 2f + pad;
        float top  = (h - size) / 2f + pad;
        oval.set(left, top, left + size - pad * 2, top + size - pad * 2);

        arcPaint.setShader(new LinearGradient(
            oval.left, oval.top, oval.right, oval.bottom,
            COLOR_START, COLOR_END, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // full track
        canvas.drawArc(oval, 0, 360, false, trackPaint);
        // animated progress (start at top, -90deg)
        float deg = 360f * (sweep / 100f);
        if (deg > 0) {
            canvas.drawArc(oval, -90, deg, false, glowPaint);
            canvas.drawArc(oval, -90, deg, false, arcPaint);
        }
    }
}
