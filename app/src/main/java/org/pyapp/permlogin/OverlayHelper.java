package org.pyapp.permlogin;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * ជំនួយសម្រាប់ «បង្ហាញលើ app ផ្សេង» (SYSTEM_ALERT_WINDOW).
 *
 * ប្រើក្នុង MainActivity:
 *   if (!OverlayHelper.has(this)) { OverlayHelper.request(this); }
 *   WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
 *       ..., OverlayHelper.layoutType(), ...);
 */
public final class OverlayHelper {

    private OverlayHelper() {}

    /** តើ app មានសិទ្ធិបង្ហាញ overlay ហើយឬនៅ? (SDK < 23 មិនត្រូវការអនុញ្ញាត) */
    public static boolean has(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return Settings.canDrawOverlays(ctx);
    }

    /** បើកផ្ទាំង Settings ដើម្បីឲ្យអ្នកប្រើបើក «បង្ហាញលើ app ផ្សេង»។ */
    public static void request(Context ctx) {
        if (has(ctx)) {
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()));
            if (!(ctx instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            ctx.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(fallback);
            } catch (Exception ignored) {
            }
        }
    }

    /** Window type ត្រឹមត្រូវតាម SDK (TYPE_PHONE ចាស់ / TYPE_APPLICATION_OVERLAY ថ្មី)។ */
    @SuppressWarnings("deprecation")
    public static int layoutType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }
}
