package lovely.cane.jmsdfgen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class SavePng {
    private SavePng() {
    }

    public static boolean savePngByte(Bitmap.BitmapConstSection<Byte> bitmap, String filename) {
        try {
            var width = bitmap.width;
            var height = bitmap.height;
            var channels = bitmap.channels;

            int imageType;
            switch (channels) {
                case 1:
                    imageType = BufferedImage.TYPE_BYTE_GRAY;
                    break;
                case 3:
                    imageType = BufferedImage.TYPE_3BYTE_BGR;
                    break;
                case 4:
                    imageType = BufferedImage.TYPE_4BYTE_ABGR;
                    break;
                default:
                    return false;
            }

            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var image = new BufferedImage(width, height, imageType);
            var raster = image.getRaster();
            var row = new byte[channels * width];

            for (var y = 0; y < height; y++) {
                var base = bitmap.getPixelIndex(0, y);
                System.arraycopy(bitmap.pixels, base, row, 0, row.length);
                raster.setDataElements(0, y, width, 1, row);
            }

            return ImageIO.write(image, "png", new File(filename));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean savePngFloat(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            var width = bitmap.width;
            var height = bitmap.height;
            var channels = bitmap.channels;

            int imageType;
            switch (channels) {
                case 1:
                    imageType = BufferedImage.TYPE_BYTE_GRAY;
                    break;
                case 3:
                    imageType = BufferedImage.TYPE_3BYTE_BGR;
                    break;
                case 4:
                    imageType = BufferedImage.TYPE_4BYTE_ABGR;
                    break;
                default:
                    return false;
            }

            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var image = new BufferedImage(width, height, imageType);
            var raster = image.getRaster();
            var row = new byte[channels * width];

            for (var y = 0; y < height; y++) {
                var base = bitmap.getPixelIndex(0, y);
                for (var i = 0; i < row.length; i++) {
                    row[i] = Arithmetic.pixelFloatToByte(bitmap.pixels[base + i]);
                }
                raster.setDataElements(0, y, width, 1, row);
            }

            return ImageIO.write(image, "png", new File(filename));
        } catch (IOException e) {
            return false;
        }
    }
}
