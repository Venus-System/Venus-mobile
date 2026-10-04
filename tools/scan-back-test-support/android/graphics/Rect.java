package android.graphics;

/** JVM-only geometry double. Never included in the Android app source sets. */
public final class Rect {
    public int left, top, right, bottom;
    public Rect(int left, int top, int right, int bottom) {
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
    }
    public Rect(Rect other) { this(other.left, other.top, other.right, other.bottom); }
    public int centerX() { return (left + right) >> 1; }
    public int centerY() { return (top + bottom) >> 1; }
    public int width() { return right - left; }
    public int height() { return bottom - top; }
}
