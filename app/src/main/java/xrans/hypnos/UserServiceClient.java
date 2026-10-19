package xrans.hypnos;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

public class UserServiceClient {

    private static final String TAG = "UserServiceClient";
    private final IBinder binder;

    public UserServiceClient(IBinder binder) {
        this.binder = binder;
    }

    public boolean isAlive() {
        return binder != null && binder.isBinderAlive();
    }

    public boolean injectEvent(float x, float y, int action) {
        return injectEvent(x, y, action, -1);
    }

    public boolean injectEvent(float x, float y, int action, int targetUid) {
        if (!isAlive()) return false;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(UserServiceInput.DESCRIPTOR);
            data.writeFloat(x);
            data.writeFloat(y);
            data.writeInt(action);
            data.writeInt(targetUid);
            binder.transact(UserServiceInput.TRANSACTION_INJECT_EVENT, data, reply, 0);
            reply.readException();
            return reply.readInt() == 1;
        } catch (Throwable t) {
            Log.e(TAG, "injectEvent remote call failed", t);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public int getPid() {
        if (!isAlive()) return -1;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(UserServiceInput.DESCRIPTOR);
            binder.transact(UserServiceInput.TRANSACTION_GET_PID, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Throwable t) {
            Log.e(TAG, "getPid remote call failed", t);
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public void applyGlobalTouchTuning(float sensiX, float sensiY) {
        if (!isAlive()) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(UserServiceInput.DESCRIPTOR);
            data.writeFloat(sensiX);
            data.writeFloat(sensiY);
            binder.transact(UserServiceInput.TRANSACTION_APPLY_SETTINGS, data, reply, 0);
            reply.readException();
        } catch (Throwable t) {
            Log.e(TAG, "applyGlobalTouchTuning failed", t);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    public void destroyService() {
        if (!isAlive()) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(UserServiceInput.DESCRIPTOR);
            binder.transact(UserServiceInput.TRANSACTION_DESTROY, data, reply, 0);
            reply.readException();
        } catch (Throwable ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
