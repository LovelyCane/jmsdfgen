package lovely.cane.jmsdfgen;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SaveBmp {
    private static final byte[] BMP_LINEAR_COLOR_SPACE_SPECIFICATION = {
            (byte)0xf8, (byte)0xc2, 0x64, 0x1a, 0x08, 0x3d, (byte)0x9b, 0x0d, 0x11, 0x36, 0x3c, 0x01,
            0x1c, (byte)0xeb, (byte)0xe2, 0x16, 0x39, (byte)0xd6, (byte)0xc5, 0x2d, 0x09, (byte)0xf9, (byte)0xa0, 0x07,
            (byte)0xdf, 0x4f, (byte)0x8d, 0x0b, (byte)0xc0, (byte)0xec, (byte)0x9e, 0x04, (byte)0xf4, (byte)0xfd, (byte)0xd4, 0x3c,
            0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00
    };

    public static boolean saveBmpFloat(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            var channels = bitmap.channels;
            if (channels == 1) return saveBmpFloat1(bitmap, filename);
            else if (channels == 3) return saveBmpFloat3(bitmap, filename);
            else if (channels == 4) return saveBmpFloat4(bitmap, filename);
            else return false;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveBmpByte(Bitmap.BitmapConstSection<Byte> bitmap, String filename) {
        try {
            var channels = bitmap.channels;
            if (channels == 1) return saveBmpByte1(bitmap, filename);
            else if (channels == 3) return saveBmpByte3(bitmap, filename);
            else if (channels == 4) return saveBmpByte4(bitmap, filename);
            else return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean saveBmpByte1(Bitmap.BitmapConstSection<Byte> bitmap, String filename) throws IOException {
        bitmap.reorient(YAxisOrientation.Y_UPWARD);
        var width = bitmap.width;
        var height = bitmap.height;
        var paddedWidth = (width + 3) & ~3;
        var header = buildBmpHeader(1, width, height, paddedWidth);
        try (var fos = new FileOutputStream(filename)) {
            fos.write(header);
            var row = new byte[width];
            var padding = new byte[paddedWidth - width];
            for (var y = 0; y < height; y++) {
                var base = bitmap.getPixelIndex(0, y);
                for (var x = 0; x < width; x++) {
                    row[x] = bitmap.pixels[base + x];
                }
                fos.write(row);
                fos.write(padding);
            }
        }
        return true;
    }

    private static boolean saveBmpByte3(Bitmap.BitmapConstSection<Byte> bitmap, String filename) throws IOException {
        bitmap.reorient(YAxisOrientation.Y_UPWARD);
        var width = bitmap.width;
        var height = bitmap.height;
        var bytesPerPixel = 3;
        var paddedWidth = (bytesPerPixel * width + 3) & ~3;
        var header = buildBmpHeader(bytesPerPixel, width, height, paddedWidth);
        try (var fos = new FileOutputStream(filename)) {
            fos.write(header);
            var bgr = new byte[3];
            var padding = new byte[paddedWidth - bytesPerPixel * width];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var base = bitmap.getPixelIndex(x, y);
                    bgr[0] = bitmap.pixels[base + 2];
                    bgr[1] = bitmap.pixels[base + 1];
                    bgr[2] = bitmap.pixels[base];
                    fos.write(bgr);
                }
                fos.write(padding);
            }
        }
        return true;
    }

    private static boolean saveBmpByte4(Bitmap.BitmapConstSection<Byte> bitmap, String filename) throws IOException {
        bitmap.reorient(YAxisOrientation.Y_UPWARD);
        var width = bitmap.width;
        var height = bitmap.height;
        var bytesPerPixel = 4;
        var paddedWidth = bytesPerPixel * width;
        var header = buildBmpHeader(bytesPerPixel, width, height, paddedWidth);
        try (var fos = new FileOutputStream(filename)) {
            fos.write(header);
            var bgra = new byte[4];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var base = bitmap.getPixelIndex(x, y);
                    bgra[0] = bitmap.pixels[base + 2];
                    bgra[1] = bitmap.pixels[base + 1];
                    bgra[2] = bitmap.pixels[base];
                    bgra[3] = bitmap.pixels[base + 3];
                    fos.write(bgra);
                }
            }
        }
        return true;
    }

    private static boolean saveBmpFloat1(Bitmap.BitmapConstSection<Float> bitmap, String filename) throws IOException {
        bitmap.reorient(YAxisOrientation.Y_UPWARD);
        var width = bitmap.width;
        var height = bitmap.height;
        var paddedWidth = (width + 3) & ~3;
        var header = buildBmpHeader(1, width, height, paddedWidth);
        try (var fos = new FileOutputStream(filename)) {
            fos.write(header);
            var row = new byte[width];
            var padding = new byte[paddedWidth - width];
            for (var y = 0; y < height; y++) {
                var base = bitmap.getPixelIndex(0, y);
                for (var x = 0; x < width; x++) {
                    row[x] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + x]);
                }
                fos.write(row);
                fos.write(padding);
            }
        }
        return true;
    }

    private static boolean saveBmpFloat3(Bitmap.BitmapConstSection<Float> bitmap, String filename) throws IOException {
        bitmap.reorient(YAxisOrientation.Y_UPWARD);
        var width = bitmap.width;
        var height = bitmap.height;
        var bytesPerPixel = 3;
        var paddedWidth = (bytesPerPixel * width + 3) & ~3;
        var header = buildBmpHeader(bytesPerPixel, width, height, paddedWidth);
        try (var fos = new FileOutputStream(filename)) {
            fos.write(header);
            var bgr = new byte[3];
            var padding = new byte[paddedWidth - bytesPerPixel * width];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var base = bitmap.getPixelIndex(x, y);
                    bgr[0] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + 2]);
                    bgr[1] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + 1]);
                    bgr[2] = Arithmetic.pixelFloatToByte(bitmap.pixels[base]);
                    fos.write(bgr);
                }
                fos.write(padding);
            }
        }
        return true;
    }

    private static boolean saveBmpFloat4(Bitmap.BitmapConstSection<Float> bitmap, String filename) throws IOException {
        bitmap.reorient(YAxisOrientation.Y_UPWARD);
        var width = bitmap.width;
        var height = bitmap.height;
        var bytesPerPixel = 4;
        var paddedWidth = bytesPerPixel * width;
        var header = buildBmpHeader(bytesPerPixel, width, height, paddedWidth);
        try (var fos = new FileOutputStream(filename)) {
            fos.write(header);
            var bgra = new byte[4];
            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var base = bitmap.getPixelIndex(x, y);
                    bgra[0] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + 2]);
                    bgra[1] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + 1]);
                    bgra[2] = Arithmetic.pixelFloatToByte(bitmap.pixels[base]);
                    bgra[3] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + 3]);
                    fos.write(bgra);
                }
            }
        }
        return true;
    }

    private static byte[] buildBmpHeader(int bytesPerPixel, int width, int height, int paddedWidth) {
        var colorTableEntries = bytesPerPixel == 1 ? 256 : 0;
        var bitmapStart = 14 + 108 + 4 * colorTableEntries;
        var bitmapSize = paddedWidth * height;
        var fileSize = bitmapStart + bitmapSize;

        var buf = ByteBuffer.allocate(bitmapStart);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.putShort((short) 0x4D42);
        buf.putInt(fileSize);
        buf.putShort((short) 0);
        buf.putShort((short) 0);
        buf.putInt(bitmapStart);

        buf.putInt(108);
        buf.putInt(width);
        buf.putInt(height);
        buf.putShort((short) 1);
        buf.putShort((short) (8 * bytesPerPixel));
        buf.putInt(bytesPerPixel == 4 ? 3 : 0);
        buf.putInt(bitmapSize);
        buf.putInt(2835);
        buf.putInt(2835);
        buf.putInt(colorTableEntries);
        buf.putInt(colorTableEntries);
        buf.putInt(0x00FF0000);
        buf.putInt(0x0000FF00);
        buf.putInt(0x000000FF);
        buf.putInt(bytesPerPixel == 4 ? 0xFF000000 : 0);
        buf.putInt(0);
        buf.put(BMP_LINEAR_COLOR_SPACE_SPECIFICATION);

        if (bytesPerPixel == 1) {
            var gray = 0;
            for (var i = 0; i < 256; i++) {
                buf.putInt(gray | 0xFF000000);
                gray += 0x00010101;
            }
        }

        return buf.array();
    }
}
