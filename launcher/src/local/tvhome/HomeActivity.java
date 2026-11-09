package local.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import local.tvcommon.Weather;
import local.tvcommon.WeatherRow;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal TV home screen: clock and date over a wallpaper, glass buttons for Inputs, network,
 * screensaver and Settings, and one row of app banners. Framework classes only.
 */
public class HomeActivity extends Activity {
    /** Never shown as tiles. */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "com.android.tv.settings", // Settings button
            "com.mitv.livetv",         // Inputs menu
            "local.tvhome"));
    /** Always shown last. */
    private static final String LAST = "com.android.vending";

    private static final int TILE_W = 192, TILE_H = 108; // dp, 16:9 banner
    private static final int TILE_GAP = 16;
    private static final float IDLE_ALPHA = 0.4f;
    private static final int MARGIN = 64;

    private static final int INK = 0xFF0B0F17;
    private static final int GLASS = 0x1AFFFFFF;
    private static final int GLASS_STROKE = 0x2EFFFFFF;
    private static final int FOCUS = 0xF2FFFFFF;

    private float density;
    private LinearLayout row;
    private HorizontalScrollView scroller;
    private TextView appName;
    private TextView netLabel;
    private ImageView netIcon;
    private final List<View> buttons = new ArrayList<>();
    private View lastTile;
    private PopupWindow inputsPopup;
    private long pausedAt;
    private WeatherRow weather;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshWeather = this::refreshWeather;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            runOnUiThread(HomeActivity.this::updateNetwork);
        }

        @Override
        public void onLost(Network network) {
            runOnUiThread(HomeActivity.this::updateNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        setContentView(buildUi());
        loadApps();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
        getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        getSystemService(ConnectivityManager.class).unregisterNetworkCallback(networkCallback);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNetwork();
        focusRow();
        refreshWeather();
    }

    @Override
    protected void onPause() {
        pausedAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(refreshWeather);
        dismissInputs();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dismissInputs();
        // Android pauses us right before delivering the intent, so a very recent pause means
        // Home was pressed while already home: jump back to the first app. Coming back from
        // an app keeps the last focused tile instead.
        boolean wasShowing = SystemClock.uptimeMillis() - pausedAt < 500;
        if (wasShowing && row.getChildCount() > 0) {
            scroller.smoothScrollTo(0, 0);
            row.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    // ---- UI ----

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF05070C);
        root.setClipChildren(false);

        Drawable wallpaper = resource("wallpaper", "drawable");
        if (wallpaper != null) {
            ImageView wp = new ImageView(this);
            wp.setScaleType(ImageView.ScaleType.CENTER_CROP);
            wp.setImageDrawable(wallpaper);
            root.addView(wp, match());
        }
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40000000, 0x00000000, 0x00000000, 0xB3000000}));
        root.addView(scrim, match());

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(104);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        clockBox.addView(time, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(22);
        date.setTextColor(0xCCFFFFFF);
        date.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date.setPadding(dp(6), dp(4), 0, 0);
        clockBox.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weather = new WeatherRow(this, 18, false);
        LinearLayout.LayoutParams weatherLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weatherLp.setMargins(dp(4), dp(18), 0, 0);
        clockBox.addView(weather, weatherLp);
        FrameLayout.LayoutParams clockLp = wrap(Gravity.TOP | Gravity.START);
        clockLp.setMargins(dp(MARGIN), dp(44), 0, 0);
        root.addView(clockBox, clockLp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(button("ic_input", "Inputs", this::showInputs));
        View net = button("ic_wifi", "Wi-Fi", v -> open(Settings.ACTION_WIFI_SETTINGS));
        netIcon = (ImageView) ((ViewGroup) net).getChildAt(0);
        netLabel = (TextView) ((ViewGroup) net).getChildAt(1);
        bar.addView(net);
        bar.addView(button("ic_moon", null, v -> startScreensaver()));
        bar.addView(button("ic_settings", null, v -> open(Settings.ACTION_SETTINGS)));
        for (int i = 1; i < buttons.size(); i++) {
            ((LinearLayout.LayoutParams) buttons.get(i).getLayoutParams()).leftMargin = dp(12);
        }
        FrameLayout.LayoutParams barLp = wrap(Gravity.TOP | Gravity.END);
        barLp.setMargins(0, dp(52), dp(MARGIN), 0);
        root.addView(bar, barLp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        TextView section = new TextView(this);
        section.setText("APPS");
        section.setTextSize(13);
        section.setLetterSpacing(0.18f);
        section.setTextColor(0x99FFFFFF);
        section.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        section.setPadding(dp(MARGIN), 0, 0, dp(6));
        bottom.addView(section);

        scroller = new HorizontalScrollView(this) {
            // Scroll only when the focused tile would come within the page margin of either edge.
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                int pad = dp(MARGIN);
                int left = rect.left - getScrollX(), right = rect.right - getScrollX();
                int delta = right > getWidth() - pad ? right - (getWidth() - pad) : left < pad ? left - pad : 0;
                int max = Math.max(0, getChildAt(0).getWidth() - getWidth());
                return Math.min(max, Math.max(0, getScrollX() + delta)) - getScrollX();
            }

            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                placeName(false);
            }
        };
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setHorizontalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(dp(48));
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(MARGIN), dp(12), dp(MARGIN - TILE_GAP), dp(12));
        scroller.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottom.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        appName = new TextView(this);
        appName.setTextSize(18);
        appName.setTextColor(Color.WHITE);
        appName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        appName.setSingleLine(true);
        bottom.addView(appName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.bottomMargin = dp(40);
        root.addView(bottom, bottomLp);
        return root;
    }

    private View button(String icon, String text, View.OnClickListener onClick) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setMinimumWidth(dp(48));
        b.setPadding(text == null ? 0 : dp(14), 0, text == null ? 0 : dp(18), 0);

        ImageView iv = new ImageView(this);
        Drawable d = resource(icon, "drawable");
        if (d != null) iv.setImageDrawable(d.mutate());
        iv.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        b.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView label = new TextView(this);
        label.setTextSize(15);
        label.setTextColor(Color.WHITE);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setSingleLine(true);
        if (text != null) label.setText(text);
        label.setVisibility(text == null ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = dp(8);
        b.addView(label, labelLp);

        Drawable idle = glass(dp(24));
        Drawable focused = round(FOCUS, dp(24));
        b.setBackground(idle);
        b.setOnFocusChangeListener((v, has) -> {
            b.setBackground(has ? focused : idle);
            int fg = has ? INK : Color.WHITE;
            label.setTextColor(fg);
            iv.setImageTintList(ColorStateList.valueOf(fg));
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(140).start();
        });
        b.setOnClickListener(onClick);
        b.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = buttons.indexOf(b);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    focusRow();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return i == 0;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return i == buttons.size() - 1;
            }
            return false;
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        buttons.add(b);
        return b;
    }

    // ---- Apps ----

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, ResolveInfo> byPackage = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!HIDDEN.contains(pkg) && !byPackage.containsKey(pkg)) byPackage.put(pkg, ri);
        }

        List<ResolveInfo> ordered = new ArrayList<>();
        int favoritesId = getResources().getIdentifier("favorites", "array", getPackageName());
        String[] favorites = favoritesId != 0 ? getResources().getStringArray(favoritesId) : new String[0];
        for (String pkg : favorites) {
            ResolveInfo ri = byPackage.remove(pkg);
            if (ri != null) ordered.add(ri);
        }
        ResolveInfo last = byPackage.remove(LAST);
        List<ResolveInfo> rest = new ArrayList<>(byPackage.values());
        Collator collator = Collator.getInstance();
        rest.sort((a, b) -> collator.compare(a.loadLabel(pm).toString(), b.loadLabel(pm).toString()));
        ordered.addAll(rest);
        if (last != null) ordered.add(last);

        String focusedPkg = lastTile != null ? (String) lastTile.getTag() : null;
        boolean hadFocus = lastTile != null && lastTile.hasFocus();
        row.removeAllViews();
        lastTile = null;
        for (ResolveInfo ri : ordered) {
            View t = tile(ri);
            row.addView(t);
            if (ri.activityInfo.packageName.equals(focusedPkg)) lastTile = t;
        }
        if (hadFocus) focusRow();
    }

    private View tile(ResolveInfo ri) {
        PackageManager pm = getPackageManager();
        ActivityInfo ai = ri.activityInfo;
        String label = ri.loadLabel(pm).toString();

        FrameLayout t = new FrameLayout(this);
        t.setTag(ai.packageName);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(round(0xFF161C26, dp(16)));
        t.setClipToOutline(true);

        Drawable banner = ai.loadBanner(pm);
        if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
        if (banner != null) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageDrawable(banner);
            t.addView(iv, match());
        } else {
            LinearLayout fallback = new LinearLayout(this);
            fallback.setOrientation(LinearLayout.VERTICAL);
            fallback.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(ri.loadIcon(pm));
            fallback.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView name = new TextView(this);
            name.setText(label);
            name.setTextSize(13);
            name.setTextColor(Color.WHITE);
            name.setSingleLine(true);
            name.setPadding(dp(8), dp(8), dp(8), 0);
            fallback.addView(name);
            t.addView(fallback, match());
        }

        t.setOnFocusChangeListener((v, has) -> {
            v.animate().alpha(has ? 1f : IDLE_ALPHA)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(180).start();
            if (has) {
                lastTile = v;
                appName.setText(label);
                v.post(() -> placeName(true));
            }
        });
        t.setOnClickListener(v -> launch(ai));
        t.setOnLongClickListener(v -> {
            startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", ai.packageName, null)));
            return true;
        });
        t.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int i = row.indexOfChild(v);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    buttons.get(0).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return i == 0;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return i == row.getChildCount() - 1;
            }
            return false;
        });

        t.setAlpha(IDLE_ALPHA);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H));
        lp.setMargins(0, 0, dp(TILE_GAP), 0);
        t.setLayoutParams(lp);
        return t;
    }

    /** Keeps the app name under the focused tile, following it while the row scrolls. */
    private void placeName(boolean animate) {
        if (lastTile == null || lastTile.getParent() != row) return;
        float x = lastTile.getLeft() - scroller.getScrollX();
        x = Math.max(dp(MARGIN), Math.min(x, scroller.getWidth() - appName.getWidth() - dp(MARGIN)));
        if (animate) {
            appName.animate().translationX(x).setDuration(160).start();
        } else {
            appName.animate().cancel();
            appName.setTranslationX(x);
        }
    }

    private void focusRow() {
        View t = lastTile != null && lastTile.getParent() == row ? lastTile : row.getChildAt(0);
        if (t != null) t.requestFocus();
    }

    private void launch(ActivityInfo ai) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafely(i);
    }

    // ---- Inputs ----

    private void showInputs(View anchor) {
        TvInputManager tim = getSystemService(TvInputManager.class);
        List<TvInputInfo> inputs = new ArrayList<>();
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getParentId() == null && info.isPassthroughInput()) inputs.add(info);
            }
        }
        // HDMI ports first, then AV and the rest, each group by name.
        inputs.sort((a, b) -> {
            boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
            if (ha != hb) return ha ? -1 : 1;
            return inputLabel(a).compareTo(inputLabel(b));
        });
        if (inputs.isEmpty()) {
            Toast.makeText(this, "No inputs found", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable boxBg = round(0xF2121822, dp(18));
        boxBg.setStroke(dp(1), GLASS_STROKE);
        box.setBackground(boxBg);
        inputsPopup = new PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        inputsPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        inputsPopup.setElevation(dp(16));

        for (TvInputInfo info : inputs) {
            TextView item = new TextView(this);
            item.setText(inputLabel(info));
            item.setTextSize(16);
            item.setTextColor(Color.WHITE);
            item.setMinWidth(dp(200));
            item.setPadding(dp(18), dp(12), dp(18), dp(12));
            item.setFocusable(true);
            item.setClickable(true);
            Drawable focused = round(FOCUS, dp(12));
            item.setOnFocusChangeListener((v, has) -> {
                item.setBackground(has ? focused : null);
                item.setTextColor(has ? INK : Color.WHITE);
            });
            item.setOnClickListener(v -> {
                dismissInputs();
                Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(info.getId()))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startSafely(i);
            });
            box.addView(item);
        }
        inputsPopup.showAsDropDown(anchor, 0, dp(12));
        box.getChildAt(0).requestFocus();
    }

    private String inputLabel(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(this);
        return (custom != null && custom.length() > 0 ? custom : info.loadLabel(this)).toString();
    }

    private void dismissInputs() {
        if (inputsPopup != null && inputsPopup.isShowing()) inputsPopup.dismiss();
        inputsPopup = null;
    }

    // ---- Network ----

    private void updateNetwork() {
        String text = "Offline";
        String icon = "ic_offline";
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = "Ethernet";
                icon = "ic_ethernet";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                String ssid = getApplicationContext().getSystemService(WifiManager.class).getConnectionInfo().getSSID();
                text = ssid == null || ssid.contains("unknown ssid") ? "Wi-Fi" : ssid.replace("\"", "");
                icon = "ic_wifi";
            }
        }
        netLabel.setText(text);
        Drawable d = resource(icon, "drawable");
        if (d != null) netIcon.setImageDrawable(d.mutate());
    }

    private void refreshWeather() {
        handler.removeCallbacks(refreshWeather);
        Weather.load(this, w -> weather.setWeather(w));
        handler.postDelayed(refreshWeather, Weather.REFRESH_MS);
    }

    // ---- Helpers ----

    /** Starts the screensaver chosen in Settings, the same way "Start now" does there. */
    private void startScreensaver() {
        startSafely(new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void open(String settingsAction) {
        startSafely(new Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void startSafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Can't open that here", Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable resource(String name, String type) {
        int id = getResources().getIdentifier(name, type, getPackageName());
        return id != 0 ? getDrawable(id) : null;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable glass(int radius) {
        GradientDrawable d = round(GLASS, radius);
        d.setStroke(dp(1), GLASS_STROKE);
        return d;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private FrameLayout.LayoutParams wrap(int gravity) {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
