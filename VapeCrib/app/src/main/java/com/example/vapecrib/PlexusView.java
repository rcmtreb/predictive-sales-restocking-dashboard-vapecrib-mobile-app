package com.example.vapecrib;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Full-screen animated plexus / constellation background.
 * Particles drift slowly across the screen; nearby ones are joined by
 * semi-transparent cyan lines that fade with distance.
 * Purely Canvas-based — no external library needed.
 */
public class PlexusView extends View {

    private static final int   PARTICLE_COUNT  = 55;
    private static final float MAX_CONNECT_DP  = 180f;   // max distance to draw a line
    private static final float RADIUS_DP       = 2.5f;   // dot radius
    private static final float SPEED_DP        = 0.55f;  // base speed per frame

    private final Paint dotPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();

    private float[] px, py, vx, vy;
    private boolean ready = false;
    private float density;

    public PlexusView(Context context) {
        super(context);
        init();
    }

    public PlexusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlexusView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getContext().getResources().getDisplayMetrics().density;
        dotPaint.setColor(0xFF00CFFF);          // cyan dots
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1f);
        // Hardware layer keeps animation smooth without straining the CPU
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Called once when dimensions are known so particles start on-screen. */
    private void setup(int w, int h) {
        px = new float[PARTICLE_COUNT];
        py = new float[PARTICLE_COUNT];
        vx = new float[PARTICLE_COUNT];
        vy = new float[PARTICLE_COUNT];
        float maxSpeed = SPEED_DP * density;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            px[i] = rng.nextFloat() * w;
            py[i] = rng.nextFloat() * h;
            float angle = rng.nextFloat() * (float) (2 * Math.PI);
            float speed = (0.3f + rng.nextFloat() * 0.7f) * maxSpeed;
            vx[i] = (float) Math.cos(angle) * speed;
            vy[i] = (float) Math.sin(angle) * speed;
        }
        ready = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        if (!ready) setup(w, h);

        float maxDist = MAX_CONNECT_DP * density;

        // ── Move particles, bounce off edges ──
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            px[i] += vx[i];
            py[i] += vy[i];
            if (px[i] < 0 || px[i] > w) vx[i] = -vx[i];
            if (py[i] < 0 || py[i] > h) vy[i] = -vy[i];
        }

        // ── Draw connecting lines (opacity falls off with distance) ──
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            for (int j = i + 1; j < PARTICLE_COUNT; j++) {
                float dx = px[i] - px[j];
                float dy = py[i] - py[j];
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < maxDist) {
                    int alpha = (int) (150 * (1f - d / maxDist));
                    linePaint.setARGB(alpha, 0, 207, 255); // cyan
                    canvas.drawLine(px[i], py[i], px[j], py[j], linePaint);
                }
            }
        }

        // ── Draw dots ──
        float r = RADIUS_DP * density;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            canvas.drawCircle(px[i], py[i], r, dotPaint);
        }

        // Request next frame
        postInvalidateOnAnimation();
    }
}
