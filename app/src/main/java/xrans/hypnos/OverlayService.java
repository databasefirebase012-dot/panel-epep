package xrans.hypnos;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.example.BuildConfig;
import com.example.R;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import rikka.shizuku.Shizuku;

public class OverlayService extends Service {

    private static final String TAG = "HYPNOS_OverlayService";
    private static final String CHANNEL_ID = "hypnos_touch_channel";
    private static final int NOTIF_ID = 1001;
    public static final String PREF_NAME = "hypnos_config";
    public static final String KEY_CIRCLES = "circles_json";
    public static final String KEY_SELECTED_GAME = "selected_game_pkg";

    private WindowManager windowManager;
    private SharedPreferences prefs;

    // 1. Floating Menu Icon Views
    private View floatingView;
    private WindowManager.LayoutParams floatingParams;
    private FrameLayout btnFloatingIcon;
    private LinearLayout panelMenu;
    private TextView btnMenuConfig;
    private TextView btnMenuHide;
    private TextView btnMenuExit;

    // 2. Config Bottom Sheet Views
    private View bottomSheetView;
    private WindowManager.LayoutParams bottomSheetParams;
    private boolean isBottomSheetShown = false;
    private boolean isConfigMode = false;

    private TextView tvActiveCircleTitle;
    private TextView tvLabelSensiX, tvLabelSensiY, tvLabelRadius;
    private SeekBar seekSensiX, seekSensiY, seekRadius;
    private Button btnAddCircle, btnDeleteCircle, btnCancelConfig, btnSaveConfig;
    private FrameLayout btnCloseConfig;

    // 3. Multiple Touch Target Circles (Live Touch Interception & Parallel Injected Multiplier)
    private final List<TouchCircleItem> circleList = new ArrayList<>();
    private final List<TouchCircleItem> backupCircleList = new ArrayList<>();
    private TouchCircleItem selectedCircle = null;
    private boolean isAllCirclesHidden = false;

    // Local Fallback Injector
    private final ShizukuInputInjector localInjector = new ShizukuInputInjector();

    // Shizuku User Service Client (UID 2000)
    private UserServiceClient userServiceClient;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        Log.i(TAG, "Shizuku binder received in OverlayService");
        bindShizukuUserService();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        Log.w(TAG, "Shizuku binder dead in OverlayService");
        userServiceClient = null;
    };

    private final ServiceConnection shizukuConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            userServiceClient = new UserServiceClient(service);
            Log.i(TAG, "Shizuku UserService connected in OverlayService! PID: " + userServiceClient.getPid());
            applyTouchOptimizations();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            userServiceClient = null;
            Log.w(TAG, "Shizuku UserService disconnected in OverlayService");
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        startForegroundNotification();

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        bindShizukuUserService();

        initFloatingIcon();
        initBottomSheet();
        loadSavedCircles();
        applyTouchOptimizations();
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "HYPNOS Touch Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("HYPNOS Touch Optimization Overlay");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HYPNOS ENGINE ACTIVE")
                .setContentText("Touch-sensitivity optimizer running in background")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();

        startForeground(NOTIF_ID, notification);
    }

    private void bindShizukuUserService() {
        try {
            if (ShizukuHelper.hasShizukuPermission()) {
                Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                        new ComponentName(getPackageName(), UserServiceInput.class.getName())
                )
                        .daemon(false)
                        .processNameSuffix("input_service")
                        .debuggable(BuildConfig.DEBUG)
                        .version(BuildConfig.VERSION_CODE);

                Shizuku.bindUserService(args, shizukuConnection);
                Log.i(TAG, "Binding UserService from OverlayService...");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error binding Shizuku UserService in OverlayService", t);
        }
    }

    public void applyTouchOptimizations() {
        // 1. Android WRITE_SETTINGS (pointer_speed = 7, touch_sensitivity = 1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(), "pointer_speed", 7);
                Settings.System.putInt(getContentResolver(), "touch_sensitivity", 1);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed applying WRITE_SETTINGS", t);
        }

        // 2. Shizuku UID 2000 shell touch tuning
        float sx = selectedCircle != null ? selectedCircle.sensiX : 2.50f;
        float sy = selectedCircle != null ? selectedCircle.sensiY : 3.50f;

        if (userServiceClient != null && userServiceClient.isAlive()) {
            userServiceClient.applyGlobalTouchTuning(sx, sy);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    public int getTargetGameUid() {
        String selected = prefs.getString(KEY_SELECTED_GAME, null);
        if (selected != null) {
            try {
                return getPackageManager().getPackageUid(selected, 0);
            } catch (Throwable ignored) {}
        }
        try {
            return getPackageManager().getPackageUid(MainActivity.PKG_FF_MAX, 0);
        } catch (Throwable ignored) {}
        try {
            return getPackageManager().getPackageUid(MainActivity.PKG_FF_ORIGINAL, 0);
        } catch (Throwable ignored) {}
        return Process.INVALID_UID;
    }

    // ═══════════════════════════════════════════════════════════
    // 1. FLOATING MENU ICON (44dp, BLACK BOX, RED BORDER, RED 'X')
    // ═══════════════════════════════════════════════════════════
    private void initFloatingIcon() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_panel, null);

        btnFloatingIcon = floatingView.findViewById(R.id.btnFloatingIcon);
        panelMenu = floatingView.findViewById(R.id.panelMenu);
        btnMenuConfig = floatingView.findViewById(R.id.btnMenuConfig);
        btnMenuHide = floatingView.findViewById(R.id.btnMenuHide);
        btnMenuExit = floatingView.findViewById(R.id.btnMenuExit);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        floatingParams.gravity = Gravity.TOP | Gravity.START;
        floatingParams.x = 24;
        floatingParams.y = 320;

        btnFloatingIcon.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = floatingParams.x;
                        initialY = floatingParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDrag = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX;
                        float diffY = event.getRawY() - initialTouchY;
                        if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                            isDrag = true;
                            floatingParams.x = initialX + (int) diffX;
                            floatingParams.y = initialY + (int) diffY;
                            windowManager.updateViewLayout(floatingView, floatingParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            toggleMenu();
                        }
                        return true;
                }
                return false;
            }
        });

        btnMenuConfig.setOnClickListener(v -> {
            panelMenu.setVisibility(View.GONE);
            openConfigBottomSheet();
        });

        btnMenuHide.setOnClickListener(v -> {
            panelMenu.setVisibility(View.GONE);
            toggleAllCirclesVisibility();
        });

        btnMenuExit.setOnClickListener(v -> stopSelf());

        windowManager.addView(floatingView, floatingParams);
    }

    private void toggleMenu() {
        if (panelMenu.getVisibility() == View.VISIBLE) {
            panelMenu.setVisibility(View.GONE);
        } else {
            panelMenu.setVisibility(View.VISIBLE);
        }
    }

    private void toggleAllCirclesVisibility() {
        isAllCirclesHidden = !isAllCirclesHidden;
        int visibility = isAllCirclesHidden ? View.GONE : View.VISIBLE;
        for (TouchCircleItem item : circleList) {
            if (item.visualView != null) item.visualView.setVisibility(visibility);
        }
        btnMenuHide.setText(isAllCirclesHidden ? "SHOW" : "HIDE");
    }

    public void injectMotionEvent(float x, float y, int action, int targetUid) {
        if (userServiceClient != null && userServiceClient.isAlive()) {
            userServiceClient.injectEvent(x, y, action, targetUid);
        } else {
            localInjector.injectEvent(x, y, action, targetUid);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 2. CONFIG BOTTOM SHEET (0.00 - 20.00 SENSI, ADD, DELETE, SAVE)
    // ═══════════════════════════════════════════════════════════
    private void initBottomSheet() {
        bottomSheetView = LayoutInflater.from(this).inflate(R.layout.layout_config_bottom_sheet, null);

        tvActiveCircleTitle = bottomSheetView.findViewById(R.id.tvActiveCircleTitle);
        tvLabelSensiX = bottomSheetView.findViewById(R.id.tvLabelSensiX);
        tvLabelSensiY = bottomSheetView.findViewById(R.id.tvLabelSensiY);
        tvLabelRadius = bottomSheetView.findViewById(R.id.tvLabelRadius);

        seekSensiX = bottomSheetView.findViewById(R.id.seekSensiX);
        seekSensiY = bottomSheetView.findViewById(R.id.seekSensiY);
        seekRadius = bottomSheetView.findViewById(R.id.seekRadius);

        btnAddCircle = bottomSheetView.findViewById(R.id.btnAddCircle);
        btnDeleteCircle = bottomSheetView.findViewById(R.id.btnDeleteCircle);
        btnCancelConfig = bottomSheetView.findViewById(R.id.btnCancelConfig);
        btnSaveConfig = bottomSheetView.findViewById(R.id.btnSaveConfig);
        btnCloseConfig = bottomSheetView.findViewById(R.id.btnCloseConfig);

        seekSensiX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (selectedCircle != null) {
                    selectedCircle.sensiX = progress / 100.0f; // 0.00 to 20.00
                    tvLabelSensiX.setText(String.format("SENSI X: %.2f", selectedCircle.sensiX));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekSensiY.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (selectedCircle != null) {
                    selectedCircle.sensiY = progress / 100.0f; // 0.00 to 20.00
                    tvLabelSensiY.setText(String.format("SENSI Y: %.2f", selectedCircle.sensiY));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (selectedCircle != null) {
                    selectedCircle.radiusDp = 10 + progress;
                    tvLabelRadius.setText(String.format("RADIUS: %d DP", selectedCircle.radiusDp));
                    updateCircleViewDimensions(selectedCircle);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnAddCircle.setOnClickListener(v -> addNewCircle());
        btnDeleteCircle.setOnClickListener(v -> deleteSelectedCircle());
        btnCancelConfig.setOnClickListener(v -> cancelConfigEdits());
        btnSaveConfig.setOnClickListener(v -> saveConfigEdits());
        btnCloseConfig.setOnClickListener(v -> closeConfigBottomSheet(false));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sheetWidth = (int) (dm.widthPixels * 0.92f);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bottomSheetParams = new WindowManager.LayoutParams(
                sheetWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        bottomSheetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        bottomSheetParams.y = dpToPx(16);
    }

    private void openConfigBottomSheet() {
        if (!isBottomSheetShown) {
            backupCircleList.clear();
            for (TouchCircleItem item : circleList) {
                backupCircleList.add(item.copy());
            }

            isConfigMode = true;

            for (TouchCircleItem item : circleList) {
                if (item.visualView != null) {
                    item.visualView.setConfigMode(true);
                }
            }

            if (selectedCircle == null && !circleList.isEmpty()) {
                selectCircle(circleList.get(0));
            } else if (selectedCircle != null) {
                selectCircle(selectedCircle);
            }

            windowManager.addView(bottomSheetView, bottomSheetParams);
            isBottomSheetShown = true;
        }
    }

    private void closeConfigBottomSheet(boolean saved) {
        if (isBottomSheetShown) {
            isConfigMode = false;

            for (TouchCircleItem item : circleList) {
                if (item.visualView != null) {
                    item.visualView.setConfigMode(false);
                    item.visualView.setSelected(false);
                }
            }

            windowManager.removeView(bottomSheetView);
            isBottomSheetShown = false;
        }
    }

    private void saveConfigEdits() {
        saveCirclesToPrefs();
        applyTouchOptimizations();
        closeConfigBottomSheet(true);
        Toast.makeText(this, "DISIMPAN: TOUCH SENSITIVITAS X&Y AKTIF", Toast.LENGTH_SHORT).show();
    }

    private void cancelConfigEdits() {
        removeAllCircleViews();
        circleList.clear();
        for (TouchCircleItem backup : backupCircleList) {
            TouchCircleItem restored = backup.copy();
            circleList.add(restored);
            attachCircleViews(restored);
        }

        if (!circleList.isEmpty()) {
            selectedCircle = circleList.get(0);
        }

        closeConfigBottomSheet(false);
        Toast.makeText(this, "PERUBAHAN DIBATALKAN", Toast.LENGTH_SHORT).show();
    }

    private void selectCircle(TouchCircleItem target) {
        selectedCircle = target;
        for (TouchCircleItem item : circleList) {
            if (item.visualView != null) {
                item.visualView.setSelected(item == target);
            }
        }

        if (target != null) {
            tvActiveCircleTitle.setText("TARGET: " + target.label + " (GESER POSISI)");

            int progressX = Math.round(target.sensiX * 100.0f); // 0.00 to 20.00
            int progressY = Math.round(target.sensiY * 100.0f); // 0.00 to 20.00
            int progressRadius = target.radiusDp - 10;

            seekSensiX.setProgress(Math.max(0, Math.min(2000, progressX)));
            seekSensiY.setProgress(Math.max(0, Math.min(2000, progressY)));
            seekRadius.setProgress(Math.max(0, Math.min(90, progressRadius)));

            tvLabelSensiX.setText(String.format("SENSI X: %.2f", target.sensiX));
            tvLabelSensiY.setText(String.format("SENSI Y: %.2f", target.sensiY));
            tvLabelRadius.setText(String.format("RADIUS: %d DP", target.radiusDp));
        }
    }

    private void addNewCircle() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int newId = circleList.size() + 1;
        String newLabel = "C" + newId;

        int defaultX = dm.widthPixels / 2 - dpToPx(45) + (newId * dpToPx(20));
        int defaultY = dm.heightPixels / 2 - dpToPx(45) + (newId * dpToPx(20));

        TouchCircleItem newItem = new TouchCircleItem(newId, newLabel, defaultX, defaultY, 45, 2.50f, 3.50f);
        circleList.add(newItem);
        attachCircleViews(newItem);

        if (newItem.visualView != null) {
            newItem.visualView.setConfigMode(true);
        }

        selectCircle(newItem);
        Toast.makeText(this, "LINGKARAN " + newLabel + " DITAMBAHKAN", Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedCircle() {
        if (circleList.size() <= 1) {
            Toast.makeText(this, "MINIMAL 1 LINGKARAN HARUS AKTIF", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCircle == null) {
            Toast.makeText(this, "KLIK LINGKARAN YANG INGIN DIHAPUS DULU", Toast.LENGTH_SHORT).show();
            return;
        }

        TouchCircleItem toDelete = selectedCircle;
        removeCircleView(toDelete);
        circleList.remove(toDelete);

        for (int i = 0; i < circleList.size(); i++) {
            circleList.get(i).id = i + 1;
            circleList.get(i).label = "C" + (i + 1);
            if (circleList.get(i).visualView != null) {
                circleList.get(i).visualView.setCircleLabel(circleList.get(i).label);
            }
        }

        selectCircle(circleList.get(0));
        Toast.makeText(this, "LINGKARAN DIHAPUS", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════
    // 3. PERSISTENCE & CIRCLE MANAGEMENT
    // ═══════════════════════════════════════════════════════════
    private void loadSavedCircles() {
        String json = prefs.getString(KEY_CIRCLES, null);
        circleList.clear();

        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    int id = obj.optInt("id", i + 1);
                    String label = obj.optString("label", "C" + (i + 1));
                    int posX = obj.optInt("posX", 200);
                    int posY = obj.optInt("posY", 400);
                    int radiusDp = obj.optInt("radiusDp", 45);
                    float sensiX = (float) obj.optDouble("sensiX", 2.50);
                    float sensiY = (float) obj.optDouble("sensiY", 3.50);

                    TouchCircleItem item = new TouchCircleItem(id, label, posX, posY, radiusDp, sensiX, sensiY);
                    circleList.add(item);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed parsing saved circles json", t);
            }
        }

        if (circleList.isEmpty()) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int defaultSize = dpToPx(45 * 2);
            int defX = (dm.widthPixels - defaultSize) / 2;
            int defY = (dm.heightPixels - defaultSize) / 2;
            TouchCircleItem initial = new TouchCircleItem(1, "C1", defX, defY, 45, 2.50f, 3.50f);
            circleList.add(initial);
        }

        for (TouchCircleItem item : circleList) {
            attachCircleViews(item);
        }

        if (!circleList.isEmpty()) {
            selectedCircle = circleList.get(0);
        }
    }

    private void saveCirclesToPrefs() {
        try {
            JSONArray array = new JSONArray();
            for (TouchCircleItem item : circleList) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("label", item.label);
                obj.put("posX", item.posX);
                obj.put("posY", item.posY);
                obj.put("radiusDp", item.radiusDp);
                obj.put("sensiX", item.sensiX);
                obj.put("sensiY", item.sensiY);
                array.put(obj);
            }
            prefs.edit().putString(KEY_CIRCLES, array.toString()).apply();
        } catch (Throwable t) {
            Log.e(TAG, "Error saving circles to prefs", t);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 4. ATTACH CIRCLE VIEWS (DUAL-MODE: CONFIG DRAG & GAME INJECT)
    // ═══════════════════════════════════════════════════════════
    private void attachCircleViews(TouchCircleItem item) {
        int sizePx = dpToPx(item.radiusDp * 2);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        CircleVisualView visualView = new CircleVisualView(this);
        visualView.setCircleLabel(item.label);
        visualView.setConfigMode(isConfigMode);
        visualView.setSelected(item == selectedCircle);

        // Uses FLAG_NOT_FOCUSABLE in both modes:
        // - Touches outside this circle pass through natively to other game controls (joystick, aim, etc.)
        // - Touches inside this circle trigger the high-sensi injection directly into the game fire button
        WindowManager.LayoutParams visualParams = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        visualParams.gravity = Gravity.TOP | Gravity.START;
        visualParams.x = item.posX;
        visualParams.y = item.posY;

        visualView.setOnTouchListener(new View.OnTouchListener() {
            private int initialWindowX, initialWindowY;
            private float touchDownRawX, touchDownRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Ignore injected parallel events to prevent loopbacks
                if (event.getDeviceId() == -1 || event.getPointerId(event.getActionIndex()) == 19) {
                    return false;
                }

                if (isConfigMode) {
                    // CONFIG MODE: TAP TO SELECT & DRAG TO POSITION CIRCLE OVER FIRE BUTTON
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            selectCircle(item);
                            initialWindowX = item.visualParams.x;
                            initialWindowY = item.visualParams.y;
                            touchDownRawX = event.getRawX();
                            touchDownRawY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - touchDownRawX;
                            float dy = event.getRawY() - touchDownRawY;
                            int newX = initialWindowX + (int) dx;
                            int newY = initialWindowY + (int) dy;

                            item.posX = newX;
                            item.posY = newY;
                            item.visualParams.x = newX;
                            item.visualParams.y = newY;

                            windowManager.updateViewLayout(item.visualView, item.visualParams);
                            return true;

                        case MotionEvent.ACTION_UP:
                            return true;
                    }
                } else {
                    // GAME MODE: TOUCH INTERCEPTION WITH PARALLEL SHIZUKU INJECTION & SENSI MULTIPLIER
                    int targetUid = getTargetGameUid();
                    float rawX = event.getRawX();
                    float rawY = event.getRawY();

                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            item.lastRawX = rawX;
                            item.lastRawY = rawY;

                            // Calculate center of fire button under the circle
                            int circleSize = dpToPx(item.radiusDp * 2);
                            float centerFireX = item.posX + (circleSize / 2f);
                            float centerFireY = item.posY + (circleSize / 2f);

                            item.lastInjectedX = centerFireX;
                            item.lastInjectedY = centerFireY;

                            // Inject ACTION_DOWN to the game underneath via Shizuku UID 2000
                            injectMotionEvent(item.lastInjectedX, item.lastInjectedY, MotionEvent.ACTION_DOWN, targetUid);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            float deltaX = rawX - item.lastRawX;
                            float deltaY = rawY - item.lastRawY;

                            // Real working sensitivity multiplier X and Y (0.00 to 20.00)
                            float newInjectedX = item.lastInjectedX + (deltaX * item.sensiX);
                            float newInjectedY = item.lastInjectedY + (deltaY * item.sensiY);

                            // Inject ACTION_MOVE with high sensitivity to the game
                            injectMotionEvent(newInjectedX, newInjectedY, MotionEvent.ACTION_MOVE, targetUid);

                            item.lastRawX = rawX;
                            item.lastRawY = rawY;
                            item.lastInjectedX = newInjectedX;
                            item.lastInjectedY = newInjectedY;
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // Inject ACTION_UP to release game fire button
                            injectMotionEvent(item.lastInjectedX, item.lastInjectedY, MotionEvent.ACTION_UP, targetUid);
                            return true;
                    }
                }
                return false;
            }
        });

        item.visualView = visualView;
        item.visualParams = visualParams;
        windowManager.addView(visualView, visualParams);
    }

    private void updateCircleViewDimensions(TouchCircleItem item) {
        int sizePx = dpToPx(item.radiusDp * 2);

        if (item.visualParams != null && item.visualView != null) {
            item.visualParams.width = sizePx;
            item.visualParams.height = sizePx;
            windowManager.updateViewLayout(item.visualView, item.visualParams);
            item.visualView.invalidate();
        }
    }

    private void removeCircleView(TouchCircleItem item) {
        try {
            if (item.visualView != null) windowManager.removeView(item.visualView);
        } catch (Throwable ignored) {
        }
    }

    private void removeAllCircleViews() {
        for (TouchCircleItem item : circleList) {
            removeCircleView(item);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        saveCirclesToPrefs();

        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);

        try {
            if (floatingView != null) windowManager.removeView(floatingView);
            if (isBottomSheetShown && bottomSheetView != null) windowManager.removeView(bottomSheetView);
            removeAllCircleViews();
        } catch (Throwable ignored) {
        }

        try {
            Shizuku.unbindUserService(
                    new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), UserServiceInput.class.getName())),
                    shizukuConnection,
                    true
            );
        } catch (Throwable ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
