package lovely.cane.jmsdfgen;

public record MultiAndTrueDistance(double r, double g, double b, double a) {
    public MultiAndTrueDistance() {
        this(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
    }

    public static double resolveDistance(MultiAndTrueDistance distance) {
        return Arithmetic.median(distance.r(), distance.g(), distance.b());
    }
}
