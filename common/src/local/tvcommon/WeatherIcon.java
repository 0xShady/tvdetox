package local.tvcommon;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Simple line-art weather icons drawn in code, sized to the drawable bounds. */
public final class WeatherIcon extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int code;
    private final boolean day;

    public WeatherIcon(int code, boolean day, int color) {
        this.code = code;
        this.day = day;
        fill.setColor(color);
        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        canvas.save();
        canvas.translate(b.left, b.top);
        line.setStrokeWidth(s * 0.07f);

        if (code == 0) {
            sky(canvas, s, s * 0.5f, s * 0.5f, 1f);
        } else if (code <= 2) {
            canvas.save();
            canvas.clipOutPath(cloud(s, 0.08f, 0.1f, 0.09f));
            sky(canvas, s, s * 0.38f, s * 0.36f, 0.75f);
            canvas.restore();
            canvas.drawPath(cloud(s, 0.08f, 0.1f, 0f), fill);
        } else if (code == 45 || code == 48) {
            for (int i = 0; i < 3; i++) {
                float y = s * (0.32f + i * 0.18f);
                canvas.drawLine(s * (0.18f + i * 0.06f), y, s * (0.82f - (2 - i) * 0.06f), y, line);
            }
        } else {
            boolean precip = code >= 51;
            canvas.drawPath(cloud(s, 0f, precip ? -0.12f : 0f, 0f), fill);
            if (code >= 95) {
                Path bolt = new Path();
                bolt.moveTo(s * 0.52f, s * 0.62f);
                bolt.lineTo(s * 0.40f, s * 0.80f);
                bolt.lineTo(s * 0.50f, s * 0.80f);
                bolt.lineTo(s * 0.44f, s * 0.96f);
                bolt.lineTo(s * 0.62f, s * 0.74f);
                bolt.lineTo(s * 0.52f, s * 0.74f);
                bolt.lineTo(s * 0.58f, s * 0.62f);
                bolt.close();
                canvas.drawPath(bolt, fill);
            } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
                for (int i = 0; i < 3; i++) {
                    canvas.drawCircle(s * (0.34f + i * 0.16f), s * (0.76f + (i % 2) * 0.1f), s * 0.045f, fill);
                }
            } else if (precip) {
                for (int i = 0; i < 3; i++) {
                    float x = s * (0.38f + i * 0.14f);
                    canvas.drawLine(x, s * 0.70f, x - s * 0.06f, s * 0.88f, line);
                }
            }
        }
        canvas.restore();
    }

    private void sky(Canvas canvas, float s, float cx, float cy, float k) {
        if (day) {
            canvas.drawCircle(cx, cy, s * 0.17f * k, fill);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float c = (float) Math.cos(a), n = (float) Math.sin(a);
                canvas.drawLine(cx + c * s * 0.27f * k, cy + n * s * 0.27f * k,
                        cx + c * s * 0.38f * k, cy + n * s * 0.38f * k, line);
            }
        } else {
            Path moon = new Path();
            moon.addCircle(cx, cy, s * 0.32f * k, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + s * 0.18f * k, cy - s * 0.12f * k, s * 0.27f * k, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            canvas.drawPath(moon, fill);
        }
    }

    private Path cloud(float s, float dx, float dy, float grow) {
        Path p = new Path();
        p.addCircle(s * (0.33f + dx), s * (0.60f + dy), s * (0.15f + grow), Path.Direction.CW);
        p.addCircle(s * (0.52f + dx), s * (0.50f + dy), s * (0.21f + grow), Path.Direction.CW);
        p.addCircle(s * (0.70f + dx), s * (0.62f + dy), s * (0.13f + grow), Path.Direction.CW);
        Path base = new Path();
        base.addRoundRect(s * (0.18f + dx - grow), s * (0.58f + dy), s * (0.83f + dx + grow), s * (0.75f + dy + grow),
                s * (0.085f + grow), s * (0.085f + grow), Path.Direction.CW);
        p.op(base, Path.Op.UNION);
        Path whole = new Path();
        whole.op(p, Path.Op.UNION);
        return whole;
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        line.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
