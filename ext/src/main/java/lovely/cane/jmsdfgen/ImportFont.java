package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.NativeType;
import org.lwjgl.util.freetype.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import static lovely.cane.jmsdfgen.Vector2.crossProduct;
import static org.lwjgl.util.freetype.FreeType.*;

public final class ImportFont {
    public static final double MSDFGEN_LEGACY_FONT_COORDINATE_SCALE = 1.0 / 64.0;

    private ImportFont() {
    }

    public static @Nullable FreetypeHandle initializeFreetype() {
        try (var stack = MemoryStack.stackPush()) {
            var libP = stack.mallocPointer(1);
            if (FT_Init_FreeType(libP) != FT_Err_Ok) return null;
            return new FreetypeHandle(libP.get(0));
        }
    }

    public static void deinitializeFreetype(FreetypeHandle library) {
        FT_Done_FreeType(library.library);
    }

    public static FontHandle adoptFreetypeFont(FT_Face ftFace) {
        return new FontHandle(ftFace, false);
    }

    @NativeType("FT_Error")
    public static int readFreetypeOutline(Shape output, FT_Outline outline, double scale) {
        output.contours.clear();
        output.setYAxisOrientation(YAxisOrientation.Y_UPWARD);
        var context = new FtContext(scale, output);

        try (var stack = MemoryStack.stackPush()) {
            var funcs = FT_Outline_Funcs.malloc(stack);
            funcs.move_to((to, _) -> ftMoveTo(to, context));
            funcs.line_to((to, _) -> ftLineTo(to, context));
            funcs.conic_to((control, to, _) -> ftConicTo(control, to, context));
            funcs.cubic_to(
                    (control1, control2, to, _) -> ftCubicTo(control1, control2, to, context)
            );
            funcs.shift(0);
            funcs.delta(0);

            var error = FT_Outline_Decompose(outline, funcs, MemoryUtil.NULL);
            if (!output.contours.isEmpty() && output.contours.getLast().edges.isEmpty())
                output.contours.removeLast();
            return error;
        }
    }

    public static @Nullable FontHandle loadFont(FreetypeHandle library, String filename) {
        try (var stack = MemoryStack.stackPush()) {
            var faceP = stack.mallocPointer(1);
            if (FT_New_Face(library.library, filename, 0, faceP) != FT_Err_Ok) return null;
            return new FontHandle(FT_Face.create(faceP.get(0)), true);
        }
    }

    public static @Nullable FontHandle loadFontData(FreetypeHandle library, ByteBuffer data) {
        try (var stack = MemoryStack.stackPush()) {
            var faceP = stack.mallocPointer(1);
            if (FT_New_Memory_Face(library.library, data, 0, faceP) != FT_Err_Ok) return null;
            return new FontHandle(FT_Face.create(faceP.get(0)), true);
        }
    }

    public static void destroyFont(FontHandle font) {
        if (font.ownership) FT_Done_Face(font.face);
    }

    public static FontMetrics getFontMetrics(
            FontHandle font, FontCoordinateScaling coordinateScaling
    ) {
        var face = font.face;
        var scale = getFontCoordinateScale(face, coordinateScaling);

        return new FontMetrics(scale * face.units_per_EM(),
                scale * face.ascender(),
                scale * face.descender(),
                scale * face.height(),
                scale * face.underline_position(),
                scale * face.underline_thickness()
        );
    }

    public static @Nullable WhitespaceWidth getFontWhitespaceWidth(FontHandle font, FontCoordinateScaling scaling) {
        var face = font.face;
        var scale = getFontCoordinateScale(face, scaling);

        if (FT_Load_Char(face, ' ', FT_LOAD_NO_SCALE) != FT_Err_Ok) return null;
        var spaceGlyph = face.glyph();
        assert spaceGlyph != null;
        @SuppressWarnings("resource")
        var spaceAdvance = scale * spaceGlyph.advance().x();

        if (FT_Load_Char(face, '\t', FT_LOAD_NO_SCALE) != FT_Err_Ok) return null;
        var tabGlyph = face.glyph();
        assert tabGlyph != null;
        @SuppressWarnings("resource")
        var tabAdvance = scale * tabGlyph.advance().x();

        return new WhitespaceWidth(spaceAdvance, tabAdvance);
    }

    @NativeType("FT_Long")
    public static long getGlyphCount(FontHandle font) {
        return font.face.num_glyphs();
    }

    public static GlyphIndex getGlyphIndex(FontHandle font, @NativeType("FT_ULong") long unicode) {
        return new GlyphIndex(FT_Get_Char_Index(font.face, unicode));
    }

    public static Optional<Double> loadGlyph(Shape output, FontHandle font, GlyphIndex glyphIndex,
                                             FontCoordinateScaling scaling) {
        var face = font.face;
        if (FT_Load_Glyph(face, glyphIndex.index(), FT_LOAD_NO_SCALE) != FT_Err_Ok) return Optional.empty();
        var scale = getFontCoordinateScale(face, scaling);
        var glyph = face.glyph();
        assert glyph != null;
        @SuppressWarnings("resource")
        var outAdvance = scale * glyph.advance().x();
        return readFreetypeOutline(output, glyph.outline(), scale) == FT_Err_Ok
                ? Optional.of(outAdvance)
                : Optional.empty();
    }

    public static Optional<Double> loadGlyph(Shape output, FontHandle font, @NativeType("FT_ULong") long unicode,
                                             FontCoordinateScaling coordinateScaling) {
        return loadGlyph(output, font, new GlyphIndex(FT_Get_Char_Index(font.face, unicode)), coordinateScaling);
    }

    public static Optional<Double> loadGlyph(Shape output, FontHandle font, GlyphIndex glyphIndex) {
        return loadGlyph(output, font, glyphIndex, FontCoordinateScaling.FONT_SCALING_LEGACY);
    }

    public static Optional<Double> loadGlyph(Shape output, FontHandle font, @NativeType("FT_ULong") long unicode) {
        return loadGlyph(output, font, unicode, FontCoordinateScaling.FONT_SCALING_LEGACY);
    }

    public static Optional<Double> getKerning(FontHandle font,
                                              GlyphIndex glyphIndex0,
                                              GlyphIndex glyphIndex1,
                                              FontCoordinateScaling scaling) {
        try (var stack = MemoryStack.stackPush()) {
            var kerning = FT_Vector.malloc(stack);
            if (FT_Get_Kerning(font.face, glyphIndex0.index(), glyphIndex1.index(), FT_KERNING_UNSCALED, kerning) != FT_Err_Ok)
                return Optional.empty();
            return Optional.of(getFontCoordinateScale(font.face, scaling) * kerning.x());
        }
    }

    public static Optional<Double> getKerning(FontHandle font,
                                              @NativeType("FT_ULong") long unicode0,
                                              @NativeType("FT_ULong") long unicode1,
                                              FontCoordinateScaling scaling) {
        return getKerning(font, getGlyphIndex(font, unicode0), getGlyphIndex(font, unicode1), scaling);
    }

    public static boolean setFontVariationAxis(FreetypeHandle library, FontHandle font,
                                               FontVariationAxis.Tag tag, double coordinate) {
        var success = false;
        var face = font.face;
        if ((face.face_flags() & FT_FACE_FLAG_MULTIPLE_MASTERS) != 0) {
            try (var stack = MemoryStack.stackPush()) {
                var masterP = stack.mallocPointer(1);
                if (FT_Get_MM_Var(face, masterP) != FT_Err_Ok) return false;
                var master = FT_MM_Var.create(masterP.get(0));
                var numAxis = master.num_axis();
                if (numAxis != 0) {
                    var coords = stack.mallocCLong(numAxis);
                    if (FT_Get_Var_Design_Coordinates(face, coords) == FT_Err_Ok) {
                        var ftTag = FT_MAKE_TAG(
                                tag.characters[0], tag.characters[1], tag.characters[2], tag.characters[3]
                        );
                        for (var i = 0; i < numAxis; i++) {
                            if (master.axis().get(i).tag() == ftTag) {
                                coords.put(i, doubleToF16Dot16(coordinate));
                                success = true;
                                break;
                            }
                        }
                    }
                    if (FT_Set_Var_Design_Coordinates(face, coords) != FT_Err_Ok) success = false;
                }
                FT_Done_MM_Var(library.library, master);
            }
        }
        return success;
    }

    public static boolean setFontVariationAxis(FreetypeHandle library, FontHandle font,
                                               String name, double coordinate) {
        var success = false;
        var face = font.face;
        if ((face.face_flags() & FT_FACE_FLAG_MULTIPLE_MASTERS) != 0) {
            try (var stack = MemoryStack.stackPush()) {
                var masterP = stack.mallocPointer(1);
                if (FT_Get_MM_Var(face, masterP) != FT_Err_Ok) return false;
                var master = FT_MM_Var.create(masterP.get(0));
                var numAxis = master.num_axis();
                if (numAxis != 0) {
                    var coords = stack.mallocCLong(numAxis);
                    if (FT_Get_Var_Design_Coordinates(face, coords) == FT_Err_Ok) {
                        for (var i = 0; i < numAxis; i++) {
                            if (name.equals(master.axis().get(i).nameString())) {
                                coords.put(i, doubleToF16Dot16(coordinate));
                                success = true;
                                break;
                            }
                        }
                    }
                    if (FT_Set_Var_Design_Coordinates(face, coords) != FT_Err_Ok) success = false;
                }
                FT_Done_MM_Var(library.library, master);
            }
        }
        return success;
    }

    public static boolean listFontVariationAxes(List<FontVariationAxis> axes, FreetypeHandle library,
                                                FontHandle font) {
        var face = font.face;
        if ((face.face_flags() & FT_FACE_FLAG_MULTIPLE_MASTERS) != 0) {
            try (var stack = MemoryStack.stackPush()) {
                var masterP = stack.mallocPointer(1);
                if (FT_Get_MM_Var(face, masterP) != FT_Err_Ok) return false;
                var master = FT_MM_Var.create(masterP.get(0));
                var numAxis = master.num_axis();
                axes.clear();
                for (var i = 0; i < numAxis; i++) {
                    var axis = master.axis().get(i);
                    var fa = new FontVariationAxis();
                    fa.tag = new FontVariationAxis.Tag(axis.tag());
                    fa.name = axis.nameString();
                    fa.minValue = f16Dot16ToDouble(axis.minimum());
                    fa.maxValue = f16Dot16ToDouble(axis.maximum());
                    fa.defaultValue = f16Dot16ToDouble(axis.def());
                    axes.add(fa);
                }
                FT_Done_MM_Var(library.library, master);
                return true;
            }
        }
        return false;
    }

    public static final class FreetypeHandle {
        private final long library;

        private FreetypeHandle(long library) {
            this.library = library;
        }
    }

    public static final class FontHandle {
        private final FT_Face face;
        private final boolean ownership;

        private FontHandle(FT_Face face, boolean ownership) {
            this.face = face;
            this.ownership = ownership;
        }
    }

    public record GlyphIndex(@NativeType("FT_UInt") int index) {
    }

    public record FontMetrics(double emSize,
                              double ascenderY,
                              double descenderY,
                              double lineHeight,
                              double underlineY,
                              double underlineThickness) {
    }

    public enum FontCoordinateScaling {
        FONT_SCALING_NONE,
        FONT_SCALING_EM_NORMALIZED,
        FONT_SCALING_LEGACY
    }

    public static class FontVariationAxis {
        public Tag tag = new Tag();
        public @Nullable String name;
        public double minValue;
        public double maxValue;
        public double defaultValue;

        public static final class Tag {
            public final char[] characters = new char[4];

            public Tag() {
            }

            public Tag(long freetypeTagValue) {
                characters[0] = (char) ((freetypeTagValue >>> 24) & 0xFF);
                characters[1] = (char) ((freetypeTagValue >>> 16) & 0xFF);
                characters[2] = (char) ((freetypeTagValue >>> 8) & 0xFF);
                characters[3] = (char) (freetypeTagValue & 0xFF);
            }

            public Tag(String stringValue) {
                for (var i = 0; i < 4 && i < stringValue.length(); i++)
                    characters[i] = stringValue.charAt(i);
            }
        }
    }

    public record WhitespaceWidth(double spaceAdvance, double tabAdvance) {
    }

    private static final class FtContext {
        private final double scale;
        private final Shape shape;

        private Vector2 position = new Vector2();
        private @Nullable Contour contour;

        private FtContext(double scale, Shape shape) {
            this.scale = scale;
            this.shape = shape;
        }
    }

    private static Vector2 ftPoint2(long vectorPtr, double scale) {
        return new Vector2(
                scale * FT_Vector.nx(vectorPtr),
                scale * FT_Vector.ny(vectorPtr)
        );
    }

    private static double f16Dot16ToDouble(long x) {
        return (1.0 / 65536.0) * x;
    }

    private static long doubleToF16Dot16(double x) {
        return (long) (65536.0 * x);
    }

    private static double getFontCoordinateScale(FT_Face face, FontCoordinateScaling scaling) {
        return switch (scaling) {
            case FONT_SCALING_NONE -> 1.0;
            case FONT_SCALING_EM_NORMALIZED -> 1.0 / (face.units_per_EM() != 0 ? face.units_per_EM() : 1);
            case FONT_SCALING_LEGACY -> MSDFGEN_LEGACY_FONT_COORDINATE_SCALE;
        };
    }

    private static int ftMoveTo(long to, FtContext context) {
        var pt = ftPoint2(to, context.scale);
        if (!(context.contour != null && context.contour.edges.isEmpty()))
            context.contour = context.shape.addContour();
        context.position = pt;
        return 0;
    }

    private static int ftLineTo(long to, FtContext context) {
        var endpoint = ftPoint2(to, context.scale);
        if (endpoint.x != context.position.x || endpoint.y != context.position.y) {
            assert context.contour != null;
            context.contour.addEdge(new EdgeHolder(context.position, endpoint));
            context.position = endpoint;
        }
        return 0;
    }

    private static int ftConicTo(long control, long to, FtContext context) {
        var endpoint = ftPoint2(to, context.scale);
        if (endpoint.x != context.position.x || endpoint.y != context.position.y) {
            assert context.contour != null;
            context.contour.addEdge(new EdgeHolder(context.position, ftPoint2(control, context.scale), endpoint));
            context.position = endpoint;
        }
        return 0;
    }

    private static int ftCubicTo(long control1, long control2, long to, FtContext context) {
        var endpoint = ftPoint2(to, context.scale);
        var c1 = ftPoint2(control1, context.scale);
        var c2 = ftPoint2(control2, context.scale);
        if (endpoint.x != context.position.x || endpoint.y != context.position.y ||
                crossProduct(new Vector2(c1.x - endpoint.x, c1.y - endpoint.y),
                        new Vector2(c2.x - endpoint.x, c2.y - endpoint.y)) != 0) {
            assert context.contour != null;
            context.contour.addEdge(new EdgeHolder(context.position, c1, c2, endpoint));
            context.position = endpoint;
        }
        return 0;
    }
}
