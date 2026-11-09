package local.tvcommon;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Weather icon, temperature and details, with the next sunrise or sunset below; hidden until loaded. */
public final class WeatherRow extends LinearLayout {
    private final ImageView icon;
    private final TextView temp, details, sun;

    public WeatherRow(Context context, float textSize, boolean shadow) {
        super(context);
        setOrientation(VERTICAL);
        setVisibility(GONE);
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        addView(line, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        icon = new ImageView(context);
        int size = Math.round(textSize * 1.6f * density);
        line.addView(icon, new LayoutParams(size, size));

        temp = new TextView(context);
        temp.setTextSize(textSize * 1.35f);
        temp.setTextColor(Color.WHITE);
        temp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        temp.setIncludeFontPadding(false);
        LayoutParams tempLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tempLp.leftMargin = Math.round(textSize * 0.6f * density);
        line.addView(temp, tempLp);

        details = new TextView(context);
        details.setTextSize(textSize);
        details.setTextColor(0xB3FFFFFF);
        details.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        details.setSingleLine(true);
        LayoutParams detailsLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        detailsLp.leftMargin = Math.round(textSize * 0.9f * density);
        line.addView(details, detailsLp);

        sun = new TextView(context);
        sun.setTextSize(textSize);
        sun.setTextColor(0xB3FFFFFF);
        sun.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        sun.setSingleLine(true);
        LayoutParams sunLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sunLp.leftMargin = size + tempLp.leftMargin;
        sunLp.topMargin = Math.round(textSize * 0.3f * density);
        addView(sun, sunLp);

        if (shadow) {
            temp.setShadowLayer(12, 0, 2, 0x66000000);
            details.setShadowLayer(12, 0, 2, 0x66000000);
            sun.setShadowLayer(12, 0, 2, 0x66000000);
        }
    }

    public void setWeather(Weather w) {
        if (w == null) {
            setVisibility(GONE);
            return;
        }
        icon.setImageDrawable(new WeatherIcon(w.code, w.day, Color.WHITE));
        temp.setText(w.temp + "°");
        details.setText(w.details());
        sun.setText(w.sun == null ? "" : w.sun);
        sun.setVisibility(w.sun == null ? GONE : VISIBLE);
        setVisibility(View.VISIBLE);
    }
}
