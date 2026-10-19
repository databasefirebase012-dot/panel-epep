package xrans.hypnos;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

public class UserServiceInput extends Binder {

    public static final String DESCRIPTOR = "xrans.hypnos.IUserServiceInput";
    public static final int TRANSACTION_INJECT_EVENT = IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int TRANSACTION_GET_PID = IBinder.FIRST_CALL_TRANSACTION + 2;
    public static final int TRANSACTION_DESTROY = IBinder.FIRST_CALL_TRANSACTION + 3;
    public static final int TRANSACTION_APPLY_SETTINGS = IBinder.FIRST_CALL_TRANSACTION + 4;

    private static final String TAG = "HYPNOS_UserService";
    private final ShizukuInputInjector injector;

    public UserServiceInput() {
        this.injector = new ShizukuInputInjector();
        attachInterface(null, DESCRIPTOR);
        Log.i(TAG, "UserServiceInput started with PID: " + Process.myPid() + ", UID: " + Process.myUid());
    }

    public UserServiceInput(Context context) {
        this();
    }

    public void destroy() {
        Log.i(TAG, "UserServiceInput destroy called on PID: " + Process.myPid());
        System.exit(0);
    }

    public void applyGlobalTouchTuning(float sensiX, float sensiY) {
        try {
            Runtime rt = Runtime.getRuntime();
            // Max pointer speed (ranges -7 to 7, 7 is maximum sensitivity in Android system)
            rt.exec(new String[]{"sh", "-c", "settings put system pointer_speed 7"}).waitFor();
            // Touch sensitivity boost (glove mode / hyper touch sensitivity)
            rt.exec(new String[]{"sh", "-c", "settings put system touch_sensitivity 1"}).waitFor();
            // Low touch latency timeouts
            rt.exec(new String[]{"sh", "-c", "settings put secure long_press_timeout 100"}).waitFor();
            rt.exec(new String[]{"sh", "-c", "settings put secure multi_press_timeout 100"}).waitFor();
            // Instant touch responsiveness
            rt.exec(new String[]{"sh", "-c", "settings put system view_touch_slop 1"}).waitFor();
            // Reduce animations for fastest response
            rt.exec(new String[]{"sh", "-c", "settings put global window_animation_scale 0.5"}).waitFor();
            rt.exec(new String[]{"sh", "-c", "settings put global transition_animation_scale 0.5"}).waitFor();
            rt.exec(new String[]{"sh", "-c", "settings put global animator_duration_scale 0.5"}).waitFor();

            Log.i(TAG, "Applied Shizuku touch tuning: SensiX=" + sensiX + " SensiY=" + sensiY);
        } catch (Throwable t) {
            Log.e(TAG, "Failed applying touch tuning via Shizuku shell", t);
        }
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        switch (code) {
            case INTERFACE_TRANSACTION:
                reply.writeString(DESCRIPTOR);
                return true;

            case TRANSACTION_INJECT_EVENT: {
                data.enforceInterface(DESCRIPTOR);
                float x = data.readFloat();
                float y = data.readFloat();
                int action = data.readInt();
                int targetUid = (data.dataAvail() > 0) ? data.readInt() : -1;
                boolean success = injector.injectEvent(x, y, action, targetUid);
                reply.writeNoException();
                reply.writeInt(success ? 1 : 0);
                return true;
            }

            case TRANSACTION_GET_PID: {
                data.enforceInterface(DESCRIPTOR);
                reply.writeNoException();
                reply.writeInt(Process.myPid());
                return true;
            }

            case TRANSACTION_APPLY_SETTINGS: {
                data.enforceInterface(DESCRIPTOR);
                float sx = data.readFloat();
                float sy = data.readFloat();
                applyGlobalTouchTuning(sx, sy);
                reply.writeNoException();
                return true;
            }

            case TRANSACTION_DESTROY: {
                data.enforceInterface(DESCRIPTOR);
                reply.writeNoException();
                destroy();
                return true;
            }

            default:
                return super.onTransact(code, data, reply, flags);
        }
    }
}
