package xrans.hypnos;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.BuildConfig;
import com.example.R;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final String TAG = "HYPNOS_MainActivity";
    private static final int REQ_OVERLAY_PERMISSION = 2001;
    private static final int REQ_WRITE_SETTINGS_PERMISSION = 2002;
    private static final int SHIZUKU_PERMISSION_CODE = 2003;

    public static final String PKG_FF_MAX = "com.dts.freefiremax";
    public static final String PKG_FF_ORIGINAL = "com.dts.freefireth";

    private TextView tvPidStatus;
    private TextView tvShizukuHint;
    private LinearLayout boxPid;

    private LinearLayout rowPermOverlay;
    private TextView tvStatusOverlay;
    private LinearLayout rowPermWriteSettings;
    private TextView tvStatusWriteSettings;

    private LinearLayout cardFfMax;
    private ImageView imgFfMaxIcon;
    private TextView tvFfMaxStatus;

    private LinearLayout cardFfOriginal;
    private ImageView imgFfOriginalIcon;
    private TextView tvFfOriginalStatus;

    private Button btnStart;

    private String selectedGamePackage = null;
    private boolean isFfMaxInstalled = false;
    private boolean isFfOriginalInstalled = false;

    private UserServiceClient userServiceClient;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode == SHIZUKU_PERMISSION_CODE) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        checkShizukuState();
                    } else {
                        updatePidUi(-1, "IZIN DITOLAK");
                    }
                }
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        Log.i(TAG, "Shizuku binder received");
        checkShizukuState();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        Log.w(TAG, "Shizuku binder dead");
        updatePidUi(-1, "TERPUTUS");
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            userServiceClient = new UserServiceClient(service);
            int pid = userServiceClient.getPid();
            Log.i(TAG, "UserService bound successfully, PID: " + pid);
            if (pid > 0) {
                updatePidUi(pid, null);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            userServiceClient = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        detectInstalledGames();
        setupListeners();

        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkShizukuState();
        checkPermissionsState();
    }

    private void initViews() {
        boxPid = findViewById(R.id.boxPid);
        tvPidStatus = findViewById(R.id.tvPidStatus);
        tvShizukuHint = findViewById(R.id.tvShizukuHint);

        rowPermOverlay = findViewById(R.id.rowPermOverlay);
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay);
        rowPermWriteSettings = findViewById(R.id.rowPermWriteSettings);
        tvStatusWriteSettings = findViewById(R.id.tvStatusWriteSettings);

        cardFfMax = findViewById(R.id.cardFfMax);
        imgFfMaxIcon = findViewById(R.id.imgFfMaxIcon);
        tvFfMaxStatus = findViewById(R.id.tvFfMaxStatus);

        cardFfOriginal = findViewById(R.id.cardFfOriginal);
        imgFfOriginalIcon = findViewById(R.id.imgFfOriginalIcon);
        tvFfOriginalStatus = findViewById(R.id.tvFfOriginalStatus);

        btnStart = findViewById(R.id.btnStart);
    }

    private void detectInstalledGames() {
        PackageManager pm = getPackageManager();

        // Check Free Fire MAX
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(PKG_FF_MAX, 0);
            Drawable icon = pm.getApplicationIcon(appInfo);
            imgFfMaxIcon.setImageDrawable(icon);
            tvFfMaxStatus.setText("TERPASANG");
            tvFfMaxStatus.setTextColor(Color.parseColor("#E53935"));
            isFfMaxInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            imgFfMaxIcon.setImageResource(R.drawable.ic_cross_dim);
            tvFfMaxStatus.setText("TIDAK TERPASANG");
            tvFfMaxStatus.setTextColor(Color.parseColor("#888899"));
            isFfMaxInstalled = false;
        }

        // Check Free Fire Original
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(PKG_FF_ORIGINAL, 0);
            Drawable icon = pm.getApplicationIcon(appInfo);
            imgFfOriginalIcon.setImageDrawable(icon);
            tvFfOriginalStatus.setText("TERPASANG");
            tvFfOriginalStatus.setTextColor(Color.parseColor("#E53935"));
            isFfOriginalInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            imgFfOriginalIcon.setImageResource(R.drawable.ic_cross_dim);
            tvFfOriginalStatus.setText("TIDAK TERPASANG");
            tvFfOriginalStatus.setTextColor(Color.parseColor("#888899"));
            isFfOriginalInstalled = false;
        }
    }

    private void setupListeners() {
        boxPid.setOnClickListener(v -> {
            if (!ShizukuHelper.isShizukuAvailable()) {
                Toast.makeText(this, "BUKA APLIKASI SHIZUKU & AKTIFKAN LAYANAN", Toast.LENGTH_LONG).show();
                return;
            }
            if (!ShizukuHelper.hasShizukuPermission()) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE);
            } else {
                checkShizukuState();
                Toast.makeText(this, "SHIZUKU CORE AKTIF", Toast.LENGTH_SHORT).show();
            }
        });

        rowPermOverlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
            } else {
                Toast.makeText(this, "IZIN OVERLAY SUDAH AKTIF", Toast.LENGTH_SHORT).show();
            }
        });

        rowPermWriteSettings.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_WRITE_SETTINGS_PERMISSION);
            } else {
                Toast.makeText(this, "IZIN WRITE SETTINGS SUDAH AKTIF", Toast.LENGTH_SHORT).show();
            }
        });

        cardFfMax.setOnClickListener(v -> selectGame(PKG_FF_MAX));
        cardFfOriginal.setOnClickListener(v -> selectGame(PKG_FF_ORIGINAL));

        btnStart.setOnClickListener(v -> handleStartAction());
    }

    private void selectGame(String pkg) {
        selectedGamePackage = pkg;
        getSharedPreferences(OverlayService.PREF_NAME, MODE_PRIVATE)
                .edit()
                .putString(OverlayService.KEY_SELECTED_GAME, pkg)
                .apply();

        if (PKG_FF_MAX.equals(pkg)) {
            cardFfMax.setBackgroundResource(R.drawable.bg_glass_card_active);
            cardFfOriginal.setBackgroundResource(R.drawable.bg_glass_card);
        } else {
            cardFfOriginal.setBackgroundResource(R.drawable.bg_glass_card_active);
            cardFfMax.setBackgroundResource(R.drawable.bg_glass_card);
        }

        btnStart.setEnabled(true);
    }

    private void checkPermissionsState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean canDraw = Settings.canDrawOverlays(this);
            tvStatusOverlay.setText(canDraw ? "AKTIF" : "TAP UNTUK IZIN");
            tvStatusOverlay.setTextColor(canDraw ? Color.parseColor("#FFFFFF") : Color.parseColor("#E53935"));

            boolean canWrite = Settings.System.canWrite(this);
            tvStatusWriteSettings.setText(canWrite ? "AKTIF" : "TAP UNTUK IZIN");
            tvStatusWriteSettings.setTextColor(canWrite ? Color.parseColor("#FFFFFF") : Color.parseColor("#E53935"));
        }
    }

    private void handleStartAction() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_OVERLAY_PERMISSION);
                return;
            }

            if (!Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQ_WRITE_SETTINGS_PERMISSION);
                return;
            }
        }

        applyTouchSettings();

        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Toast.makeText(this, "HYPNOS PELICIN AKTIF", Toast.LENGTH_SHORT).show();

        if (selectedGamePackage != null) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(selectedGamePackage);
            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        }
    }

    private void applyTouchSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)) {
                Settings.System.putInt(getContentResolver(), "pointer_speed", 7);
                Settings.System.putInt(getContentResolver(), "touch_sensitivity", 1);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply system settings", t);
        }

        if (userServiceClient != null && userServiceClient.isAlive()) {
            userServiceClient.applyGlobalTouchTuning(2.50f, 3.50f);
        }
    }

    private void checkShizukuState() {
        try {
            if (ShizukuHelper.isShizukuAvailable()) {
                if (ShizukuHelper.hasShizukuPermission()) {
                    int uid = ShizukuHelper.getShizukuUid();
                    updatePidUi(Process.myPid(), "CORE AKTIF (UID: " + uid + ")");
                    bindUserService();
                } else {
                    updatePidUi(-1, "IZIN DIPERLUKAN");
                    tvShizukuHint.setText("TAP UNTUK MINTA IZIN SHIZUKU");
                }
            } else {
                updatePidUi(-1, "TERPUTUS");
                tvShizukuHint.setText("BUKA APLIKASI SHIZUKU & AKTIFKAN");
            }
        } catch (Throwable t) {
            updatePidUi(-1, "TERPUTUS");
        }
    }

    private void bindUserService() {
        try {
            Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                    new ComponentName(getPackageName(), UserServiceInput.class.getName())
            )
                    .daemon(false)
                    .processNameSuffix("input_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);

            Shizuku.bindUserService(args, serviceConnection);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to bind UserService", t);
        }
    }

    private void updatePidUi(int pid, String fallbackText) {
        runOnUiThread(() -> {
            if (pid > 0) {
                tvPidStatus.setText("LAYANAN: " + pid);
                tvPidStatus.setTextColor(Color.parseColor("#E53935"));
                tvShizukuHint.setText("UID 2000 TERHUBUNG & WORK");
                tvShizukuHint.setTextColor(Color.parseColor("#888899"));
            } else {
                tvPidStatus.setText(fallbackText != null ? fallbackText : "TERPUTUS");
                tvPidStatus.setTextColor(Color.parseColor("#888899"));
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);

        try {
            Shizuku.unbindUserService(
                    new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), UserServiceInput.class.getName())),
                    serviceConnection,
                    true
            );
        } catch (Throwable ignored) {
        }
    }
}
