package lovely.cane.jmsdfgen;

public final class Vector2 {
    public double x;
    public double y;

    public Vector2() {
        this(0);
    }

    public Vector2(double val) {
        this(val, val);
    }

    public Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void reset() {
        x = 0;
        y = 0;
    }

    public void set(double newX, double newY) {
        x = newX;
        y = newY;
    }

    public double squaredLength() {
        return x * x + y * y;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2 normalize() {
        return normalize(false);
    }

    public Vector2 normalize(boolean allowZero) {
        var len = length();
        if (len != 0) return new Vector2(x / len, y / len);
        return new Vector2(0, allowZero ? 0 : 1);
    }

    public Vector2 getOrthogonal() {
        return getOrthogonal(true);
    }

    @SuppressWarnings("SuspiciousNameCombination")
    public Vector2 getOrthogonal(boolean polarity) {
        return polarity ? new Vector2(-y, x) : new Vector2(y, -x);
    }

    public Vector2 getOrthonormal() {
        return getOrthonormal(true, false);
    }

    public Vector2 getOrthonormal(boolean polarity) {
        return getOrthonormal(polarity, false);
    }

    public Vector2 getOrthonormal(boolean polarity, boolean allowZero) {
        var len = length();
        if (len != 0) {
            if (polarity) return new Vector2(-y / len, x / len);
            else return new Vector2(y / len, -x / len);
        }
        if (polarity) return new Vector2(0, allowZero ? 0 : 1);
        else return new Vector2(0, allowZero ? 0 : -1);
    }

    public boolean isNonZero() {
        return x != 0 || y != 0;
    }

    public Vector2 add(Vector2 other) {
        x += other.x;
        y += other.y;
        return this;
    }

    public Vector2 subtract(Vector2 other) {
        x -= other.x;
        y -= other.y;
        return this;
    }

    public Vector2 multiply(Vector2 other) {
        x *= other.x;
        y *= other.y;
        return this;
    }

    public Vector2 divide(Vector2 other) {
        x /= other.x;
        y /= other.y;
        return this;
    }

    public Vector2 multiply(double value) {
        x *= value;
        y *= value;
        return this;
    }

    public Vector2 divide(double value) {
        x /= value;
        y /= value;
        return this;
    }

    public static double dotProduct(Vector2 a, Vector2 b) {
        return a.x * b.x + a.y * b.y;
    }

    public static double crossProduct(Vector2 a, Vector2 b) {
        return a.x * b.y - a.y * b.x;
    }

    public static boolean equals(Vector2 a, Vector2 b) {
        return a.x == b.x && a.y == b.y;
    }

    public static boolean notEquals(Vector2 a, Vector2 b) {
        return a.x != b.x || a.y != b.y;
    }

    public static Vector2 plus(Vector2 v) {
        return new Vector2(v.x, v.y);
    }

    public static Vector2 negate(Vector2 v) {
        return new Vector2(-v.x, -v.y);
    }

    public static boolean isZero(Vector2 v) {
        return v.x == 0 && v.y == 0;
    }

    public static Vector2 add(Vector2 a, Vector2 b) {
        return new Vector2(a.x + b.x, a.y + b.y);
    }

    public static Vector2 subtract(Vector2 a, Vector2 b) {
        return new Vector2(a.x - b.x, a.y - b.y);
    }

    public static Vector2 multiply(Vector2 a, Vector2 b) {
        return new Vector2(a.x * b.x, a.y * b.y);
    }

    public static Vector2 divide(Vector2 a, Vector2 b) {
        return new Vector2(a.x / b.x, a.y / b.y);
    }

    public static Vector2 multiply(double a, Vector2 b) {
        return new Vector2(a * b.x, a * b.y);
    }

    public static Vector2 divide(double a, Vector2 b) {
        return new Vector2(a / b.x, a / b.y);
    }

    public static Vector2 multiply(Vector2 a, double b) {
        return new Vector2(a.x * b, a.y * b);
    }

    public static Vector2 divide(Vector2 a, double b) {
        return new Vector2(a.x / b, a.y / b);
    }

    public static Vector2 copy(Vector2 v) {
        return new Vector2(v.x, v.y);
    }
}
