package android.util;

/** JVM-only logger double. Does not validate Android logging or ML Kit behavior. */
public final class Log {
    public static int d(String tag, String message) {
        if (Boolean.getBoolean("scan.trace")) System.out.println(message);
        return 0;
    }
}
