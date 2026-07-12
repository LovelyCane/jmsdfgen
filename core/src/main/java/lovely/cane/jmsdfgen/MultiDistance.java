package lovely.cane.jmsdfgen;

public record MultiDistance(double r, double g, double b) {
    public MultiDistance() {
        this(-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE);
    }

    public static double resolveDistance(MultiDistance distance) {
        return Arithmetic.median(distance.r(), distance.g(), distance.b());
    }
}
