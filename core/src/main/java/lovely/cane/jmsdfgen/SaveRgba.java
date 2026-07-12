package lovely.cane.jmsdfgen;

import java.io.FileOutputStream;

public final class SaveRgba {
    private SaveRgba() {
    }

    public static boolean saveRgbaFloat(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        if (bitmap.channels == 1) return saveRgbaFloat1(bitmap, filename);
        else if (bitmap.channels == 3) return saveRgbaFloat3(bitmap, filename);
        else return saveRgbaFloat4(bitmap, filename);
    }

    public static boolean saveRgbaByte1(Bitmap.BitmapConstSection<Byte> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var header = createRgbaHeader(bitmap.width, bitmap.height);
            var rgba = new byte[4];
            rgba[3] = (byte) 0xff;
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var x = 0; x < bitmap.width; ++x) {
                        byte v = bitmap.pixels[base + x];
                        rgba[0] = v;
                        rgba[1] = v;
                        rgba[2] = v;
                        fos.write(rgba);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveRgbaByte3(Bitmap.BitmapConstSection<Byte> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var header = createRgbaHeader(bitmap.width, bitmap.height);
            var rgba = new byte[4];
            rgba[3] = (byte) 0xff;
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var x = 0; x < bitmap.width; ++x) {
                        var i = base + 3 * x;
                        rgba[0] = bitmap.pixels[i];
                        rgba[1] = bitmap.pixels[i + 1];
                        rgba[2] = bitmap.pixels[i + 2];
                        fos.write(rgba);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveRgbaByte4(Bitmap.BitmapConstSection<Byte> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var header = createRgbaHeader(bitmap.width, bitmap.height);
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                var rowBytes = 4 * bitmap.width;
                var row = new byte[rowBytes];
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var i = 0; i < rowBytes; ++i) {
                        row[i] = bitmap.pixels[base + i];
                    }
                    fos.write(row);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveRgbaFloat1(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var header = createRgbaHeader(bitmap.width, bitmap.height);
            var rgba = new byte[4];
            rgba[3] = (byte) 0xff;
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var x = 0; x < bitmap.width; ++x) {
                        var v = Arithmetic.pixelFloatToByte(bitmap.pixels[base + x]);
                        rgba[0] = v;
                        rgba[1] = v;
                        rgba[2] = v;
                        fos.write(rgba);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveRgbaFloat3(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var header = createRgbaHeader(bitmap.width, bitmap.height);
            var rgba = new byte[4];
            rgba[3] = (byte) 0xff;
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var x = 0; x < bitmap.width; ++x) {
                        var i = base + 3 * x;
                        rgba[0] = Arithmetic.pixelFloatToByte(bitmap.pixels[i]);
                        rgba[1] = Arithmetic.pixelFloatToByte(bitmap.pixels[i + 1]);
                        rgba[2] = Arithmetic.pixelFloatToByte(bitmap.pixels[i + 2]);
                        fos.write(rgba);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveRgbaFloat4(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var header = createRgbaHeader(bitmap.width, bitmap.height);
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                var rgba = new byte[4];
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var x = 0; x < bitmap.width; ++x) {
                        var i = base + 4 * x;
                        rgba[0] = Arithmetic.pixelFloatToByte(bitmap.pixels[i]);
                        rgba[1] = Arithmetic.pixelFloatToByte(bitmap.pixels[i + 1]);
                        rgba[2] = Arithmetic.pixelFloatToByte(bitmap.pixels[i + 2]);
                        rgba[3] = Arithmetic.pixelFloatToByte(bitmap.pixels[i + 3]);
                        fos.write(rgba);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] createRgbaHeader(int width, int height) {
        var header = new byte[12];
        header[0] = 'R';
        header[1] = 'G';
        header[2] = 'B';
        header[3] = 'A';
        header[4] = (byte) (width >> 24);
        header[5] = (byte) (width >> 16);
        header[6] = (byte) (width >> 8);
        header[7] = (byte) width;
        header[8] = (byte) (height >> 24);
        header[9] = (byte) (height >> 16);
        header[10] = (byte) (height >> 8);
        header[11] = (byte) height;
        return header;
    }
}
