package lovely.cane.jmsdfgen;

public class Range {
    public double lower, upper;

    public Range() {
        lower = 0;
        upper = 0;
    }

    public Range(double symmetricalWidth) {
        this(-0.5 * symmetricalWidth, 0.5 * symmetricalWidth);
    }

    public Range(double lowerBound, double upperBound) {
        lower = lowerBound;
        upper = upperBound;
    }

    public Range mulEquals(double factor) {
        lower *= factor;
        upper *= factor;
        return this;
    }

    public Range divEquals(double divisor) {
        lower /= divisor;
        upper /= divisor;
        return this;
    }

    public Range multiply(double factor) {
        return new Range(lower * factor, upper * factor);
    }

    public Range divide(double divisor) {
        return new Range(lower / divisor, upper / divisor);
    }

    public static Range multiply(double factor, Range range) {
        return new Range(factor * range.lower, factor * range.upper);
    }
}
