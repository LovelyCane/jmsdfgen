package lovely.cane.jmsdfgen;

public class DistanceMapping {
    private final double scale;
    private final double translate;

    public DistanceMapping() {
        scale = 1;
        translate = 0;
    }

    public DistanceMapping(Range range) {
        var rangeWidth = range.upper - range.lower;
        scale = 1.0 / rangeWidth;
        translate = -range.lower;
    }

    public DistanceMapping(double scale, double translate) {
        this.scale = scale;
        this.translate = translate;
    }

    public double apply(double d) {
        return scale * (d + translate);
    }

    public double applyDelta(Delta delta) {
        return scale * delta.value();
    }

    public DistanceMapping inverse() {
        return new DistanceMapping(1.0 / scale, -scale * translate);
    }

    public static DistanceMapping inverse(Range range) {
        var rangeWidth = range.upper - range.lower;
        return new DistanceMapping(rangeWidth, range.lower / (rangeWidth != 0 ? rangeWidth : 1));
    }

    public record Delta(double value) {
    }
}
