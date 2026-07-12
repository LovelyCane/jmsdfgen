package lovely.cane.jmsdfgen;

public class RenderSDF {
    private static float distVal(float dist, DistanceMapping mapping) {
        return (float) Arithmetic.clamp(mapping.apply(dist) + 0.5);
    }

    public static void renderSDF1_1(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange) {
        renderSDF1_1(output, sdf, sdfPxRange, 0.5f);
    }

    public static void renderSDF1_1(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange, float sdThreshold) {
        var scaleX = (double) sdf.width / output.width;
        var scaleY = (double) sdf.height / output.height;
        if (sdfPxRange.lower == sdfPxRange.upper) {
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[1];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = sd[0] >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[output.getPixelIndex(x, y)] = v;
                }
            }
        } else {
            sdfPxRange.mulEquals((double) (output.width + output.height) / (sdf.width + sdf.height));
            var mapping = DistanceMapping.inverse(sdfPxRange);
            var sdBias = 0.5f - sdThreshold;
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[1];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = distVal(sd[0] + sdBias, mapping);
                    output.pixels[output.getPixelIndex(x, y)] = v;
                }
            }
        }
    }

    public static void renderSDF3_1(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange) {
        renderSDF3_1(output, sdf, sdfPxRange, 0.5f);
    }

    public static void renderSDF3_1(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange, float sdThreshold) {
        var scaleX = (double) sdf.width / output.width;
        var scaleY = (double) sdf.height / output.height;
        if (sdfPxRange.lower == sdfPxRange.upper) {
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[1];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = sd[0] >= sdThreshold ? 1.0f : 0.0f;
                    var base = output.getPixelIndex(x, y);
                    output.pixels[base] = v;
                    output.pixels[base + 1] = v;
                    output.pixels[base + 2] = v;
                }
            }
        } else {
            sdfPxRange.mulEquals((double) (output.width + output.height) / (sdf.width + sdf.height));
            var mapping = DistanceMapping.inverse(sdfPxRange);
            var sdBias = 0.5f - sdThreshold;
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[1];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = distVal(sd[0] + sdBias, mapping);
                    var base = output.getPixelIndex(x, y);
                    output.pixels[base] = v;
                    output.pixels[base + 1] = v;
                    output.pixels[base + 2] = v;
                }
            }
        }
    }

    public static void renderSDF1_3(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange) {
        renderSDF1_3(output, sdf, sdfPxRange, 0.5f);
    }

    public static void renderSDF1_3(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange, float sdThreshold) {
        var scaleX = (double) sdf.width / output.width;
        var scaleY = (double) sdf.height / output.height;
        if (sdfPxRange.lower == sdfPxRange.upper) {
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[3];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = Arithmetic.median(sd[0], sd[1], sd[2]) >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[output.getPixelIndex(x, y)] = v;
                }
            }
        } else {
            sdfPxRange.mulEquals((double) (output.width + output.height) / (sdf.width + sdf.height));
            var mapping = DistanceMapping.inverse(sdfPxRange);
            var sdBias = 0.5f - sdThreshold;
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[3];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = distVal(Arithmetic.median(sd[0], sd[1], sd[2]) + sdBias, mapping);
                    output.pixels[output.getPixelIndex(x, y)] = v;
                }
            }
        }
    }

    public static void renderSDF3_3(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange) {
        renderSDF3_3(output, sdf, sdfPxRange, 0.5f);
    }

    public static void renderSDF3_3(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange, float sdThreshold) {
        var scaleX = (double) sdf.width / output.width;
        var scaleY = (double) sdf.height / output.height;
        if (sdfPxRange.lower == sdfPxRange.upper) {
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[3];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var base = output.getPixelIndex(x, y);
                    output.pixels[base] = sd[0] >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[base + 1] = sd[1] >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[base + 2] = sd[2] >= sdThreshold ? 1.0f : 0.0f;
                }
            }
        } else {
            sdfPxRange.mulEquals((double) (output.width + output.height) / (sdf.width + sdf.height));
            var mapping = DistanceMapping.inverse(sdfPxRange);
            var sdBias = 0.5f - sdThreshold;
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[3];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var base = output.getPixelIndex(x, y);
                    output.pixels[base] = distVal(sd[0] + sdBias, mapping);
                    output.pixels[base + 1] = distVal(sd[1] + sdBias, mapping);
                    output.pixels[base + 2] = distVal(sd[2] + sdBias, mapping);
                }
            }
        }
    }

    public static void renderSDF1_4(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange) {
        renderSDF1_4(output, sdf, sdfPxRange, 0.5f);
    }

    public static void renderSDF1_4(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange, float sdThreshold) {
        var scaleX = (double) sdf.width / output.width;
        var scaleY = (double) sdf.height / output.height;
        if (sdfPxRange.lower == sdfPxRange.upper) {
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[4];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = Arithmetic.median(sd[0], sd[1], sd[2]) >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[output.getPixelIndex(x, y)] = v;
                }
            }
        } else {
            sdfPxRange.mulEquals((double) (output.width + output.height) / (sdf.width + sdf.height));
            var mapping = DistanceMapping.inverse(sdfPxRange);
            var sdBias = 0.5f - sdThreshold;
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[4];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var v = distVal(Arithmetic.median(sd[0], sd[1], sd[2]) + sdBias, mapping);
                    output.pixels[output.getPixelIndex(x, y)] = v;
                }
            }
        }
    }

    public static void renderSDF4_4(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange) {
        renderSDF4_4(output, sdf, sdfPxRange, 0.5f);
    }

    public static void renderSDF4_4(Bitmap.BitmapSection<Float> output, Bitmap.BitmapConstSection<Float> sdf, Range sdfPxRange, float sdThreshold) {
        var scaleX = (double) sdf.width / output.width;
        var scaleY = (double) sdf.height / output.height;
        if (sdfPxRange.lower == sdfPxRange.upper) {
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[4];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var base = output.getPixelIndex(x, y);
                    output.pixels[base] = sd[0] >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[base + 1] = sd[1] >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[base + 2] = sd[2] >= sdThreshold ? 1.0f : 0.0f;
                    output.pixels[base + 3] = sd[3] >= sdThreshold ? 1.0f : 0.0f;
                }
            }
        } else {
            sdfPxRange.mulEquals((double) (output.width + output.height) / (sdf.width + sdf.height));
            var mapping = DistanceMapping.inverse(sdfPxRange);
            var sdBias = 0.5f - sdThreshold;
            for (var y = 0; y < output.height; ++y) {
                for (var x = 0; x < output.width; ++x) {
                    var sd = new float[4];
                    Bitmap.interpolate(sd, sdf, new Vector2(scaleX * (x + 0.5), scaleY * (y + 0.5)));
                    var base = output.getPixelIndex(x, y);
                    output.pixels[base] = distVal(sd[0] + sdBias, mapping);
                    output.pixels[base + 1] = distVal(sd[1] + sdBias, mapping);
                    output.pixels[base + 2] = distVal(sd[2] + sdBias, mapping);
                    output.pixels[base + 3] = distVal(sd[3] + sdBias, mapping);
                }
            }
        }
    }

    public static void simulate8bit1(Bitmap.BitmapSection<Float> bitmap) {
        for (var y = 0; y < bitmap.height; ++y) {
            for (var x = 0; x < bitmap.width; ++x) {
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < 1; ++c) {
                    float val = bitmap.pixels[base + c];
                    bitmap.pixels[base + c] = Arithmetic.pixelByteToFloat(Arithmetic.pixelFloatToByte(val));
                }
            }
        }
    }

    public static void simulate8bit3(Bitmap.BitmapSection<Float> bitmap) {
        for (var y = 0; y < bitmap.height; ++y) {
            for (var x = 0; x < bitmap.width; ++x) {
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < 3; ++c) {
                    float val = bitmap.pixels[base + c];
                    bitmap.pixels[base + c] = Arithmetic.pixelByteToFloat(Arithmetic.pixelFloatToByte(val));
                }
            }
        }
    }

    public static void simulate8bit4(Bitmap.BitmapSection<Float> bitmap) {
        for (var y = 0; y < bitmap.height; ++y) {
            for (var x = 0; x < bitmap.width; ++x) {
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < 4; ++c) {
                    float val = bitmap.pixels[base + c];
                    bitmap.pixels[base + c] = Arithmetic.pixelByteToFloat(Arithmetic.pixelFloatToByte(val));
                }
            }
        }
    }
}
