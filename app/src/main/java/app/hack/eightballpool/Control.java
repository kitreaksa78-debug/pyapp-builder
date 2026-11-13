package app.hack.eightballpool;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

public class Control {
    static void start(Context context) {
        // Start the foreground overlay service before opening the game.
        ContextCompat.startForegroundService(context, new Intent(context, ViewService.class));
        Helper.startGame(context);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, ViewService.class));
    }
}
