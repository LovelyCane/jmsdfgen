package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.List;

public final class BenchComprehensive {
    static double now() {
        return System.nanoTime() / 1e9;
    }

    public static void main(String[] args) throws Exception {
        String font = args[0];
        int warmup = Integer.parseInt(args[1]);
        int reps = Integer.parseInt(args[2]);
        int size = Integer.parseInt(args[3]);
        List<Long> unicodes = new ArrayList<>();
        for (int i = 4; i < args.length; i++) unicodes.add(Long.parseLong(args[i]));

        var ft = ImportFont.initializeFreetype();
        var fontHandle = ImportFont.loadFont(ft, font);
        if (fontHandle == null) throw new IllegalStateException("font load failed");

        var genConfig = new GeneratorConfig.MSDFGeneratorConfig();
        genConfig.overlapSupport = true;
        genConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.DISABLED;
        genConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;

        for (long u : unicodes) {
            var shape = new Shape();
            ImportFont.loadGlyph(shape, fontHandle, ImportFont.getGlyphIndex(fontHandle, u));
            shape.normalize();
            EdgeColoring.edgeColoringSimple(shape, 3.0, 0);
            if (shape.edgeCount() == 0) {
                System.out.printf("U+%04X: empty glyph, skipped%n", u);
                continue;
            }

            var pxRange = new Range(2);
            var bounds = shape.getBounds();
            double l = bounds.l, b = bounds.b, r = bounds.r, t = bounds.t;
            var frame = Vector2.add(new Vector2(size, size), new Vector2(2 * pxRange.lower));
            if (l >= r || b >= t) {
                l = 0;
                b = 0;
                r = 1;
                t = 1;
            }
            var dims = new Vector2(r - l, t - b);
            Vector2 scale;
            var translate = new Vector2();
            double avgScale;
            if (dims.x * frame.y < dims.y * frame.x) {
                translate = new Vector2(0.5 * (frame.x / frame.y * dims.y - dims.x) - l, -b);
                avgScale = frame.y / dims.y;
            } else {
                translate = new Vector2(-l, 0.5 * (frame.y / frame.x * dims.x - dims.y) - b);
                avgScale = frame.x / dims.x;
            }
            scale = new Vector2(avgScale);
            translate = Vector2.subtract(translate, Vector2.divide(pxRange.lower, scale));
            var range = pxRange.divide(Math.min(scale.y, scale.x));
            var transformation = new SDFTransformation(new Projection(scale, translate), range);

            var bitmap = new Bitmap<>(size, size, 3, Float[]::new);
            var section = bitmap.toBitmapSection();

            for (int i = 0; i < warmup; i++) {
                MSDFGen.generateMSDF(section, shape, transformation, genConfig);
            }

            double best = Double.MAX_VALUE;
            double total = 0;
            for (int i = 0; i < reps; i++) {
                double t0 = now();
                MSDFGen.generateMSDF(section, shape, transformation, genConfig);
                double dt = (now() - t0) * 1e3;
                total += dt;
                if (dt < best) best = dt;
            }
            System.out.printf("U+%04X edges=%d avg=%.1f best=%.1f%n",
                    u, shape.edgeCount(), total / reps, best);
        }

        ImportFont.destroyFont(fontHandle);
        ImportFont.deinitializeFreetype(ft);
    }
}
