package xrans.hypnos;

import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;
import rikka.shizuku.Shizuku;

public class ShizukuHelper {

    private static final String TAG = "HYPNOS_ShizukuHelper";

    public static boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasShizukuPermission() {
        try {
            if (!isShizukuAvailable()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getShizukuUid() {
        try {
            if (hasShizukuPermission()) {
                return Shizuku.getUid();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static int getShizukuVersion() {
        try {
            if (isShizukuAvailable()) {
                return Shizuku.getVersion();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
