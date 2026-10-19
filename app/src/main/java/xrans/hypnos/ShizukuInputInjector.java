package xrans.hypnos;

import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

public class ShizukuInputInjector {

    private static final String TAG = "HYPNOS_Injector";
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;

    private Object iInputManager;
    private Method injectInputEventMethod;
    private Method injectInputEventToTargetMethod;
    private long downTime = 0L;

    public ShizukuInputInjector() {
        initReflection();
    }

    private void initReflection() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "input");

            if (binder == null) {
                Log.e(TAG, "ServiceManager.getService(\"input\") returned null");
                return;
            }

            Class<?> stubClass = Class.forName("android.hardware.input.IInputManager$Stub");
            Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
            iInputManager = asInterface.invoke(null, binder);

            if (iInputManager == null) {
                Log.e(TAG, "IInputManager.Stub.asInterface returned null");
                return;
            }

            for (Method m : iInputManager.getClass().getMethods()) {
                String name = m.getName();
                Class<?>[] params = m.getParameterTypes();

                if ("injectInputEventToTarget".equals(name) && params.length >= 3 && InputEvent.class.isAssignableFrom(params[0])) {
                    m.setAccessible(true);
                    injectInputEventToTargetMethod = m;
                    Log.i(TAG, "Found injectInputEventToTarget: " + m);
                } else if ("injectInputEvent".equals(name) && params.length >= 2 && InputEvent.class.isAssignableFrom(params[0])) {
                    m.setAccessible(true);
                    injectInputEventMethod = m;
                    Log.i(TAG, "Found injectInputEvent: " + m);
                }
            }
            Log.i(TAG, "IInputManager reflection ready (toTarget=" + (injectInputEventToTargetMethod != null)
                    + ", standard=" + (injectInputEventMethod != null) + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to reflect IInputManager", t);
        }
    }

    public synchronized boolean injectEvent(float x, float y, int action) {
        return injectEvent(x, y, action, Process.INVALID_UID);
    }

    /**
     * Injects a MotionEvent with UID 2000 / ADB privileges.
     * Uses deviceId = -1 and pointerId = 19 (parallel virtual stream) as requested.
     * Passes targetUid so the event reaches the underlying game window without being blocked.
     */
    public synchronized boolean injectEvent(float x, float y, int action, int targetUid) {
        if (iInputManager == null || (injectInputEventMethod == null && injectInputEventToTargetMethod == null)) {
            initReflection();
            if (iInputManager == null) {
                return false;
            }
        }

        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) {
            downTime = now;
        } else if (downTime == 0L) {
            downTime = now;
        }

        float pressure = (action == MotionEvent.ACTION_UP) ? 0.0f : 1.0f;
        float size = 1.0f;

        MotionEvent event = null;
        try {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
            properties[0] = new MotionEvent.PointerProperties();
            properties[0].id = 19;
            properties[0].toolType = MotionEvent.TOOL_TYPE_FINGER;

            MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
            coords[0] = new MotionEvent.PointerCoords();
            coords[0].x = x;
            coords[0].y = y;
            coords[0].pressure = pressure;
            coords[0].size = size;

            event = MotionEvent.obtain(
                    downTime,
                    now,
                    action,
                    1,
                    properties,
                    coords,
                    0,    // metaState
                    0,    // buttonState
                    1.0f, // xPrecision
                    1.0f, // yPrecision
                    -1,   // deviceId = -1 (virtual parallel stream)
                    0,    // edgeFlags
                    InputDevice.SOURCE_TOUCHSCREEN,
                    0     // flags
            );
        } catch (Throwable t) {
            event = MotionEvent.obtain(
                    downTime,
                    now,
                    action,
                    x,
                    y,
                    pressure,
                    size,
                    0,
                    1.0f,
                    1.0f,
                    0,
                    0
            );
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        }

        try {
            int effectiveUid = (targetUid > 0) ? targetUid : Process.INVALID_UID;

            // 1. Try injectInputEventToTarget if available
            if (injectInputEventToTargetMethod != null) {
                try {
                    Object res = injectInputEventToTargetMethod.invoke(
                            iInputManager,
                            event,
                            INJECT_INPUT_EVENT_MODE_ASYNC,
                            effectiveUid
                    );
                    if (res instanceof Boolean && (Boolean) res) {
                        return true;
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "injectInputEventToTarget invoke failed", t);
                }
            }

            // 2. Try injectInputEvent
            if (injectInputEventMethod != null) {
                Class<?>[] paramTypes = injectInputEventMethod.getParameterTypes();
                if (paramTypes.length == 2) {
                    injectInputEventMethod.invoke(iInputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC);
                    return true;
                } else if (paramTypes.length == 3) {
                    injectInputEventMethod.invoke(iInputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC, effectiveUid);
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "injectEvent execution failed", t);
            return false;
        } finally {
            if (event != null) {
                event.recycle();
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                downTime = 0L;
            }
        }
    }
}
