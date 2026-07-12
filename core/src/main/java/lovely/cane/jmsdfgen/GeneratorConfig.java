package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;

public class GeneratorConfig {
    public boolean overlapSupport;

    public GeneratorConfig() {
        this(true);
    }

    public GeneratorConfig(boolean overlapSupport) {
        this.overlapSupport = overlapSupport;
    }

    public static final class ErrorCorrectionConfig {
        public static final double DEFAULT_MIN_DEVIATION_RATIO = 1.11111111111111111;
        public static final double DEFAULT_MIN_IMPROVE_RATIO = 1.11111111111111111;
        public Mode mode;
        public DistanceCheckMode distanceCheckMode;
        public double minDeviationRatio;
        public double minImproveRatio;
        private final byte @Nullable [] buffer;

        public ErrorCorrectionConfig(Mode mode, DistanceCheckMode distanceCheckMode, double minDeviationRatio,
                                     double minImproveRatio, byte @Nullable [] buffer) {
            this.mode = mode;
            this.distanceCheckMode = distanceCheckMode;
            this.minDeviationRatio = minDeviationRatio;
            this.minImproveRatio = minImproveRatio;
            this.buffer = buffer;
        }

        public enum Mode {
            DISABLED,
            INDISCRIMINATE,
            EDGE_PRIORITY,
            EDGE_ONLY
        }

        public enum DistanceCheckMode {
            DO_NOT_CHECK_DISTANCE,
            CHECK_DISTANCE_AT_EDGE,
            ALWAYS_CHECK_DISTANCE
        }

        public ErrorCorrectionConfig() {
            this(Mode.EDGE_PRIORITY, DistanceCheckMode.CHECK_DISTANCE_AT_EDGE, DEFAULT_MIN_DEVIATION_RATIO, DEFAULT_MIN_IMPROVE_RATIO, null);
        }

        public Mode mode() {
            return mode;
        }

        public DistanceCheckMode distanceCheckMode() {
            return distanceCheckMode;
        }

        public double minDeviationRatio() {
            return minDeviationRatio;
        }

        public double minImproveRatio() {
            return minImproveRatio;
        }

        public byte @Nullable [] buffer() {
            return buffer;
        }
    }

    public static class MSDFGeneratorConfig extends GeneratorConfig {
        public final ErrorCorrectionConfig errorCorrection;

        public MSDFGeneratorConfig() {
            overlapSupport = true;
            errorCorrection = new ErrorCorrectionConfig();
        }

        public MSDFGeneratorConfig(boolean overlapSupport, ErrorCorrectionConfig errorCorrection) {
            super(overlapSupport);
            this.errorCorrection = errorCorrection;
        }
    }
}
