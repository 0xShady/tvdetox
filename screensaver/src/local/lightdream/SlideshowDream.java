package local.lightdream;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

import local.tvcommon.Weather;
import local.tvcommon.WeatherRow;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Slideshow screensaver: crossfades images/GIFs from /sdcard/Pictures/Screensaver with a slow
 * pan and zoom, under a clock that drifts a little every minute. Shows the built-in wallpaper
 * when the folder is empty. Images are decoded at screen size on a background thread, and only
 * two are held in memory at any time.
 */
public class SlideshowDream extends DreamService {
    private static final long INTERVAL_MS = 15_000;
    private static final long FADE_MS = 1_800;
    private static final long DRIFT_MS = 60_000;
    private static final float ZOOM = 1.08f;
    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final List<File> files = new ArrayList<>();
    private final Random random = new Random();
    private ImageView front, back;
    private View clockBox;
    private WeatherRow weather;
    private int index;
    private int screenW, screenH;
    private float density;

    private final Runnable next = this::showNext;
    private final Runnable drift = this::driftClock;
    private final Runnable refreshWeather = this::refreshWeather;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        density = dm.density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        front = newImageView();
        back = newImageView();
        root.addView(back, match());
        root.addView(front, match());

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.BL_TR,
                new int[]{0x99000000, 0x00000000, 0x00000000}));
        scrim.setTranslationZ(1f);
        root.addView(scrim, match());

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(84);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setShadowLayer(16, 0, 2, 0x66000000);
        box.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(20);
        date.setTextColor(0xD9FFFFFF);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setShadowLayer(12, 0, 2, 0x66000000);
        date.setPadding(dp(4), dp(2), 0, 0);
        box.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weather = new WeatherRow(this, 16, true);
        LinearLayout.LayoutParams weatherLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weatherLp.setMargins(dp(4), dp(14), 0, 0);
        box.addView(weather, weatherLp);
        box.setTranslationZ(2f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        lp.setMargins(dp(72), 0, 0, dp(64));
        root.addView(box, lp);
        clockBox = box;

        scanFolder();
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (files.isEmpty()) {
            int id = getResources().getIdentifier("wallpaper", "drawable", getPackageName());
            if (id != 0) crossfadeTo(getDrawable(id), 600_000);
        } else {
            showNext();
        }
        handler.postDelayed(drift, DRIFT_MS);
        refreshWeather();
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(next);
        handler.removeCallbacks(drift);
        handler.removeCallbacks(refreshWeather);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(next);
        handler.removeCallbacks(drift);
        handler.removeCallbacks(refreshWeather);
        decoder.shutdownNow();
        release(front);
        release(back);
        super.onDetachedFromWindow();
    }

    private ImageView newImageView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setAlpha(0f);
        return v;
    }

    private static File folder() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screensaver");
    }

    private void scanFolder() {
        files.clear();
        File[] list = folder().listFiles();
        if (list == null) return;
        for (File f : list) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            for (String ext : EXTENSIONS) {
                if (f.isFile() && name.endsWith(ext)) {
                    files.add(f);
                    break;
                }
            }
        }
        Collections.shuffle(files);
    }

    private void showNext() {
        File file = files.get(index++ % files.size());
        decoder.execute(() -> {
            Drawable d = decode(file);
            handler.post(() -> {
                if (d != null) crossfadeTo(d, INTERVAL_MS + FADE_MS);
                handler.postDelayed(next, d != null ? INTERVAL_MS : 0);
            });
        });
    }

    private Drawable decode(File file) {
        try {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file), (dec, info, src) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                float scale = Math.min(1f, Math.max(ZOOM * screenW / w, ZOOM * screenH / h));
                dec.setTargetSize(Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)));
            });
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void crossfadeTo(Drawable d, long motionMs) {
        ImageView incoming = back;
        ImageView outgoing = front;
        incoming.setImageDrawable(d);
        if (d instanceof Animatable) ((Animatable) d).start();
        incoming.bringToFront();

        boolean zoomIn = random.nextBoolean();
        float panX = (random.nextFloat() - 0.5f) * screenW * (ZOOM - 1f);
        float panY = (random.nextFloat() - 0.5f) * screenH * (ZOOM - 1f);
        incoming.setScaleX(zoomIn ? 1f : ZOOM);
        incoming.setScaleY(zoomIn ? 1f : ZOOM);
        incoming.setTranslationX(zoomIn ? 0 : panX);
        incoming.setTranslationY(zoomIn ? 0 : panY);
        ObjectAnimator fade = ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, 1f).setDuration(FADE_MS);
        fade.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                outgoing.animate().cancel();
                outgoing.setAlpha(0f);
                release(outgoing);
            }
        });
        fade.start();
        incoming.animate()
                .scaleX(zoomIn ? ZOOM : 1f).scaleY(zoomIn ? ZOOM : 1f)
                .translationX(zoomIn ? panX : 0).translationY(zoomIn ? panY : 0)
                .setInterpolator(new LinearInterpolator())
                .setDuration(motionMs);
        front = incoming;
        back = outgoing;
    }

    private void refreshWeather() {
        Weather.load(this, w -> weather.setWeather(w));
        handler.postDelayed(refreshWeather, Weather.REFRESH_MS);
    }

    private void driftClock() {
        float dx = (random.nextFloat() - 0.5f) * dp(48);
        float dy = (random.nextFloat() - 0.5f) * dp(32);
        clockBox.animate().translationX(dx).translationY(dy).setDuration(4_000).start();
        handler.postDelayed(drift, DRIFT_MS);
    }

    private static void release(ImageView v) {
        Drawable old = v.getDrawable();
        if (old instanceof Animatable) ((Animatable) old).stop();
        v.setImageDrawable(null);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
