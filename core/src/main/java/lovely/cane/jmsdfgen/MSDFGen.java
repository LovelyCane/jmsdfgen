package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public final class MSDFGen {
    private MSDFGen() {
    }

    public static void generateSDF(Bitmap.BitmapSection<Float> output, Shape shape,
                                   SDFTransformation transformation, GeneratorConfig config) {
        generateDistanceField(
                output, shape, transformation,
                config.overlapSupport ? new ContourCombiner.OverlappingContourCombiner<>(
                        shape, EdgeSelector.TrueDistanceSelector::new, () -> -Double.MAX_VALUE,
                        Function.identity()
                ) : new ContourCombiner.SimpleContourCombiner<>(new EdgeSelector.TrueDistanceSelector()),
                EdgeSelector.TrueDistanceSelector.EdgeCache::new,
                new DistancePixelConversion.DistancePixelConversionDouble(transformation.distanceMapping)
        );
    }

    public static void generatePSDF(Bitmap.BitmapSection<Float> output, Shape shape,
                                    SDFTransformation transformation, GeneratorConfig config) {
        generateDistanceField(
                output, shape, transformation,
                config.overlapSupport ? new ContourCombiner.OverlappingContourCombiner<>(
                        shape, EdgeSelector.PerpendicularDistanceSelector::new, () -> -Double.MAX_VALUE,
                        Function.identity()
                ) : new ContourCombiner.SimpleContourCombiner<>(new EdgeSelector.PerpendicularDistanceSelector()),
                EdgeSelector.PerpendicularDistanceSelectorBase.EdgeCache::new,
                new DistancePixelConversion.DistancePixelConversionDouble(transformation.distanceMapping)
        );
    }

    public static void generateMSDF(Bitmap.BitmapSection<Float> output, Shape shape,
                                    SDFTransformation transformation, GeneratorConfig.MSDFGeneratorConfig config) {
        generateDistanceField(
                output, shape, transformation,
                config.overlapSupport ? new ContourCombiner.OverlappingContourCombiner<>(
                        shape, EdgeSelector.MultiDistanceSelector::new, MultiDistance::new,
                        MultiDistance::resolveDistance
                ) : new ContourCombiner.SimpleContourCombiner<>(new EdgeSelector.MultiDistanceSelector()),
                EdgeSelector.PerpendicularDistanceSelectorBase.EdgeCache::new,
                new DistancePixelConversion.DistancePixelConversionMultiDistance(transformation.distanceMapping)
        );
        MSDFErrorCorrection.msdfErrorCorrection(output, shape, transformation, config);
    }

    public static void generateMTSDF(Bitmap.BitmapSection<Float> output, Shape shape,
                                     SDFTransformation transformation, GeneratorConfig.MSDFGeneratorConfig config) {
        generateDistanceField(
                output, shape, transformation,
                config.overlapSupport ? new ContourCombiner.OverlappingContourCombiner<>(
                        shape, EdgeSelector.MultiAndTrueDistanceSelector::new, MultiAndTrueDistance::new,
                        MultiAndTrueDistance::resolveDistance
                ) : new ContourCombiner.SimpleContourCombiner<>(new EdgeSelector.MultiAndTrueDistanceSelector()),
                EdgeSelector.PerpendicularDistanceSelectorBase.EdgeCache::new,
                new DistancePixelConversion.DistancePixelConversionMultiAndTrueDistance(transformation.distanceMapping)
        );
        MSDFErrorCorrection.msdfErrorCorrection(output, shape, transformation, config);
    }

    private static <D, Cache, S extends EdgeSelector<D, Cache, S>, Combiner extends ContourCombiner<S, D, Cache>>
    void generateDistanceField(
            Bitmap.BitmapSection<Float> output, Shape shape, SDFTransformation transformation,
            Combiner combiner, Supplier<Cache> newCache, DistancePixelConversion<D> distancePixelConversion
    ) {
        output.reorient(shape.getYAxisOrientation());
        var finder = new ShapeDistanceFinder<>(shape, combiner, newCache);
        var xDirection = 1;
        for (var y = 0; y < output.height; ++y) {
            var x = xDirection < 0 ? output.width - 1 : 0;
            for (var col = 0; col < output.width; ++col) {
                var p = transformation.unproject(new Vector2(x + 0.5, y + 0.5));
                var distance = finder.distance(p);
                distancePixelConversion.distancePixelConversion(output, x, y, distance);
                x += xDirection;
            }
            xDirection = -xDirection;
        }
    }

    public static void generateSDF_legacy(Bitmap.BitmapSection<Float> output, Shape shape, Range range, Vector2 scale, Vector2 translate) {
        var distanceMapping = new DistanceMapping(range);
        output.reorient(shape.getYAxisOrientation());
        var pixelConversion = new DistancePixelConversion.DistancePixelConversionDouble(distanceMapping);
        for (var y = 0; y < output.height; ++y) {
            for (var x = 0; x < output.width; ++x) {
                var p = Vector2.subtract(Vector2.divide(new Vector2(x + 0.5, y + 0.5), scale), translate);
                var minDistance = new SignedDistance();
                for (var contour : shape.contours) {
                    for (var edge : contour.edges) {
                        var distance = edge.get().signedDistance(p, new double[1]);
                        if (distance.compareTo(minDistance) < 0) {
                            minDistance = distance;
                        }
                    }
                }
                pixelConversion.distancePixelConversion(output, x, y, minDistance.distance);
            }
        }
    }

    public static void generatePSDF_legacy(Bitmap.BitmapSection<Float> output, Shape shape, Range range, Vector2 scale, Vector2 translate) {
        var distanceMapping = new DistanceMapping(range);
        output.reorient(shape.getYAxisOrientation());
        var pixelConversion = new DistancePixelConversion.DistancePixelConversionDouble(distanceMapping);
        for (var y = 0; y < output.height; ++y) {
            for (var x = 0; x < output.width; ++x) {
                var p = new Vector2(x + 0.5, y + 0.5).divide(scale).subtract(translate);
                var minDistance = new SignedDistance();
                EdgeHolder nearEdge = null;
                var nearParam = 0.0;
                for (var contour : shape.contours) {
                    for (var edge : contour.edges) {
                        var param = new double[1];
                        var distance = edge.get().signedDistance(p, param);
                        if (distance.compareTo(minDistance) < 0) {
                            minDistance = distance;
                            nearEdge = edge;
                            nearParam = param[0];
                        }
                    }
                }
                if (nearEdge != null) {
                    nearEdge.get().distanceToPerpendicularDistance(minDistance, p, nearParam);
                }
                pixelConversion.distancePixelConversion(output, x, y, minDistance.distance);
            }
        }
    }

    public static void generatePseudoSDF_legacy(Bitmap.BitmapSection<Float> output, Shape shape, Range range, Vector2 scale, Vector2 translate) {
        generatePSDF_legacy(output, shape, range, scale, translate);
    }

    public static void generateMSDF_legacy(Bitmap.BitmapSection<Float> output, Shape shape, Range range, Vector2 scale, Vector2 translate, GeneratorConfig.ErrorCorrectionConfig errorCorrectionConfig) {
        var distanceMapping = new DistanceMapping(range);
        output.reorient(shape.getYAxisOrientation());
        var pixelConversion = new DistancePixelConversion.DistancePixelConversionMultiDistance(distanceMapping);
        for (var y = 0; y < output.height; ++y) {
            for (var x = 0; x < output.width; ++x) {
                var p = new Vector2(x + 0.5, y + 0.5).divide(scale).subtract(translate);

                class Struct {
                    SignedDistance minDistance = new SignedDistance();
                    @Nullable EdgeHolder nearEdge;
                    double nearParam;
                }
                var r = new Struct();
                var g = new Struct();
                var b = new Struct();

                for (var contour : shape.contours) {
                    for (var edge : contour.edges) {
                        var param = new double[1];
                        var distance = edge.get().signedDistance(p, param);
                        if (edge.get().color.has(EdgeColor.RED) && distance.compareTo(r.minDistance) < 0) {
                            r.minDistance = distance;
                            r.nearEdge = edge;
                            r.nearParam = param[0];
                        }
                        if (edge.get().color.has(EdgeColor.GREEN) && distance.compareTo(g.minDistance) < 0) {
                            g.minDistance = distance;
                            g.nearEdge = edge;
                            g.nearParam = param[0];
                        }
                        if (edge.get().color.has(EdgeColor.BLUE) && distance.compareTo(b.minDistance) < 0) {
                            b.minDistance = distance;
                            b.nearEdge = edge;
                            b.nearParam = param[0];
                        }
                    }
                }
                if (r.nearEdge != null) {
                    r.nearEdge.get().distanceToPerpendicularDistance(r.minDistance, p, r.nearParam);
                }
                if (g.nearEdge != null) {
                    g.nearEdge.get().distanceToPerpendicularDistance(g.minDistance, p, g.nearParam);
                }
                if (b.nearEdge != null) {
                    b.nearEdge.get().distanceToPerpendicularDistance(b.minDistance, p, b.nearParam);
                }
                pixelConversion.distancePixelConversion(output, x, y, new MultiDistance(
                        r.minDistance.distance,
                        g.minDistance.distance,
                        b.minDistance.distance
                ));
            }
        }
        errorCorrectionConfig.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
        MSDFErrorCorrection.msdfErrorCorrection(output, shape, new SDFTransformation(new Projection(scale, translate), range), new GeneratorConfig.MSDFGeneratorConfig(false, errorCorrectionConfig));
    }

    public static void generateMTSDF_legacy(Bitmap.BitmapSection<Float> output, Shape shape, Range range, Vector2 scale, Vector2 translate, GeneratorConfig.ErrorCorrectionConfig errorCorrectionConfig) {
        var distanceMapping = new DistanceMapping(range);
        output.reorient(shape.getYAxisOrientation());
        var pixelConversion = new DistancePixelConversion.DistancePixelConversionMultiAndTrueDistance(distanceMapping);
        for (var y = 0; y < output.height; ++y) {
            for (var x = 0; x < output.width; ++x) {
                var p = new Vector2(x + 0.5, y + 0.5).divide(scale).subtract(translate);
                var minDistance = new SignedDistance();

                class Struct {
                    SignedDistance minDistance = new SignedDistance();
                    @Nullable EdgeHolder nearEdge;
                    double nearParam;
                }
                var r = new Struct();
                var g = new Struct();
                var b = new Struct();

                for (var contour : shape.contours) {
                    for (var edge : contour.edges) {
                        var param = new double[1];
                        var distance = edge.get().signedDistance(p, param);
                        if (distance.compareTo(minDistance) < 0) {
                            minDistance = distance;
                        }
                        if (edge.get().color.has(EdgeColor.RED) && distance.compareTo(r.minDistance) < 0) {
                            r.minDistance = distance;
                            r.nearEdge = edge;
                            r.nearParam = param[0];
                        }
                        if (edge.get().color.has(EdgeColor.GREEN) && distance.compareTo(g.minDistance) < 0) {
                            g.minDistance = distance;
                            g.nearEdge = edge;
                            g.nearParam = param[0];
                        }
                        if (edge.get().color.has(EdgeColor.BLUE) && distance.compareTo(b.minDistance) < 0) {
                            b.minDistance = distance;
                            b.nearEdge = edge;
                            b.nearParam = param[0];
                        }
                    }
                }
                if (r.nearEdge != null) {
                    r.nearEdge.get().distanceToPerpendicularDistance(r.minDistance, p, r.nearParam);
                }
                if (g.nearEdge != null) {
                    g.nearEdge.get().distanceToPerpendicularDistance(g.minDistance, p, g.nearParam);
                }
                if (b.nearEdge != null) {
                    b.nearEdge.get().distanceToPerpendicularDistance(b.minDistance, p, b.nearParam);
                }
                pixelConversion.distancePixelConversion(output, x, y, new MultiAndTrueDistance(
                        r.minDistance.distance,
                        g.minDistance.distance,
                        b.minDistance.distance,
                        minDistance.distance
                ));
            }
        }
        errorCorrectionConfig.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
        MSDFErrorCorrection.msdfErrorCorrection(output, shape, new SDFTransformation(new Projection(scale, translate), range), new GeneratorConfig.MSDFGeneratorConfig(false, errorCorrectionConfig));
    }
}
