package lovely.cane.jmsdfgen;

import java.util.function.IntFunction;

import static lovely.cane.jmsdfgen.Arithmetic.clamp;
import static lovely.cane.jmsdfgen.Arithmetic.mix;

public class Bitmap<T> {
    public final T[] pixels;
    public final int width;
    public final int height;
    public final int channels;
    public final YAxisOrientation yOrientation;

    public Bitmap(int width, int height, int channels, YAxisOrientation yOrientation, IntFunction<T[]> arrayCreator) {
        pixels = arrayCreator.apply(channels * width * height);
        this.width = width;
        this.height = height;
        this.channels = channels;
        this.yOrientation = yOrientation;
    }

    public Bitmap(int width, int height, int channels, IntFunction<T[]> arrayCreator) {
        this(width, height, channels, YAxisOrientation.Y_UPWARD, arrayCreator);
    }

    public Bitmap(BitmapConstRef<T> ref, IntFunction<T[]> arrayCreator) {
        pixels = arrayCreator.apply(ref.channels() * ref.width() * ref.height());
        width = ref.width();
        height = ref.height();
        channels = ref.channels();
        yOrientation = ref.yOrientation();
        System.arraycopy(ref.pixels(), 0, pixels, 0, pixels.length);
    }

    public Bitmap(BitmapConstSection<T> section, IntFunction<T[]> arrayCreator) {
        pixels = arrayCreator.apply(section.channels * section.width * section.height);
        width = section.width;
        height = section.height;
        channels = section.channels;
        yOrientation = section.yOrientation;
        var rowLength = channels * width;
        var srcOffset = section.baseOffset;
        var dstOffset = 0;
        for (var y = 0; y < height; y++) {
            System.arraycopy(section.pixels, srcOffset, pixels, dstOffset, rowLength);
            srcOffset += section.rowStride;
            dstOffset += rowLength;
        }
    }

    public Bitmap(Bitmap<T> orig) {
        pixels = orig.pixels.clone();
        width = orig.width;
        height = orig.height;
        channels = orig.channels;
        yOrientation = orig.yOrientation;
    }

    public int getPixelIndex(int x, int y) {
        return channels * (width * y + x);
    }

    public BitmapRef<T> toBitmapRef() {
        return new BitmapRef<>(pixels, width, height, channels, yOrientation);
    }

    public BitmapConstRef<T> toBitmapConstRef() {
        return new BitmapConstRef<>(pixels, width, height, channels, yOrientation);
    }

    public BitmapSection<T> toBitmapSection() {
        return new BitmapSection<>(pixels, width, height, channels, yOrientation);
    }

    public BitmapConstSection<T> toBitmapConstSection() {
        return new BitmapConstSection<>(pixels, width, height, channels, yOrientation);
    }

    public BitmapSection<T> getSection(int xMin, int yMin, int xMax, int yMax) {
        return new BitmapSection<>(
                pixels, channels * (width * yMin + xMin), xMax - xMin, yMax - yMin,
                channels * width, channels, yOrientation
        );
    }

    public BitmapConstSection<T> getConstSection(int xMin, int yMin, int xMax, int yMax) {
        return getSection(xMin, yMin, xMax, yMax).toConstSection();
    }

    public static void interpolate(float[] output, BitmapConstSection<Float> bitmap, Vector2 pos) {
        var channels = bitmap.channels;
        var x = clamp((float) pos.x, bitmap.width);
        var y = clamp((float) pos.y, bitmap.height);
        x -= 0.5f;
        y -= 0.5f;
        var l = (int) Math.floor(x);
        var b = (int) Math.floor(y);
        var r = l + 1;
        var t = b + 1;
        var lr = x - l;
        var bt = y - b;
        l = clamp(l, bitmap.width - 1);
        r = clamp(r, bitmap.width - 1);
        b = clamp(b, bitmap.height - 1);
        t = clamp(t, bitmap.height - 1);
        var i00 = bitmap.getPixelIndex(l, b);
        var i10 = bitmap.getPixelIndex(r, b);
        var i01 = bitmap.getPixelIndex(l, t);
        var i11 = bitmap.getPixelIndex(r, t);
        for (var i = 0; i < channels; i++) {
            var v00 = bitmap.pixels[i00 + i];
            var v10 = bitmap.pixels[i10 + i];
            var v01 = bitmap.pixels[i01 + i];
            var v11 = bitmap.pixels[i11 + i];
            output[i] = mix(mix(v00, v10, lr), mix(v01, v11, lr), bt);
        }
    }

    public record BitmapConstRef<T>(T[] pixels, int width, int height, int channels,
                                    YAxisOrientation yOrientation) {
        public int getPixelIndex(int x, int y) {
            return channels * (width * y + x);
        }

        public BitmapConstSection<T> toConstSection() {
            return new BitmapConstSection<>(pixels, width, height, channels, yOrientation);
        }

        public BitmapConstSection<T> getSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapConstSection<>(
                    pixels, channels * (width * yMin + xMin), xMax - xMin, yMax - yMin,
                    channels * width, channels, yOrientation
            );
        }

        public BitmapConstSection<T> getConstSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapConstSection<>(
                    pixels, channels * (width * yMin + xMin), xMax - xMin, yMax - yMin,
                    channels * width, channels, yOrientation
            );
        }
    }

    public static class BitmapConstSection<T> {
        public final T[] pixels;
        public int baseOffset;
        public final int width;
        public final int height;
        public int rowStride;
        public final int channels;
        public YAxisOrientation yOrientation;

        public BitmapConstSection(T[] pixels, int width, int height, int channels, YAxisOrientation yOrientation) {
            this.pixels = pixels;
            baseOffset = 0;
            this.width = width;
            this.height = height;
            rowStride = channels * width;
            this.channels = channels;
            this.yOrientation = yOrientation;
        }

        public BitmapConstSection(T[] pixels, int baseOffset, int width, int height, int rowStride, int channels, YAxisOrientation yOrientation) {
            this.pixels = pixels;
            this.baseOffset = baseOffset;
            this.width = width;
            this.height = height;
            this.rowStride = rowStride;
            this.channels = channels;
            this.yOrientation = yOrientation;
        }

        public int getPixelIndex(int x, int y) {
            return baseOffset + rowStride * y + channels * x;
        }

        public BitmapConstSection<T> getSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapConstSection<>(pixels, baseOffset + rowStride * yMin + channels * xMin, xMax - xMin, yMax - yMin, rowStride, channels, yOrientation);
        }

        public BitmapConstSection<T> getConstSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapConstSection<>(pixels, baseOffset + rowStride * yMin + channels * xMin, xMax - xMin, yMax - yMin, rowStride, channels, yOrientation);
        }

        public void reorient(YAxisOrientation newYAxisOrientation) {
            if (yOrientation != newYAxisOrientation) {
                baseOffset += rowStride * (height - 1);
                rowStride = -rowStride;
                yOrientation = newYAxisOrientation;
            }
        }
    }

    public record BitmapRef<T>(T[] pixels, int width, int height, int channels, YAxisOrientation yOrientation) {
        public int getPixelIndex(int x, int y) {
            return channels * (width * y + x);
        }

        public BitmapConstRef<T> toConstRef() {
            return new BitmapConstRef<>(pixels, width, height, channels, yOrientation);
        }

        public BitmapSection<T> toSection() {
            return new BitmapSection<>(pixels, width, height, channels, yOrientation);
        }

        public BitmapConstSection<T> toConstSection() {
            return new BitmapConstSection<>(pixels, width, height, channels, yOrientation);
        }

        public BitmapSection<T> getSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapSection<>(pixels, channels * (width * yMin + xMin), xMax - xMin, yMax - yMin, channels * width, channels, yOrientation);
        }

        public BitmapConstSection<T> getConstSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapConstSection<>(pixels, channels * (width * yMin + xMin), xMax - xMin, yMax - yMin, channels * width, channels, yOrientation);
        }
    }

    public static class BitmapSection<T> {
        public final T[] pixels;
        public int baseOffset;
        public final int width;
        public final int height;
        public int rowStride;
        public final int channels;
        public YAxisOrientation yOrientation;

        public BitmapSection(T[] pixels, int width, int height, int channels, YAxisOrientation yOrientation) {
            this.pixels = pixels;
            baseOffset = 0;
            this.width = width;
            this.height = height;
            rowStride = channels * width;
            this.channels = channels;
            this.yOrientation = yOrientation;
        }

        public BitmapSection(T[] pixels, int baseOffset, int width, int height, int rowStride, int channels, YAxisOrientation yOrientation) {
            this.pixels = pixels;
            this.baseOffset = baseOffset;
            this.width = width;
            this.height = height;
            this.rowStride = rowStride;
            this.channels = channels;
            this.yOrientation = yOrientation;
        }

        public int getPixelIndex(int x, int y) {
            return baseOffset + rowStride * y + channels * x;
        }

        @SafeVarargs
        public final void setPixel(int x, int y, T... value) {
            var index = getPixelIndex(x, y);
            if (channels >= 0) System.arraycopy(value, 0, pixels, index, channels);
        }

        public BitmapConstSection<T> toConstSection() {
            return new BitmapConstSection<>(pixels, baseOffset, width, height, rowStride, channels, yOrientation);
        }

        public BitmapSection<T> getSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapSection<>(pixels, baseOffset + rowStride * yMin + channels * xMin, xMax - xMin, yMax - yMin, rowStride, channels, yOrientation);
        }

        public BitmapConstSection<T> getConstSection(int xMin, int yMin, int xMax, int yMax) {
            return new BitmapConstSection<>(pixels, baseOffset + rowStride * yMin + channels * xMin, xMax - xMin, yMax - yMin, rowStride, channels, yOrientation);
        }

        public void reorient(YAxisOrientation newYAxisOrientation) {
            if (yOrientation != newYAxisOrientation) {
                baseOffset += rowStride * (height - 1);
                rowStride = -rowStride;
                yOrientation = newYAxisOrientation;
            }
        }
    }
}
