package lovely.cane.jmsdfgen;

public sealed interface DistancePixelConversion<D>
        permits
        DistancePixelConversion.DistancePixelConversionDouble,
        DistancePixelConversion.DistancePixelConversionMultiDistance,
        DistancePixelConversion.DistancePixelConversionMultiAndTrueDistance {
    void distancePixelConversion(Bitmap.BitmapSection<Float> output, int x, int y, D distance);

    record DistancePixelConversionDouble(
            DistanceMapping mapping
    ) implements DistancePixelConversion<Double> {
        @Override
        public void distancePixelConversion(Bitmap.BitmapSection<Float> output, int x, int y, Double distance) {
            output.setPixel(x, y, (float) mapping.apply(distance));
        }
    }

    record DistancePixelConversionMultiAndTrueDistance(
            DistanceMapping mapping
    ) implements DistancePixelConversion<MultiAndTrueDistance> {
        @Override
        public void distancePixelConversion(Bitmap.BitmapSection<Float> output, int x, int y, MultiAndTrueDistance distance) {
            var r = (float) mapping.apply(distance.r());
            var g = (float) mapping.apply(distance.g());
            var b = (float) mapping.apply(distance.b());
            var a = (float) mapping.apply(distance.a());
            output.setPixel(x, y, r, g, b, a);
        }
    }

    record DistancePixelConversionMultiDistance(
            DistanceMapping mapping
    ) implements DistancePixelConversion<MultiDistance> {
        @Override
        public void distancePixelConversion(Bitmap.BitmapSection<Float> output, int x, int y, MultiDistance distance) {
            var r = (float) mapping.apply(distance.r());
            var g = (float) mapping.apply(distance.g());
            var b = (float) mapping.apply(distance.b());
            output.setPixel(x, y, r, g, b);
        }
    }
}
