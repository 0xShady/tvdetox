package local.tvcommon;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Current weather and the next sunrise or sunset from Open-Meteo (no API key), cached for 30 minutes. */
public final class Weather {
    public static final long REFRESH_MS = 30 * 60_000;

    public interface Callback {
        void onWeather(Weather weather);
    }

    public final int temp, high, low, code;
    public final boolean day;
    public final String sun;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Weather cached;
    private static long cachedAt;

    private Weather(int temp, int high, int low, int code, boolean day, String sun) {
        this.temp = temp;
        this.high = high;
        this.low = low;
        this.code = code;
        this.day = day;
        this.sun = sun;
    }

    public static boolean configured(Context c) {
        return !config(c, "latitude").isEmpty() && !config(c, "longitude").isEmpty();
    }

    /** Calls back on the main thread with fresh or cached weather, or null when it can't be loaded. */
    public static void load(Context context, Callback callback) {
        if (!configured(context)) {
            callback.onWeather(null);
            return;
        }
        if (cached != null && SystemClock.elapsedRealtime() - cachedAt < REFRESH_MS) {
            callback.onWeather(cached);
            return;
        }
        Context app = context.getApplicationContext();
        boolean is24 = DateFormat.is24HourFormat(app);
        String lat = config(app, "latitude"), lon = config(app, "longitude");
        String unit = config(app, "temp_unit").isEmpty() ? "celsius" : config(app, "temp_unit");
        IO.execute(() -> {
            Weather w;
            try {
                w = fetch(lat, lon, unit, is24);
                cached = w;
                cachedAt = SystemClock.elapsedRealtime();
            } catch (Exception e) {
                w = cached;
            }
            Weather result = w;
            MAIN.post(() -> callback.onWeather(result));
        });
    }

    public String condition() {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    public String details() {
        StringBuilder b = new StringBuilder(condition());
        b.append("   ").append(high).append("° / ").append(low).append("°");
        return b.toString();
    }

    private static Weather fetch(String lat, String lon, String unit, boolean is24) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
                + "&timezone=auto&forecast_days=2&temperature_unit=" + unit;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8_000);
        conn.setReadTimeout(8_000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject json = new JSONObject(out.toString("UTF-8"));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            String now = current.getString("time");
            return new Weather(
                    (int) Math.round(current.getDouble("temperature_2m")),
                    (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                    (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                    current.getInt("weather_code"),
                    current.getInt("is_day") == 1,
                    nextSun(now, daily.getJSONArray("sunrise"), daily.getJSONArray("sunset"), is24));
        } finally {
            conn.disconnect();
        }
    }

    /** Times are local ISO strings like 2026-11-09T18:05, so they compare as text. */
    private static String nextSun(String now, JSONArray sunrise, JSONArray sunset, boolean is24) throws Exception {
        String best = null, label = null;
        for (int i = 0; i < sunrise.length(); i++) {
            String[] times = {sunrise.getString(i), sunset.getString(i)};
            String[] labels = {"Sunrise", "Sunset"};
            for (int j = 0; j < 2; j++) {
                if (times[j].compareTo(now) > 0 && (best == null || times[j].compareTo(best) < 0)) {
                    best = times[j];
                    label = labels[j];
                }
            }
        }
        if (best == null) return null;
        int h = Integer.parseInt(best.substring(11, 13));
        String m = best.substring(14, 16);
        String time = is24 ? String.format(Locale.ROOT, "%02d:%s", h, m)
                : String.format(Locale.ROOT, "%d:%s %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
        return label + " " + time;
    }

    private static String config(Context c, String name) {
        int id = c.getResources().getIdentifier(name, "string", c.getPackageName());
        return id != 0 ? c.getString(id).trim() : "";
    }
}
