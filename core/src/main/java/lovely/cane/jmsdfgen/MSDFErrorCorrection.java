package lovely.cane.jmsdfgen;

import java.util.Arrays;

public class MSDFErrorCorrection {
    public static final byte ERROR = 1;
    public static final byte PROTECTED = 2;

    private static final double ARTIFACT_T_EPSILON = 0.01;
    private static final double PROTECTION_RADIUS_TOLERANCE = 1.001;
    private static final int CLASSIFIER_FLAG_CANDIDATE = 0x01;
    private static final int CLASSIFIER_FLAG_ARTIFACT = 0x02;

    private final Bitmap.BitmapSection<Byte> stencil;
    private final SDFTransformation transformation;
    private double minDeviationRatio;
    private double minImproveRatio;

    public MSDFErrorCorrection(Bitmap.BitmapSection<Byte> stencil, SDFTransformation transformation) {
        this.stencil = stencil;
        this.transformation = transformation;
        minDeviationRatio = GeneratorConfig.ErrorCorrectionConfig.DEFAULT_MIN_DEVIATION_RATIO;
        minImproveRatio = GeneratorConfig.ErrorCorrectionConfig.DEFAULT_MIN_IMPROVE_RATIO;
        for (var y = 0; y < stencil.height; y++) {
            for (var x = 0; x < stencil.width; x++) {
                stencil.pixels[stencil.getPixelIndex(x, y)] = 0;
            }
        }
    }

    public static void msdfErrorCorrection(Bitmap.BitmapSection<Float> output, Shape shape,
                                           SDFTransformation transformation, GeneratorConfig.MSDFGeneratorConfig config) {
        if (config.errorCorrection.mode() == GeneratorConfig.ErrorCorrectionConfig.Mode.DISABLED) return;
        var stencil = new Bitmap<>(output.width, output.height, 1, Byte[]::new);
        Arrays.fill(stencil.pixels, (byte) 0);
        var ec = new MSDFErrorCorrection(stencil.toBitmapSection(), transformation);
        ec.setMinDeviationRatio(config.errorCorrection.minDeviationRatio());
        ec.setMinImproveRatio(config.errorCorrection.minImproveRatio());
        switch (config.errorCorrection.mode()) {
            case INDISCRIMINATE:
                break;
            case EDGE_PRIORITY:
                ec.protectCorners(shape);
                ec.protectEdges(output.toConstSection());
                break;
            case EDGE_ONLY:
                ec.protectAll();
                break;
        }
        if (config.errorCorrection.distanceCheckMode() == GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE ||
                (config.errorCorrection.distanceCheckMode() == GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE &&
                        config.errorCorrection.mode() != GeneratorConfig.ErrorCorrectionConfig.Mode.EDGE_ONLY)) {
            ec.findErrors(output.toConstSection());
            if (config.errorCorrection.distanceCheckMode() == GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE)
                ec.protectAll();
        }
        if (config.errorCorrection.distanceCheckMode() == GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.ALWAYS_CHECK_DISTANCE ||
                config.errorCorrection.distanceCheckMode() == GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE) {
            ec.findErrors(output.toConstSection(), shape, config.overlapSupport);
        }
        ec.apply(output);
    }

    public void setMinDeviationRatio(double minDeviationRatio) {
        this.minDeviationRatio = minDeviationRatio;
    }

    public void setMinImproveRatio(double minImproveRatio) {
        this.minImproveRatio = minImproveRatio;
    }

    public void protectCorners(Shape shape) {
        stencil.reorient(shape.getYAxisOrientation());
        for (var contour : shape.contours) {
            if (contour.edges.isEmpty()) continue;
            var prevEdge = contour.edges.getLast().get();
            for (var holder : contour.edges) {
                var edge = holder.get();
                var commonColor = prevEdge.color.mask() & edge.color.mask();
                if ((commonColor & (commonColor - 1)) == 0) {
                    var p = transformation.project(edge.point(0));
                    var l = (int) Math.floor(p.x - 0.5);
                    var b = (int) Math.floor(p.y - 0.5);
                    var r = l + 1;
                    var t = b + 1;
                    if (l < stencil.width && b < stencil.height && r >= 0 && t >= 0) {
                        if (l >= 0 && b >= 0) setStencilFlag(l, b, PROTECTED);
                        if (r < stencil.width && b >= 0) setStencilFlag(r, b, PROTECTED);
                        if (l >= 0 && t < stencil.height) setStencilFlag(l, t, PROTECTED);
                        if (r < stencil.width && t < stencil.height) setStencilFlag(r, t, PROTECTED);
                    }
                }
                prevEdge = edge;
            }
        }
    }

    public void protectEdges(Bitmap.BitmapConstSection<Float> sdf) {
        var shapeDistDelta = transformation.distanceMapping.applyDelta(new DistanceMapping.Delta(1));
        stencil.reorient(sdf.yOrientation);

        var radiusH = (float) (PROTECTION_RADIUS_TOLERANCE * transformation.unprojectVector(new Vector2(shapeDistDelta, 0)).length());
        for (var y = 0; y < sdf.height; y++) {
            for (var x = 0; x < sdf.width - 1; x++) {
                var left = getChannels(sdf, x, y);
                var right = getChannels(sdf, x + 1, y);
                var lm = Arithmetic.median(left[0], left[1], left[2]);
                var rm = Arithmetic.median(right[0], right[1], right[2]);
                if (Math.abs(lm - 0.5f) + Math.abs(rm - 0.5f) < radiusH) {
                    var mask = edgeBetweenTexels(left, right);
                    protectExtremeChannels(stencil.getPixelIndex(x, y), left, lm, mask);
                    protectExtremeChannels(stencil.getPixelIndex(x + 1, y), right, rm, mask);
                }
            }
        }

        var radiusV = (float) (PROTECTION_RADIUS_TOLERANCE * transformation.unprojectVector(new Vector2(0, shapeDistDelta)).length());
        for (var y = 0; y < sdf.height - 1; y++) {
            for (var x = 0; x < sdf.width; x++) {
                var bottom = getChannels(sdf, x, y);
                var top = getChannels(sdf, x, y + 1);
                var bm = Arithmetic.median(bottom[0], bottom[1], bottom[2]);
                var tm = Arithmetic.median(top[0], top[1], top[2]);
                if (Math.abs(bm - 0.5f) + Math.abs(tm - 0.5f) < radiusV) {
                    var mask = edgeBetweenTexels(bottom, top);
                    protectExtremeChannels(stencil.getPixelIndex(x, y), bottom, bm, mask);
                    protectExtremeChannels(stencil.getPixelIndex(x, y + 1), top, tm, mask);
                }
            }
        }

        var radiusD = (float) (PROTECTION_RADIUS_TOLERANCE * transformation.unprojectVector(new Vector2(shapeDistDelta, shapeDistDelta)).length());
        for (var y = 0; y < sdf.height - 1; y++) {
            for (var x = 0; x < sdf.width - 1; x++) {
                var lb = getChannels(sdf, x, y);
                var rb = getChannels(sdf, x + 1, y);
                var lt = getChannels(sdf, x, y + 1);
                var rt = getChannels(sdf, x + 1, y + 1);
                var mlb = Arithmetic.median(lb[0], lb[1], lb[2]);
                var mrb = Arithmetic.median(rb[0], rb[1], rb[2]);
                var mlt = Arithmetic.median(lt[0], lt[1], lt[2]);
                var mrt = Arithmetic.median(rt[0], rt[1], rt[2]);
                if (Math.abs(mlb - 0.5f) + Math.abs(mrt - 0.5f) < radiusD) {
                    var mask = edgeBetweenTexels(lb, rt);
                    protectExtremeChannels(stencil.getPixelIndex(x, y), lb, mlb, mask);
                    protectExtremeChannels(stencil.getPixelIndex(x + 1, y + 1), rt, mrt, mask);
                }
                if (Math.abs(mrb - 0.5f) + Math.abs(mlt - 0.5f) < radiusD) {
                    var mask = edgeBetweenTexels(rb, lt);
                    protectExtremeChannels(stencil.getPixelIndex(x + 1, y), rb, mrb, mask);
                    protectExtremeChannels(stencil.getPixelIndex(x, y + 1), lt, mlt, mask);
                }
            }
        }
    }

    public void protectAll() {
        for (var i = 0; i < stencil.pixels.length; i++) {
            stencil.pixels[i] = (byte) (stencil.pixels[i] | PROTECTED);
        }
    }

    public void findErrors(Bitmap.BitmapConstSection<Float> sdf) {
        var shapeDistDelta = transformation.distanceMapping.applyDelta(new DistanceMapping.Delta(1));
        stencil.reorient(sdf.yOrientation);
        var hSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(shapeDistDelta, 0)).length();
        var vSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(0, shapeDistDelta)).length();
        var dSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(shapeDistDelta, shapeDistDelta)).length();

        for (var y = 0; y < sdf.height; y++) {
            for (var x = 0; x < sdf.width; x++) {
                var c = getChannels(sdf, x, y);
                var cm = Arithmetic.median(c[0], c[1], c[2]);
                var prot = (stencil.pixels[stencil.getPixelIndex(x, y)] & PROTECTED) != 0;
                byte errorMask = 0;
                if (x > 0) {
                    var l = getChannels(sdf, x - 1, y);
                    if (hasLinearArtifact(new BaseClassifier(hSpan, prot), cm, c, l)) errorMask |= ERROR;
                }
                if (y > 0) {
                    var b = getChannels(sdf, x, y - 1);
                    if (hasLinearArtifact(new BaseClassifier(vSpan, prot), cm, c, b)) errorMask |= ERROR;
                }
                if (x < sdf.width - 1) {
                    var r = getChannels(sdf, x + 1, y);
                    if (hasLinearArtifact(new BaseClassifier(hSpan, prot), cm, c, r)) errorMask |= ERROR;
                }
                if (y < sdf.height - 1) {
                    var t = getChannels(sdf, x, y + 1);
                    if (hasLinearArtifact(new BaseClassifier(vSpan, prot), cm, c, t)) errorMask |= ERROR;
                }
                if (x > 0 && y > 0) {
                    var l = getChannels(sdf, x - 1, y);
                    var b = getChannels(sdf, x, y - 1);
                    var lb = getChannels(sdf, x - 1, y - 1);
                    if (hasDiagonalArtifact(new BaseClassifier(dSpan, prot), cm, c, l, b, lb)) errorMask |= ERROR;
                }
                if (x < sdf.width - 1 && y > 0) {
                    var r = getChannels(sdf, x + 1, y);
                    var b = getChannels(sdf, x, y - 1);
                    var rb = getChannels(sdf, x + 1, y - 1);
                    if (hasDiagonalArtifact(new BaseClassifier(dSpan, prot), cm, c, r, b, rb)) errorMask |= ERROR;
                }
                if (x > 0 && y < sdf.height - 1) {
                    var l = getChannels(sdf, x - 1, y);
                    var t = getChannels(sdf, x, y + 1);
                    var lt = getChannels(sdf, x - 1, y + 1);
                    if (hasDiagonalArtifact(new BaseClassifier(dSpan, prot), cm, c, l, t, lt)) errorMask |= ERROR;
                }
                if (x < sdf.width - 1 && y < sdf.height - 1) {
                    var r = getChannels(sdf, x + 1, y);
                    var t = getChannels(sdf, x, y + 1);
                    var rt = getChannels(sdf, x + 1, y + 1);
                    if (hasDiagonalArtifact(new BaseClassifier(dSpan, prot), cm, c, r, t, rt)) errorMask |= ERROR;
                }
                stencil.pixels[stencil.getPixelIndex(x, y)] = (byte) (stencil.pixels[stencil.getPixelIndex(x, y)] | errorMask);
            }
        }
    }

    public void findErrors(Bitmap.BitmapConstSection<Float> sdf, Shape shape, boolean overlapSupport) {
        stencil.reorient(shape.getYAxisOrientation());
        sdf.reorient(shape.getYAxisOrientation());
        var shapeDistDelta = transformation.distanceMapping.applyDelta(new DistanceMapping.Delta(1));
        var hSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(shapeDistDelta, 0)).length();
        var vSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(0, shapeDistDelta)).length();
        var dSpan = minDeviationRatio * transformation.unprojectVector(new Vector2(shapeDistDelta, shapeDistDelta)).length();

        var combiner = overlapSupport
                ? new ContourCombiner.OverlappingContourCombiner<>(
                shape, EdgeSelector.PerpendicularDistanceSelector::new, () -> -Double.MAX_VALUE, distance -> distance
        ) : new ContourCombiner.SimpleContourCombiner<>(new EdgeSelector.PerpendicularDistanceSelector());

        var finder = new ShapeDistanceFinder<>(shape, combiner, EdgeSelector.PerpendicularDistanceSelectorBase.EdgeCache::new);
        var texelSize = transformation.unprojectVector(new Vector2(1, 1));

        for (var y = 0; y < sdf.height; y++) {
            for (var x = 0; x < sdf.width; x++) {
                if ((stencil.pixels[stencil.getPixelIndex(x, y)] & ERROR) != 0) continue;
                var c = getChannels(sdf, x, y);
                var shapeCoord = transformation.unproject(new Vector2(x + 0.5, y + 0.5));
                var sdfCoord = new Vector2(x + 0.5, y + 0.5);
                var prot = (stencil.pixels[stencil.getPixelIndex(x, y)] & PROTECTED) != 0;
                var cm = Arithmetic.median(c[0], c[1], c[2]);

                var checker = new ShapeDistanceChecker(finder, transformation.distanceMapping, minImproveRatio,
                        texelSize, shapeCoord, sdfCoord, c, prot, sdf);

                byte errorMask = 0;
                if (x > 0) {
                    var l = getChannels(sdf, x - 1, y);
                    if (hasLinearArtifact(checker.new Classifier(new Vector2(-1, 0), hSpan), cm, c, l))
                        errorMask |= ERROR;
                }
                if (y > 0) {
                    var b = getChannels(sdf, x, y - 1);
                    if (hasLinearArtifact(checker.new Classifier(new Vector2(0, -1), vSpan), cm, c, b))
                        errorMask |= ERROR;
                }
                if (x < sdf.width - 1) {
                    var r = getChannels(sdf, x + 1, y);
                    if (hasLinearArtifact(checker.new Classifier(new Vector2(1, 0), hSpan), cm, c, r))
                        errorMask |= ERROR;
                }
                if (y < sdf.height - 1) {
                    var t = getChannels(sdf, x, y + 1);
                    if (hasLinearArtifact(checker.new Classifier(new Vector2(0, 1), vSpan), cm, c, t))
                        errorMask |= ERROR;
                }
                if (x > 0 && y > 0) {
                    var l = getChannels(sdf, x - 1, y);
                    var b = getChannels(sdf, x, y - 1);
                    var lb = getChannels(sdf, x - 1, y - 1);
                    if (hasDiagonalArtifact(checker.new Classifier(new Vector2(-1, -1), dSpan), cm, c, l, b, lb))
                        errorMask |= ERROR;
                }
                if (x < sdf.width - 1 && y > 0) {
                    var r = getChannels(sdf, x + 1, y);
                    var b = getChannels(sdf, x, y - 1);
                    var rb = getChannels(sdf, x + 1, y - 1);
                    if (hasDiagonalArtifact(checker.new Classifier(new Vector2(1, -1), dSpan), cm, c, r, b, rb))
                        errorMask |= ERROR;
                }
                if (x > 0 && y < sdf.height - 1) {
                    var l = getChannels(sdf, x - 1, y);
                    var t = getChannels(sdf, x, y + 1);
                    var lt = getChannels(sdf, x - 1, y + 1);
                    if (hasDiagonalArtifact(checker.new Classifier(new Vector2(-1, 1), dSpan), cm, c, l, t, lt))
                        errorMask |= ERROR;
                }
                if (x < sdf.width - 1 && y < sdf.height - 1) {
                    var r = getChannels(sdf, x + 1, y);
                    var t = getChannels(sdf, x, y + 1);
                    var rt = getChannels(sdf, x + 1, y + 1);
                    if (hasDiagonalArtifact(checker.new Classifier(new Vector2(1, 1), dSpan), cm, c, r, t, rt))
                        errorMask |= ERROR;
                }
                stencil.pixels[stencil.getPixelIndex(x, y)] = (byte) (stencil.pixels[stencil.getPixelIndex(x, y)] | errorMask);
            }
        }
    }

    public void apply(Bitmap.BitmapSection<Float> sdf) {
        sdf.reorient(stencil.yOrientation);
        for (var y = 0; y < sdf.height; y++) {
            for (var x = 0; x < sdf.width; x++) {
                if ((stencil.pixels[stencil.getPixelIndex(x, y)] & ERROR) != 0) {
                    var idx = sdf.getPixelIndex(x, y);
                    var m = Arithmetic.median(sdf.pixels[idx], sdf.pixels[idx + 1], sdf.pixels[idx + 2]);
                    sdf.pixels[idx] = m;
                    sdf.pixels[idx + 1] = m;
                    sdf.pixels[idx + 2] = m;
                }
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void setStencilFlag(int x, int y, byte flag) {
        var idx = stencil.getPixelIndex(x, y);
        stencil.pixels[idx] = (byte) (stencil.pixels[idx] | flag);
    }

    private static float[] getChannels(Bitmap.BitmapConstSection<Float> sdf, int x, int y) {
        var idx = sdf.getPixelIndex(x, y);
        return new float[]{sdf.pixels[idx], sdf.pixels[idx + 1], sdf.pixels[idx + 2]};
    }

    private static boolean edgeBetweenTexelsChannel(float[] a, float[] b, int channel) {
        var t = (a[channel] - 0.5) / (a[channel] - b[channel]);
        if (t > 0 && t < 1) {
            var c = new double[]{
                    Arithmetic.mix(a[0], b[0], t),
                    Arithmetic.mix(a[1], b[1], t),
                    Arithmetic.mix(a[2], b[2], t)
            };
            return Arithmetic.median(c[0], c[1], c[2]) == c[channel];
        }
        return false;
    }

    private static int edgeBetweenTexels(float[] a, float[] b) {
        var mask = 0;
        if (edgeBetweenTexelsChannel(a, b, 0)) mask |= EdgeColor.RED.mask();
        if (edgeBetweenTexelsChannel(a, b, 1)) mask |= EdgeColor.GREEN.mask();
        if (edgeBetweenTexelsChannel(a, b, 2)) mask |= EdgeColor.BLUE.mask();
        return mask;
    }

    private void protectExtremeChannels(int index, float[] msd, float m, int mask) {
        if ((mask & EdgeColor.RED.mask()) != 0 && msd[0] != m ||
                (mask & EdgeColor.GREEN.mask()) != 0 && msd[1] != m ||
                (mask & EdgeColor.BLUE.mask()) != 0 && msd[2] != m) {
            stencil.pixels[index] = (byte) (stencil.pixels[index] | PROTECTED);
        }
    }

    private static boolean hasLinearArtifact(ArtifactClassifier classifier, float am, float[] a, float[] b) {
        var bm = Arithmetic.median(b[0], b[1], b[2]);
        if (Math.abs(am - 0.5f) >= Math.abs(bm - 0.5f)) {
            return hasLinearArtifactInner(classifier, am, bm, a, b, a[1] - a[0], b[1] - b[0]) ||
                    hasLinearArtifactInner(classifier, am, bm, a, b, a[2] - a[1], b[2] - b[1]) ||
                    hasLinearArtifactInner(classifier, am, bm, a, b, a[0] - a[2], b[0] - b[2]);
        }
        return false;
    }

    private static boolean hasLinearArtifactInner(ArtifactClassifier classifier, float am, float bm,
                                                  float[] a, float[] b, float dA, float dB) {
        var t = (double) dA / (dA - dB);
        if (t > ARTIFACT_T_EPSILON && t < 1 - ARTIFACT_T_EPSILON) {
            var xm = interpolatedMedian(a, b, t);
            return classifier.evaluate(t, xm, classifier.rangeTest(0, 1, t, am, bm, xm));
        }
        return false;
    }

    private static boolean hasDiagonalArtifact(ArtifactClassifier classifier, float am, float[] a,
                                               float[] b, float[] c, float[] d) {
        var dm = Arithmetic.median(d[0], d[1], d[2]);
        if (Math.abs(am - 0.5f) >= Math.abs(dm - 0.5f)) {
            var abc = new float[]{a[0] - b[0] - c[0], a[1] - b[1] - c[1], a[2] - b[2] - c[2]};
            var l = new float[]{-a[0] - abc[0], -a[1] - abc[1], -a[2] - abc[2]};
            var q = new float[]{d[0] + abc[0], d[1] + abc[1], d[2] + abc[2]};
            var tEx = new double[]{-0.5 * l[0] / q[0], -0.5 * l[1] / q[1], -0.5 * l[2] / q[2]};
            return hasDiagonalArtifactInner(classifier, am, dm, a, l, q,
                    a[1] - a[0], b[1] - b[0] + c[1] - c[0], d[1] - d[0], tEx[0], tEx[1]) ||
                    hasDiagonalArtifactInner(classifier, am, dm, a, l, q,
                            a[2] - a[1], b[2] - b[1] + c[2] - c[1], d[2] - d[1], tEx[1], tEx[2]) ||
                    hasDiagonalArtifactInner(classifier, am, dm, a, l, q,
                            a[0] - a[2], b[0] - b[2] + c[0] - c[2], d[0] - d[2], tEx[2], tEx[0]);
        }
        return false;
    }

    private static boolean hasDiagonalArtifactInner(ArtifactClassifier classifier, float am, float dm,
                                                    float[] a, float[] l, float[] q,
                                                    float dA, float dBC, float dD, double tEx0, double tEx1) {
        var t = new double[2];
        var solutions = EquationSolver.solveQuadratic(t, dD - dBC + dA, dBC - dA - dA, dA);
        for (var i = 0; i < solutions; i++) {
            if (t[i] > ARTIFACT_T_EPSILON && t[i] < 1 - ARTIFACT_T_EPSILON) {
                var xm = interpolatedMedian(a, l, q, t[i]);
                var rangeFlags = classifier.rangeTest(0, 1, t[i], am, dm, xm);
                var tEnd = new double[2];
                var em = new float[2];
                if (tEx0 > 0 && tEx0 < 1) {
                    tEnd[0] = 0;
                    tEnd[1] = 1;
                    em[0] = am;
                    em[1] = dm;
                    var idx = tEx0 > t[i] ? 1 : 0;
                    tEnd[idx] = tEx0;
                    em[idx] = interpolatedMedian(a, l, q, tEx0);
                    rangeFlags |= classifier.rangeTest(tEnd[0], tEnd[1], t[i], em[0], em[1], xm);
                }
                if (tEx1 > 0 && tEx1 < 1) {
                    tEnd[0] = 0;
                    tEnd[1] = 1;
                    em[0] = am;
                    em[1] = dm;
                    var idx = tEx1 > t[i] ? 1 : 0;
                    tEnd[idx] = tEx1;
                    em[idx] = interpolatedMedian(a, l, q, tEx1);
                    rangeFlags |= classifier.rangeTest(tEnd[0], tEnd[1], t[i], em[0], em[1], xm);
                }
                if (classifier.evaluate(t[i], xm, rangeFlags)) return true;
            }
        }
        return false;
    }

    private static float interpolatedMedian(float[] a, float[] b, double t) {
        return Arithmetic.median(
                (float) Arithmetic.mix(a[0], b[0], t),
                (float) Arithmetic.mix(a[1], b[1], t),
                (float) Arithmetic.mix(a[2], b[2], t)
        );
    }

    private static float interpolatedMedian(float[] a, float[] l, float[] q, double t) {
        return Arithmetic.median(
                (float) (t * (t * q[0] + l[0]) + a[0]),
                (float) (t * (t * q[1] + l[1]) + a[1]),
                (float) (t * (t * q[2] + l[2]) + a[2])
        );
    }

    private interface ArtifactClassifier {
        int rangeTest(double at, double bt, double xt, float am, float bm, float xm);

        boolean evaluate(double t, float m, int flags);
    }

    private record BaseClassifier(double span, boolean protectedFlag) implements ArtifactClassifier {
        @Override
        public int rangeTest(double at, double bt, double xt, float am, float bm, float xm) {
            if ((am > 0.5f && bm > 0.5f && xm <= 0.5f) ||
                    (am < 0.5f && bm < 0.5f && xm >= 0.5f) ||
                    (!protectedFlag && Arithmetic.median(am, bm, xm) != xm)) {
                var axSpan = (xt - at) * span;
                var bxSpan = (bt - xt) * span;
                if (!(xm >= am - axSpan && xm <= am + axSpan && xm >= bm - bxSpan && xm <= bm + bxSpan))
                    return CLASSIFIER_FLAG_CANDIDATE | CLASSIFIER_FLAG_ARTIFACT;
                return CLASSIFIER_FLAG_CANDIDATE;
            }
            return 0;
        }

        @Override
        public boolean evaluate(double t, float m, int flags) {
            return (flags & CLASSIFIER_FLAG_ARTIFACT) != 0;
        }
    }

    private record ShapeDistanceChecker(
            ShapeDistanceFinder<?, EdgeSelector.PerpendicularDistanceSelector, Double, EdgeSelector.PerpendicularDistanceSelectorBase.EdgeCache> finder,
            DistanceMapping distanceMapping, double minImproveRatio, Vector2 texelSize, Vector2 shapeCoord,
            Vector2 sdfCoord, float[] msd, boolean protectedFlag, Bitmap.BitmapConstSection<Float> sdf) {

        class Classifier implements ArtifactClassifier {
            private final BaseClassifier base;
            private final Vector2 direction;

            Classifier(Vector2 direction, double span) {
                base = new BaseClassifier(span, protectedFlag);
                this.direction = direction;
            }

            @Override
            public int rangeTest(double at, double bt, double xt, float am, float bm, float xm) {
                return base.rangeTest(at, bt, xt, am, bm, xm);
            }

            @Override
            public boolean evaluate(double t, float m, int flags) {
                if ((flags & CLASSIFIER_FLAG_CANDIDATE) == 0) return false;
                if ((flags & CLASSIFIER_FLAG_ARTIFACT) != 0) return true;
                var tVector = Vector2.multiply(direction, t);
                var oldMSD = new float[3];
                interpolate(oldMSD, sdf, Vector2.add(sdfCoord, tVector));
                var aWeight = (1 - Math.abs(tVector.x)) * (1 - Math.abs(tVector.y));
                var aPSD = Arithmetic.median(msd[0], msd[1], msd[2]);
                var newMSD = new float[]{
                        (float) (oldMSD[0] + aWeight * (aPSD - msd[0])),
                        (float) (oldMSD[1] + aWeight * (aPSD - msd[1])),
                        (float) (oldMSD[2] + aWeight * (aPSD - msd[2]))
                };
                var oldPSD = Arithmetic.median(oldMSD[0], oldMSD[1], oldMSD[2]);
                var newPSD = Arithmetic.median(newMSD[0], newMSD[1], newMSD[2]);
                var query = Vector2.add(shapeCoord, new Vector2(tVector.x * texelSize.x, tVector.y * texelSize.y));
                var refDist = finder.distance(query);
                var refPSD = (float) distanceMapping.apply(refDist);
                return minImproveRatio * Math.abs(newPSD - refPSD) < Math.abs(oldPSD - refPSD);
            }
        }

        private static void interpolate(float[] out, Bitmap.BitmapConstSection<Float> sdf, Vector2 pos) {
            pos.x = Arithmetic.clamp(pos.x, 0, sdf.width);
            pos.y = Arithmetic.clamp(pos.y, 0, sdf.height);
            pos.x -= 0.5;
            pos.y -= 0.5;
            var l = (int) Math.floor(pos.x);
            var b = (int) Math.floor(pos.y);
            var r = l + 1;
            var t = b + 1;
            var lr = pos.x - l;
            var bt = pos.y - b;
            l = Math.clamp(l, 0, sdf.width - 1);
            r = Math.clamp(r, 0, sdf.width - 1);
            b = Math.clamp(b, 0, sdf.height - 1);
            t = Math.clamp(t, 0, sdf.height - 1);
            for (var i = 0; i < 3; i++) {
                out[i] = (float) Arithmetic.mix(
                        Arithmetic.mix(sdf.pixels[sdf.getPixelIndex(l, b) + i], sdf.pixels[sdf.getPixelIndex(r, b) + i], lr),
                        Arithmetic.mix(sdf.pixels[sdf.getPixelIndex(l, t) + i], sdf.pixels[sdf.getPixelIndex(r, t) + i], lr),
                        bt
                );
            }
        }
    }
}
