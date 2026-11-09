package local.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Makes HomeActivity the effective home screen while the Google TV launcher stays enabled
 * (it can't be replaced as default home without root, and disabling it risks a boot loop).
 * Home presses open HomeActivity, and whenever the stock home screen comes up anyway
 * (boot, an app closing) it is immediately covered by HomeActivity.
 */
public class HomeKeyService extends AccessibilityService {
    private static final String STOCK_PKG = "com.google.android.apps.tv.launcherx";
    private static final String STOCK_HOME = STOCK_PKG + ".home.HomeActivity";

    private long lastLaunch;

    @Override
    protected void onServiceConnected() {
        // At boot the stock home is already showing before we connect, so no window event arrives.
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && STOCK_PKG.contentEquals(root.getPackageName())) launchHome();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) launchHome();
        return true; // swallow both down and up so the stock home never sees it
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && STOCK_HOME.contentEquals(event.getClassName() == null ? "" : event.getClassName())) {
            launchHome();
        }
    }

    @Override
    public void onInterrupt() {
    }

    private void launchHome() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 400) return; // key-up and the window event can both fire for one press
        lastLaunch = now;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }
}
