package lovely.cane.jmsdfgen;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SaveFl32 {
    private SaveFl32() {
    }

    public static boolean saveFl32(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            bitmap.reorient(YAxisOrientation.Y_UPWARD);
            var header = new byte[16];
            header[0] = (byte) 'F';
            header[1] = (byte) 'L';
            header[2] = (byte) '3';
            header[3] = (byte) '2';
            header[4] = (byte) bitmap.height;
            header[5] = (byte) (bitmap.height >> 8);
            header[6] = (byte) (bitmap.height >> 16);
            header[7] = (byte) (bitmap.height >> 24);
            header[8] = (byte) bitmap.width;
            header[9] = (byte) (bitmap.width >> 8);
            header[10] = (byte) (bitmap.width >> 16);
            header[11] = (byte) (bitmap.width >> 24);
            header[12] = (byte) bitmap.channels;
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                var rowLength = bitmap.channels * bitmap.width;
                var row = new float[rowLength];
                var byteBuffer = ByteBuffer.allocate(rowLength * Float.BYTES);
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                for (var y = 0; y < bitmap.height; ++y) {
                    var base = bitmap.getPixelIndex(0, y);
                    for (var i = 0; i < rowLength; i++) {
                        row[i] = bitmap.pixels[base + i];
                    }
                    byteBuffer.clear();
                    byteBuffer.asFloatBuffer().put(row);
                    fos.write(byteBuffer.array());
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}