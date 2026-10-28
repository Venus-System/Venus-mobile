package android.util;

public final class Log {
    public static int d(String tag, String message) {
        if (Boolean.getBoolean("scan.trace")) System.out.println(message);
        return 0;
    }
}
