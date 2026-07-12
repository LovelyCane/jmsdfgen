package lovely.cane.jmsdfgen;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class SaveTiff {
    private SaveTiff() {
    }

    public static boolean saveTiff(Bitmap.BitmapConstSection<Float> bitmap, String filename) {
        try {
            var channels = bitmap.channels;
            bitmap.reorient(YAxisOrientation.Y_DOWNWARD);
            var width = bitmap.width;
            var height = bitmap.height;
            var header = buildTiffHeader(width, height, channels);
            try (var fos = new FileOutputStream(filename)) {
                fos.write(header);
                var rowLength = channels * width;
                var row = new float[rowLength];
                var rowBuffer = ByteBuffer.allocate(rowLength * 4);
                rowBuffer.order(ByteOrder.LITTLE_ENDIAN);
                for (var y = 0; y < height; y++) {
                    var base = bitmap.getPixelIndex(0, y);
                    System.arraycopy(bitmap.pixels, base, row, 0, rowLength);
                    rowBuffer.clear();
                    rowBuffer.asFloatBuffer().put(row);
                    fos.write(rowBuffer.array());
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] buildTiffHeader(int width, int height, int channels) {
        var offsetIFD = 8;
        var sizeIFD = 2 + 15 * 12 + 4;
        var offsetExtra = offsetIFD + sizeIFD;
        var offsetBitsPerSample = (channels > 1) ? offsetExtra : 0;
        var offsetXRes = (channels > 1) ? offsetBitsPerSample + channels * 2 : offsetExtra;
        var offsetYRes = offsetXRes + 8;
        var offsetSampleFormat = (channels > 1) ? offsetYRes + 8 : 0;
        var offsetSMin = (channels > 1) ? offsetSampleFormat + channels * 2 : 0;
        var offsetSMax = (channels > 1) ? offsetSMin + channels * 4 : 0;
        var offsetPixelData = (channels > 1) ? offsetSMax + channels * 4 : offsetYRes + 8;

        var buf = ByteBuffer.allocate(offsetPixelData);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.putShort((short) 0x4949);
        buf.putShort((short) 42);
        buf.putInt(8);

        buf.putShort((short) 15);

        writeIFDEntry(buf, 0x0100, 4, 1, width);
        writeIFDEntry(buf, 0x0101, 4, 1, height);
        writeIFDEntryBitsPerSample(buf, channels, offsetBitsPerSample);
        writeIFDEntryShortInline(buf, 0x0103, (short) 1);
        writeIFDEntryShortInline(buf, 0x0106, (short) (channels >= 3 ? 2 : 1));
        writeIFDEntry(buf, 0x0111, 4, 1, offsetPixelData);
        writeIFDEntryShortInline(buf, 0x0115, (short) channels);
        writeIFDEntry(buf, 0x0116, 4, 1, height);
        writeIFDEntry(buf, 0x0117, 4, 1, 4 * channels * width * height);
        writeIFDEntry(buf, 0x011A, 5, 1, offsetXRes);
        writeIFDEntry(buf, 0x011B, 5, 1, offsetYRes);
        writeIFDEntryShortInline(buf, 0x0128, (short) 2);
        writeIFDEntrySampleFormat(buf, channels, offsetSampleFormat);
        writeIFDEntrySMin(buf, channels, offsetSMin);
        writeIFDEntrySMax(buf, channels, offsetSMax);

        buf.putInt(0);

        if (channels > 1) {
            for (var i = 0; i < channels; i++)
                buf.putShort((short) 32);
            buf.putInt(300);
            buf.putInt(1);
            buf.putInt(300);
            buf.putInt(1);
            for (var i = 0; i < channels; i++)
                buf.putShort((short) 3);
            for (var i = 0; i < channels; i++)
                buf.putFloat(0.0f);
            for (var i = 0; i < channels; i++)
                buf.putFloat(1.0f);
        } else {
            buf.putInt(300);
            buf.putInt(1);
        }

        return buf.array();
    }

    private static void writeIFDEntry(ByteBuffer buf, int tag, int type, int count, int value) {
        buf.putShort((short) tag);
        buf.putShort((short) type);
        buf.putInt(count);
        buf.putInt(value);
    }

    private static void writeIFDEntryBitsPerSample(ByteBuffer buf, int channels, int offsetBitsPerSample) {
        buf.putShort((short) 0x0102);
        buf.putShort((short) 3);
        buf.putInt(channels);
        if (channels > 1) {
            buf.putInt(offsetBitsPerSample);
        } else {
            buf.putShort((short) 32);
            buf.putShort((short) 0);
        }
    }

    private static void writeIFDEntrySampleFormat(ByteBuffer buf, int channels, int offsetSampleFormat) {
        buf.putShort((short) 0x0153);
        buf.putShort((short) 3);
        buf.putInt(channels);
        if (channels > 1) {
            buf.putInt(offsetSampleFormat);
        } else {
            buf.putShort((short) 3);
            buf.putShort((short) 0);
        }
    }

    private static void writeIFDEntrySMin(ByteBuffer buf, int channels, int offsetSMin) {
        buf.putShort((short) 0x0154);
        buf.putShort((short) 11);
        buf.putInt(channels);
        if (channels > 1) {
            buf.putInt(offsetSMin);
        } else {
            buf.putFloat(0.0f);
        }
    }

    private static void writeIFDEntrySMax(ByteBuffer buf, int channels, int offsetSMax) {
        buf.putShort((short) 0x0155);
        buf.putShort((short) 11);
        buf.putInt(channels);
        if (channels > 1) {
            buf.putInt(offsetSMax);
        } else {
            buf.putFloat(1.0f);
        }
    }

    private static void writeIFDEntryShortInline(ByteBuffer buf, int tag, short value) {
        buf.putShort((short) tag);
        buf.putShort((short) 3);
        buf.putInt(1);
        buf.putShort(value);
        buf.putShort((short) 0);
    }
}
