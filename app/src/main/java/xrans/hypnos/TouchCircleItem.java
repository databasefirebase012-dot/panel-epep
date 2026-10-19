package xrans.hypnos;

import android.view.WindowManager;

public class TouchCircleItem {

    public int id;
    public String label;
    public int posX;
    public int posY;
    public int radiusDp;
    public float sensiX;
    public float sensiY;

    // View references
    public CircleVisualView visualView;
    public WindowManager.LayoutParams visualParams;

    // Last touch positions for Shizuku parallel injection
    public float lastRawX = 0f;
    public float lastRawY = 0f;
    public float lastInjectedX = 0f;
    public float lastInjectedY = 0f;

    public TouchCircleItem(int id, String label, int posX, int posY, int radiusDp, float sensiX, float sensiY) {
        this.id = id;
        this.label = label;
        this.posX = posX;
        this.posY = posY;
        this.radiusDp = radiusDp;
        this.sensiX = sensiX;
        this.sensiY = sensiY;
    }

    public TouchCircleItem copy() {
        return new TouchCircleItem(id, label, posX, posY, radiusDp, sensiX, sensiY);
    }
}
