package lovely.cane.jmsdfgen;

public final class Rasterization {
    private Rasterization() {
    }

    public static void rasterize(Bitmap.BitmapSection<Float> output, Shape shape,
                                 Projection projection, Scanline.FillRule fillRule) {
        output.reorient(shape.getYAxisOrientation());
        var scanline = new Scanline();
        for (var y = 0; y < output.height; y++) {
            shape.scanline(scanline, projection.unprojectY(y + 0.5));
            for (var x = 0; x < output.width; x++) {
                output.setPixel(x, y, scanline.filled(projection.unprojectX(x + .5), fillRule) ? 1.0f : 0.0f);
            }
        }
    }

    public static void distanceSignCorrection(Bitmap.BitmapSection<Float> sdf, Shape shape,
                                              Projection projection, float fillValue, Scanline.FillRule fillRule) {
        sdf.reorient(shape.getYAxisOrientation());
        if (sdf.channels == 1) {
            distanceSignCorrectionSingle(sdf, shape, projection, fillValue, fillRule);
        } else {
            multiDistanceSignCorrection(sdf, shape, projection, fillValue, fillRule);
        }
    }

    public static void distanceSignCorrectionSingle(Bitmap.BitmapSection<Float> sdf, Shape shape,
                                                    Projection projection, float fillValue, Scanline.FillRule fillRule) {
        var scanline = new Scanline();
        for (var y = 0; y < sdf.height; y++) {
            var row = projection.unprojectY(y + 0.5);
            shape.scanline(scanline, row);
            for (var x = 0; x < sdf.width; x++) {
                var fill = scanline.filled(projection.unprojectX(x + 0.5), fillRule);
                var idx = sdf.getPixelIndex(x, y);
                float sd = sdf.pixels[idx];
                if ((sd > fillValue) != fill) {
                    sdf.pixels[idx] = 2.0f * fillValue - sd;
                }
            }
        }
    }

    public static void multiDistanceSignCorrection(Bitmap.BitmapSection<Float> sdf, Shape shape,
                                                   Projection projection, float fillValue, Scanline.FillRule fillRule) {
        int w = sdf.width, h = sdf.height;
        if (w == 0 || h == 0) return;
        sdf.reorient(shape.getYAxisOrientation());
        var doubleFillValue = fillValue + fillValue;
        var scanline = new Scanline();
        var ambiguous = false;
        var match = new byte[w * h];
        for (var y = 0; y < h; y++) {
            shape.scanline(scanline, projection.unprojectY(y + 0.5));
            for (var x = 0; x < w; x++) {
                var fill = scanline.filled(projection.unprojectX(x + 0.5), fillRule);
                var idx = sdf.getPixelIndex(x, y);
                var sd = Arithmetic.median(sdf.pixels[idx], sdf.pixels[idx + 1], sdf.pixels[idx + 2]);
                if (sd == fillValue) {
                    ambiguous = true;
                } else if ((sd > fillValue) != fill) {
                    sdf.pixels[idx] = doubleFillValue - sdf.pixels[idx];
                    sdf.pixels[idx + 1] = doubleFillValue - sdf.pixels[idx + 1];
                    sdf.pixels[idx + 2] = doubleFillValue - sdf.pixels[idx + 2];
                    match[y * w + x] = -1;
                } else {
                    match[y * w + x] = 1;
                }
                if (sdf.channels >= 4) {
                    float a = sdf.pixels[idx + 3];
                    if ((a > fillValue) != fill) {
                        sdf.pixels[idx + 3] = doubleFillValue - a;
                    }
                }
            }
        }
        if (ambiguous) {
            for (var y = 0; y < h; y++) {
                for (var x = 0; x < w; x++) {
                    var mi = y * w + x;
                    if (match[mi] == 0) {
                        var neighborMatch = 0;
                        if (x > 0) neighborMatch += match[mi - 1];
                        if (x < w - 1) neighborMatch += match[mi + 1];
                        if (y > 0) neighborMatch += match[mi - w];
                        if (y < h - 1) neighborMatch += match[mi + w];
                        if (neighborMatch < 0) {
                            var idx = sdf.getPixelIndex(x, y);
                            sdf.pixels[idx] = doubleFillValue - sdf.pixels[idx];
                            sdf.pixels[idx + 1] = doubleFillValue - sdf.pixels[idx + 1];
                            sdf.pixels[idx + 2] = doubleFillValue - sdf.pixels[idx + 2];
                        }
                    }
                }
            }
        }
    }
}
