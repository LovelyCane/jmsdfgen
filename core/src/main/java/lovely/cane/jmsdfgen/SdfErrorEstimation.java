package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.List;

public final class SdfErrorEstimation {
    private SdfErrorEstimation() {
    }

    public static void scanlineSDF(Scanline line, Bitmap.BitmapConstSection<Float> sdf, Projection projection,
                                   double y, YAxisOrientation yAxisOrientation) {
        var channels = sdf.channels;
        if (channels == 1)
            scanlineSDF1(line, sdf, projection, y, yAxisOrientation);
        else
            scanlineMSDF(line, sdf, projection, y, yAxisOrientation);
    }

    public static void scanlineSDF(Scanline line, Bitmap.BitmapConstSection<Float> sdf, Projection projection,
                                   double y, boolean inverseYAxis) {
        scanlineSDF(line, sdf, projection, y,
                inverseYAxis ? YAxisOrientation.Y_DOWNWARD : YAxisOrientation.Y_UPWARD);
    }

    public static void scanlineSDF(Scanline line, Bitmap.BitmapConstSection<Float> sdf,
                                   Vector2 scale, Vector2 translate, boolean inverseYAxis, double y) {
        scanlineSDF(line, sdf, new Projection(scale, translate), y, inverseYAxis);
    }

    public static double estimateSDFError(Bitmap.BitmapConstSection<Float> sdf, Shape shape,
                                          Projection projection, int scanlinesPerRow, Scanline.FillRule fillRule) {
        return estimateSDFErrorInner(sdf, shape, projection, scanlinesPerRow, fillRule);
    }

    public static double estimateSDFError(Bitmap.BitmapConstSection<Float> sdf, Shape shape,
                                          Vector2 scale, Vector2 translate, int scanlinesPerRow, Scanline.FillRule fillRule) {
        return estimateSDFErrorInner(sdf, shape, new Projection(scale, translate), scanlinesPerRow, fillRule);
    }

    private static void scanlineSDF1(Scanline line, Bitmap.BitmapConstSection<Float> sdf,
                                     Projection projection, double y, YAxisOrientation yAxisOrientation) {
        if (sdf.width <= 0 || sdf.height <= 0) {
            line.setIntersections(new ArrayList<>());
            return;
        }
        var pixelY = Arithmetic.clamp(projection.projectY(y) - 0.5, sdf.height - 1.0);
        if (yAxisOrientation == YAxisOrientation.Y_DOWNWARD)
            pixelY = sdf.height - 1 - pixelY;
        var b = (int) Math.floor(pixelY);
        var t = b + 1;
        var bt = pixelY - b;
        if (t >= sdf.height) {
            b = sdf.height - 1;
            t = sdf.height - 1;
            bt = 1.0;
        }
        var inside = false;
        List<Scanline.Intersection> intersections = new ArrayList<>();
        float lv, rv = (float) Arithmetic.mix(sdf.pixels[sdf.getPixelIndex(0, b)],
                sdf.pixels[sdf.getPixelIndex(0, t)], bt);
        if ((inside = rv > 0.5f)) {
            intersections.add(new Scanline.Intersection(-1e240, 1));
        }
        for (int l = 0, r = 1; r < sdf.width; l++, r++) {
            lv = rv;
            rv = (float) Arithmetic.mix(sdf.pixels[sdf.getPixelIndex(r, b)],
                    sdf.pixels[sdf.getPixelIndex(r, t)], bt);
            if (lv != rv) {
                var lr = (0.5 - lv) / (double) (rv - lv);
                if (lr >= 0.0 && lr <= 1.0) {
                    intersections.add(new Scanline.Intersection(
                            projection.unprojectX(l + lr + 0.5),
                            rv > lv ? 1 : -1));
                }
            }
        }
        line.setIntersections(intersections);
    }

    private static void scanlineMSDF(Scanline line, Bitmap.BitmapConstSection<Float> sdf,
                                     Projection projection, double y, YAxisOrientation yAxisOrientation) {
        if (sdf.width <= 0 || sdf.height <= 0) {
            line.setIntersections(new ArrayList<>());
            return;
        }
        var pixelY = Arithmetic.clamp(projection.projectY(y) - 0.5, sdf.height - 1.0);
        if (yAxisOrientation == YAxisOrientation.Y_DOWNWARD)
            pixelY = sdf.height - 1 - pixelY;
        var b = (int) Math.floor(pixelY);
        var t = b + 1;
        var bt = pixelY - b;
        if (t >= sdf.height) {
            b = sdf.height - 1;
            t = sdf.height - 1;
            bt = 1.0;
        }
        var inside = false;
        List<Scanline.Intersection> intersections = new ArrayList<>();
        float[] lv = new float[3], rv = new float[3];
        var baseB0 = sdf.getPixelIndex(0, b);
        var baseT0 = sdf.getPixelIndex(0, t);
        rv[0] = (float) Arithmetic.mix(sdf.pixels[baseB0], sdf.pixels[baseT0], bt);
        rv[1] = (float) Arithmetic.mix(sdf.pixels[baseB0 + 1], sdf.pixels[baseT0 + 1], bt);
        rv[2] = (float) Arithmetic.mix(sdf.pixels[baseB0 + 2], sdf.pixels[baseT0 + 2], bt);
        if ((inside = Arithmetic.median(rv[0], rv[1], rv[2]) > 0.5f)) {
            intersections.add(new Scanline.Intersection(-1e240, 1));
        }
        for (int l = 0, r = 1; r < sdf.width; l++, r++) {
            lv[0] = rv[0];
            lv[1] = rv[1];
            lv[2] = rv[2];
            var baseBr = sdf.getPixelIndex(r, b);
            var baseTr = sdf.getPixelIndex(r, t);
            rv[0] = (float) Arithmetic.mix(sdf.pixels[baseBr], sdf.pixels[baseTr], bt);
            rv[1] = (float) Arithmetic.mix(sdf.pixels[baseBr + 1], sdf.pixels[baseTr + 1], bt);
            rv[2] = (float) Arithmetic.mix(sdf.pixels[baseBr + 2], sdf.pixels[baseTr + 2], bt);
            var newIntersections = new Scanline.Intersection[4];
            var newCount = 0;
            for (var i = 0; i < 3; i++) {
                if (lv[i] != rv[i]) {
                    var lr = (0.5 - lv[i]) / (double) (rv[i] - lv[i]);
                    if (lr >= 0.0 && lr <= 1.0) {
                        var v0 = (float) Arithmetic.mix(lv[0], rv[0], lr);
                        var v1 = (float) Arithmetic.mix(lv[1], rv[1], lr);
                        var v2 = (float) Arithmetic.mix(lv[2], rv[2], lr);
                        if (Arithmetic.median(v0, v1, v2) == (i == 0 ? v0 : i == 1 ? v1 : v2)) {
                            newIntersections[newCount] = new Scanline.Intersection(
                                    projection.unprojectX(l + lr + 0.5),
                                    rv[i] > lv[i] ? 1 : -1);
                            newCount++;
                        }
                    }
                }
            }
            if (newCount >= 2) {
                if (newIntersections[0].x > newIntersections[1].x) {
                    var tmp = newIntersections[0];
                    newIntersections[0] = newIntersections[1];
                    newIntersections[1] = tmp;
                }
                if (newCount >= 3 && newIntersections[1].x > newIntersections[2].x) {
                    var tmp = newIntersections[1];
                    newIntersections[1] = newIntersections[2];
                    newIntersections[2] = tmp;
                    if (newIntersections[0].x > newIntersections[1].x) {
                        tmp = newIntersections[0];
                        newIntersections[0] = newIntersections[1];
                        newIntersections[1] = tmp;
                    }
                }
            }
            for (var i = 0; i < newCount; i++) {
                if ((newIntersections[i].direction > 0) == !inside) {
                    intersections.add(newIntersections[i]);
                    inside = !inside;
                }
            }
            var rvScalar = Arithmetic.median(rv[0], rv[1], rv[2]);
            if ((rvScalar > 0.5f) != inside && rvScalar != 0.5f && !intersections.isEmpty()) {
                intersections.removeLast();
                inside = !inside;
            }
        }
        line.setIntersections(intersections);
    }

    private static double estimateSDFErrorInner(Bitmap.BitmapConstSection<Float> sdf, Shape shape,
                                                Projection projection, int scanlinesPerRow, Scanline.FillRule fillRule) {
        if (sdf.width <= 1 || sdf.height <= 1 || scanlinesPerRow < 1)
            return 0.0;
        var subRowSize = 1.0 / scanlinesPerRow;
        var xFrom = projection.unprojectX(0.5);
        var xTo = projection.unprojectX(sdf.width - 0.5);
        var overlapFactor = 1.0 / (xTo - xFrom);
        var error = 0.0;
        var refScanline = new Scanline();
        var sdfScanline = new Scanline();
        for (var row = 0; row < sdf.height - 1; row++) {
            for (var subRow = 0; subRow < scanlinesPerRow; subRow++) {
                var bt = (subRow + 0.5) * subRowSize;
                var y = projection.unprojectY(row + bt + 0.5);
                shape.scanline(refScanline, y);
                scanlineSDF(sdfScanline, sdf, projection, y, shape.getYAxisOrientation());
                error += 1.0 - overlapFactor * Scanline.overlap(refScanline, sdfScanline, xFrom, xTo, fillRule);
            }
        }
        return error / ((sdf.height - 1) * scanlinesPerRow);
    }
}
