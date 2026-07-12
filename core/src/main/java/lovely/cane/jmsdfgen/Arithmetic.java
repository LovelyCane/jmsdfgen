package lovely.cane.jmsdfgen;

public final class Arithmetic {
    private Arithmetic() {
    }

    public static double clamp(double n) {
        return n >= 0.0 && n <= 1.0 ? n : (n > 0 ? 1.0 : 0.0);
    }

    public static float clamp(float n) {
        return n >= 0.0f && n <= 1.0f ? n : (n > 0f ? 1.0f : 0.0f);
    }

    public static float clamp(float n, float b) {
        return (n >= 0 && n <= b) ? n : ((n > 0 ? 1 : 0) * b);
    }

    public static double clamp(double n, double b) {
        return (n >= 0 && n <= b) ? n : ((n > 0 ? 1 : 0) * b);
    }

    public static int clamp(int n, int b) {
        return (n >= 0 && n <= b) ? n : ((n > 0 ? 1 : 0) * b);
    }

    public static double clamp(double n, double a, double b) {
        return n >= a && n <= b ? n : (n < a ? a : b);
    }

    public static float clamp(float n, float a, float b) {
        return n >= a && n <= b ? n : (n < a ? a : b);
    }

    @SuppressWarnings("MathClampMigration")
    public static float median(float a, float b, float c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    @SuppressWarnings("MathClampMigration")
    public static double median(double a, double b, double c) {
        return Math.max(Math.min(a, b), Math.min(Math.max(a, b), c));
    }

    public static double mix(double a, double b, double weight) {
        return (1.0 - weight) * a + weight * b;
    }

    public static float mix(float a, float b, float weight) {
        return (1.0f - weight) * a + weight * b;
    }

    public static Vector2 mix(Vector2 a, Vector2 b, double t) {
        return new Vector2((1 - t) * a.x + t * b.x, (1 - t) * a.y + t * b.y);
    }

    public static int sign(double n) {
        return (0 < n ? 1 : 0) - (n < 0 ? 1 : 0);
    }

    public static int nonZeroSign(double n) {
        return n > 0 ? 1 : -1;
    }

    public static void boundPoint(double[] xMin, double[] yMin, double[] xMax, double[] yMax, Vector2 p) {
        if (p.x < xMin[0]) xMin[0] = p.x;
        if (p.y < yMin[0]) yMin[0] = p.y;
        if (p.x > xMax[0]) xMax[0] = p.x;
        if (p.y > yMax[0]) yMax[0] = p.y;
    }

    public static byte pixelFloatToByte(float x) {
        return (byte) (~(int) (255.5f - 255.0f * clamp(x)));
    }

    public static float pixelByteToFloat(byte x) {
        return (1f / 255f) * (x & 0xFF);
    }
}
