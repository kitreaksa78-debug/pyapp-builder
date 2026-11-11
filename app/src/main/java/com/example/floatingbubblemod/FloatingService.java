package com.example.floatingbubblemod;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingBubble;
    private View modMenuView;
    private WindowManager.LayoutParams bubbleParams, menuParams;
    private boolean isMenuVisible = false;
    private static final String CHANNEL_ID = "FloatingBubbleChannel";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Pro Mod Menu Active")
                .setContentText("Floating Bubble កំពុងដំណើរការ")
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .build();
        startForeground(1, notification);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        // ១. បង្កើតប៊ូតុងអណ្តែតតូចមួយ (Floating Bubble)
        Button bubbleBtn = new Button(this);
        bubbleBtn.setText("MOD");
        bubbleBtn.setTextColor(android.graphics.Color.WHITE);
        bubbleBtn.setBackgroundColor(android.graphics.Color.parseColor("#99000000"));
        bubbleBtn.setTextSize(12f);

        bubbleParams = new WindowManager.LayoutParams(
                120, 120,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 50;
        bubbleParams.y = 300;

        floatingBubble = bubbleBtn;
        windowManager.addView(floatingBubble, bubbleParams);

        // ២. បង្កើតផ្ទាំង Mod Menu (លាក់ទុកមុនសិន)
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(android.graphics.Color.parseColor("#E6000000"));
        mainLayout.setPadding(30, 30, 30, 30);

        TextView title = new TextView(this);
        title.setText("=== PREMIUM MOD MENU ===");
        title.setTextColor(android.graphics.Color.GREEN);
        title.setTextSize(16f);
        mainLayout.addView(title);

        String[] mods = {"[✓] Premium Skin Unlock", "[✓] Damage Multiplier x10", "[✓] Fast Movement Speed", "[✓] Anti-Ban Protection"};
        for (String mod : mods) {
            TextView tv = new TextView(this);
            tv.setText(mod);
            tv.setTextColor(android.graphics.Color.WHITE);
            tv.setTextSize(14f);
            tv.setPadding(0, 10, 0, 10);
            mainLayout.addView(tv);
        }

        Button closeBtn = new Button(this);
        closeBtn.setText("បិទ Menu (Close)");
        closeBtn.setBackgroundColor(android.graphics.Color.parseColor("#AAFF0000"));
        closeBtn.setTextColor(android.graphics.Color.WHITE);
        closeBtn.setOnClickListener(v -> toggleMenu());
        mainLayout.addView(closeBtn);

        menuParams = new WindowManager.LayoutParams(
                600, 700,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        menuParams.gravity = Gravity.CENTER;

        modMenuView = mainLayout;
        modMenuView.setVisibility(View.GONE);
        windowManager.addView(modMenuView, menuParams);

        // ចុចលើ Bubble ដើម្បីបើក/បិទ Menu
        floatingBubble.setOnClickListener(v -> toggleMenu());

        // បន្ថែមមុខងារអូសរំកិល Floating Bubble ទៅមកបានដោយសេរី
        setupTouchListener();
    }

    private void toggleMenu() {
        if (isMenuVisible) {
            modMenuView.setVisibility(View.GONE);
            isMenuVisible = false;
        } else {
            modMenuView.setVisibility(View.VISIBLE);
            isMenuVisible = true;
        }
    }

    private void setupTouchListener() {
        floatingBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingBubble, bubbleParams);
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        // ប្រសិនបើអូសតិចតួច ចាត់ទុកថាเป็นการចុច (Click) ដើម្បីបើក/បិទ Menu
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);
                        if (diffX < 10 && diffY < 10) {
                            toggleMenu();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Bubble Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBubble != null) windowManager.removeView(floatingBubble);
        if (modMenuView != null) windowManager.removeView(modMenuView);
    }
}
