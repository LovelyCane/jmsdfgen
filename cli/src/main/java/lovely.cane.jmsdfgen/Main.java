package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Optional;

import static lovely.cane.jmsdfgen.Arithmetic.pixelFloatToByte;

public final class Main {
    private static final String DEFAULT_IMAGE_EXTENSION = "png";
    private static final double DEFAULT_ANGLE_THRESHOLD = 3.0;
    private static final int SDF_ERROR_ESTIMATE_PRECISION = 19;

    private Main() {
    }

    static void main(String[] args) {
        enum InputType {
            NONE,
            SVG,
            FONT,
            VAR_FONT,
            VAR_FONT_AXIS_PRINTOUT,
            DESCRIPTION_ARG,
            DESCRIPTION_STDIN,
            DESCRIPTION_FILE
        }

        var inputType = InputType.NONE;

        enum Mode {
            SINGLE,
            PERPENDICULAR,
            MULTI,
            MULTI_AND_TRUE,
            METRICS
        }

        var mode = Mode.MULTI;

        enum GeometryPreproc {
            NO_PREPROCESS,
            WINDING_PREPROCESS,
            FULL_PREPROCESS
        }

        var geometryPreproc = GeometryPreproc.NO_PREPROCESS;

        var legacyMode = false;

        var generatorConfig = new GeneratorConfig.MSDFGeneratorConfig();
        generatorConfig.overlapSupport = true;

        var scanlinePass = true;
        var fillRule = Scanline.FillRule.FILL_NONZERO;
        var format = Format.AUTO;

        var input = "";
        var output = "output." + DEFAULT_IMAGE_EXTENSION;
        var shapeExport = "";
        var svgExport = "";
        var testRender = "";
        var testRenderMulti = "";

        var outputSpecified = false;

        var glyphIndexSpecified = false;
        var glyphIndex = new ImportFont.GlyphIndex(0);
        var unicode = 0L;
        var fontCoordinateScaling = ImportFont.FontCoordinateScaling.FONT_SCALING_LEGACY;
        var fontCoordinateScalingSpecified = false;

        int width = 64, height = 64;
        int testWidth = 0, testHeight = 0;
        int testWidthM = 0, testHeightM = 0;
        var autoFrame = false;

        enum RangeMode {
            RANGE_UNIT,
            RANGE_PX
        }

        var rangeMode = RangeMode.RANGE_PX;

        var range = new Range(1);
        var pxRange = new Range(2);
        var translate = new Vector2();
        var pxTranslate = new Vector2();
        var scale = new Vector2(1);

        var scaleSpecified = false;
        var angleThreshold = DEFAULT_ANGLE_THRESHOLD;
        var outputDistanceShift = 0.f;
        var edgeAssignment = "";
        var yFlip = false;
        var printMetrics = false;
        var estimateError = false;
        var skipColoring = false;

        enum Winding {
            KEEP,
            REVERSE,
            GUESS
        }

        var winding = Winding.KEEP;

        var coloringSeed = 0L;

        interface EdgeColoringInterface {
            void edgeColoring(Shape shape, double angleThreshold, long seed);
        }

        EdgeColoringInterface edgeColoringInterface = EdgeColoring::edgeColoringSimple;

        var explicitErrorCorrectionMode = false;

        var suggestHelp = false;

        var argPos = 0;
        while (argPos < args.length) {
            var arg = args[argPos++];
            if (arg.startsWith("--")) {
                arg = arg.substring(1);
            }
            switch (arg) {
                case "sdf":
                    mode = Mode.SINGLE;
                    continue;
                case "psdf":
                    mode = Mode.PERPENDICULAR;
                    continue;
                case "msdf":
                    mode = Mode.MULTI;
                    continue;
                case "mtsdf":
                    mode = Mode.MULTI_AND_TRUE;
                    continue;
                case "metrics":
                    mode = Mode.METRICS;
                    continue;
                case "-svg":
                    if (args.length < argPos + 1) break;
                    inputType = InputType.SVG;
                    input = args[argPos++];
                    continue;
                case "-varfont", "-font":
                    if (args.length < argPos + 2) break;
                    inputType = arg.equals("-font") ? InputType.FONT : InputType.VAR_FONT;
                    input = args[argPos++];
                    var charArg = args[argPos++];
                    switch (charArg.charAt(0)) {
                        case 'p':
                            if (inputType == InputType.VAR_FONT && !charArg.equals("printvars"))
                                inputType = InputType.VAR_FONT_AXIS_PRINTOUT;
                            continue;
                        case 'G', 'g':
                            var gi = parseUnsignedDecOrHex(charArg.substring(1));
                            if (gi.isPresent()) {
                                glyphIndex = new ImportFont.GlyphIndex(gi.get());
                                glyphIndexSpecified = true;
                            }
                            continue;
                        case 'U', 'u':
                            charArg = charArg.substring(1);
                        default:
                            var parseUnicode = parseUnicode(charArg);
                            if (parseUnicode.isEmpty()) break;
                            unicode = parseUnicode.get();
                            continue;
                    }
                    break;
                case "-noemnormalize":
                    fontCoordinateScaling = ImportFont.FontCoordinateScaling.FONT_SCALING_NONE;
                    fontCoordinateScalingSpecified = true;
                    continue;
                case "-emnormalize":
                    fontCoordinateScaling = ImportFont.FontCoordinateScaling.FONT_SCALING_EM_NORMALIZED;
                    fontCoordinateScalingSpecified = true;
                    continue;
                case "-legacyfontscaling":
                    fontCoordinateScaling = ImportFont.FontCoordinateScaling.FONT_SCALING_LEGACY;
                    fontCoordinateScalingSpecified = true;
                    continue;
                case "-defineshape":
                    inputType = InputType.DESCRIPTION_ARG;
                    input = args[argPos++];
                    continue;
                case "-stdin":
                    inputType = InputType.DESCRIPTION_STDIN;
                    input = "stdin";
                    continue;
                case "-shapedesc":
                    inputType = InputType.DESCRIPTION_FILE;
                    input = args[argPos++];
                    continue;
                case "-o", "-out", "-output", "-imageout":
                    output = args[argPos++];
                    outputSpecified = true;
                    continue;
                case "-stdout":
                    output = "";
                    continue;
                case "-legacy":
                    legacyMode = true;
                    fontCoordinateScaling = ImportFont.FontCoordinateScaling.FONT_SCALING_LEGACY;
                    fontCoordinateScalingSpecified = true;
                    continue;
                case "-nopreprocess":
                    geometryPreproc = GeometryPreproc.NO_PREPROCESS;
                    continue;
                case "-windingpreprocess":
                    geometryPreproc = GeometryPreproc.WINDING_PREPROCESS;
                    continue;
                case "-preprocess":
                    geometryPreproc = GeometryPreproc.FULL_PREPROCESS;
                    continue;
                case "-nooverlap":
                    generatorConfig.overlapSupport = false;
                    continue;
                case "-overlap":
                    generatorConfig.overlapSupport = true;
                    continue;
                case "-noscanline":
                    scanlinePass = false;
                    continue;
                case "-scanline":
                    scanlinePass = true;
                    continue;
                case "-fillrule":
                    scanlinePass = true;
                    switch (args[argPos++]) {
                        case "nonzero":
                            fillRule = Scanline.FillRule.FILL_NONZERO;
                            continue;
                        case "evenodd", "odd":
                            fillRule = Scanline.FillRule.FILL_ODD;
                            continue;
                        case "positive":
                            fillRule = Scanline.FillRule.FILL_POSITIVE;
                            continue;
                        case "negative":
                            fillRule = Scanline.FillRule.FILL_NEGATIVE;
                            continue;
                        default:
                            System.err.println("Unknown fill rule specified.");
                            break;
                    }
                    break;
                case "-format":
                    switch (args[argPos++]) {
                        case "auto":
                            continue;
                        case "png":
                            format = Format.PNG;
                            if (!outputSpecified) output = "output.png";
                            continue;
                        case "bmp":
                            format = Format.BMP;
                            if (!outputSpecified) output = "output.bmp";
                            continue;
                        case "tiff", "tif":
                            format = Format.TIFF;
                            if (!outputSpecified) output = "output.tiff";
                            continue;
                        case "rgba":
                            format = Format.RGBA;
                            if (!outputSpecified) output = "output.rgba";
                            continue;
                        case "fl32":
                            format = Format.FL32;
                            if (!outputSpecified) output = "output.fl32";
                            continue;
                        case "text", "txt":
                            format = Format.TEXT;
                            if (!outputSpecified) output = "output.txt";
                            continue;
                        case "textfloat", "txtfloat":
                            format = Format.TEXT_FLOAT;
                            if (!outputSpecified) output = "output.txt";
                            continue;
                        case "bin":
                            format = Format.BINARY;
                            if (!outputSpecified) output = "output.bin";
                            continue;
                        case "binfloat", "binfloatle":
                            format = Format.BINARY_FLOAT;
                            if (!outputSpecified) output = "output.bin";
                            continue;
                        case "binfloatbe":
                            format = Format.BINARY_FLOAT_BE;
                            if (!outputSpecified) output = "output.bin";
                            continue;
                        default:
                            System.err.println("Unknown format specified.");
                            System.exit(1);
                            break;
                    }
                    break;
                case "-dimensions", "-size":
                    if (args.length < argPos + 2) break;
                    try {
                        width = Integer.parseUnsignedInt(args[argPos++], 10);
                        height = Integer.parseUnsignedInt(args[argPos++], 10);
                    } catch (NumberFormatException _) {
                        abort("Invalid dimensions. Use -dimensions <width> <height> with two positive integers.");
                    }
                    continue;
                case "-autoframe":
                    autoFrame = true;
                    continue;
                case "-range", "-unitrange":
                    if (args.length < argPos + 1) break;
                    try {
                        var r = Double.parseDouble(args[argPos++]);
                        if (r == 0.0) abort("Range must be non-zero.");
                        rangeMode = RangeMode.RANGE_UNIT;
                        range = new Range(r);
                    } catch (NumberFormatException _) {
                        abort("Invalid range argument. Use -range <range> with a real number.");
                    }
                    continue;
                case "-pxrange":
                    if (args.length < argPos + 1) break;
                    try {
                        var r = Double.parseDouble(args[argPos++]);
                        if (r == 0.0) abort("Range must be non-zero.");
                        rangeMode = RangeMode.RANGE_PX;
                        pxRange = new Range(r);
                    } catch (NumberFormatException _) {
                        abort("Invalid range argument. Use -pxrange <range> with a real number.");
                    }
                    continue;
                case "-arange", "-aunitrange":
                    if (args.length < argPos + 2) break;
                    try {
                        var r0 = Double.parseDouble(args[argPos++]);
                        var r1 = Double.parseDouble(args[argPos++]);
                        if (r0 == r1) abort("Range must be non-empty.");
                        rangeMode = RangeMode.RANGE_UNIT;
                        range = new Range(r0, r1);
                    } catch (NumberFormatException _) {
                        abort("Invalid range arguments. Use -arange <minimum> <maximum> with two real numbers.");
                    }
                    continue;
                case "-apxrange":
                    if (args.length < argPos + 2) break;
                    try {
                        var r0 = Double.parseDouble(args[argPos++]);
                        var r1 = Double.parseDouble(args[argPos++]);
                        if (r0 == r1) abort("Range must be non-empty.");
                        rangeMode = RangeMode.RANGE_PX;
                        pxRange = new Range(r0, r1);
                    } catch (NumberFormatException _) {
                        abort("Invalid range arguments. Use -apxrange <minimum> <maximum> with two real numbers.");
                    }
                    continue;
                case "-scale":
                    if (args.length < argPos + 1) break;
                    try {
                        scale = new Vector2(Double.parseDouble(args[argPos++]));
                        scaleSpecified = true;
                    } catch (NumberFormatException _) {
                        abort("Invalid scale argument. Use -scale <scale> with a positive real number.");
                    }
                    continue;
                case "-ascale":
                    if (args.length < argPos + 2) break;
                    try {
                        var sX = Double.parseDouble(args[argPos++]);
                        var sY = Double.parseDouble(args[argPos++]);
                        scale.set(sX, sY);
                        scaleSpecified = true;
                    } catch (NumberFormatException _) {
                        abort("Invalid scale argument. Use -scale <scale> with a positive real number.");
                    }
                    continue;
                case "-translate":
                    if (args.length < argPos + 2) break;
                    try {
                        var tx = Double.parseDouble(args[argPos++]);
                        var ty = Double.parseDouble(args[argPos++]);
                        translate.set(tx, ty);
                    } catch (NumberFormatException _) {
                        abort("Invalid translate arguments. Use -translate <x> <y> with two real numbers.");
                    }
                    continue;
                case "-pxtranslate":
                    if (args.length < argPos + 2) break;
                    try {
                        var tx = Double.parseDouble(args[argPos++]);
                        var ty = Double.parseDouble(args[argPos++]);
                        pxTranslate.set(tx, ty);
                    } catch (NumberFormatException _) {
                        abort("Invalid translate arguments. Use -pxtranslate <x> <y> with two real numbers.");
                    }
                    continue;
                case "-angle":
                    if (args.length < argPos + 1) break;
                    try {
                        var argAngle = args[argPos++];
                        double at;
                        if (argAngle.endsWith("d") || argAngle.endsWith("D")) {
                            at = Double.parseDouble(argAngle.substring(0, argAngle.length() - 1));
                            at *= Math.PI / 180.0;
                        } else {
                            at = Double.parseDouble(argAngle);
                        }
                        angleThreshold = at;
                    } catch (NumberFormatException _) {
                        abort("Invalid angle threshold. Use -angle <min angle> with a positive real number less than PI or a value in degrees followed by 'd' below 180d.");
                    }
                    continue;
                case "-errorcorrection":
                    if (args.length < argPos + 1) break;
                    var ecArg = args[argPos++];
                    switch (ecArg) {
                        case "disable", "disabled", "0", "none", "false":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.DISABLED;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
                            break;
                        case "default", "auto", "auto-mixed", "mixed":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.EDGE_PRIORITY;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.CHECK_DISTANCE_AT_EDGE;
                            break;
                        case "auto-fast", "fast":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.EDGE_PRIORITY;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
                            break;
                        case "auto-full", "full":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.EDGE_PRIORITY;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.ALWAYS_CHECK_DISTANCE;
                            break;
                        case "distance", "distance-fast", "indiscriminate", "indiscriminate-fast":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.INDISCRIMINATE;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
                            break;
                        case "distance-full", "indiscriminate-full":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.INDISCRIMINATE;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.ALWAYS_CHECK_DISTANCE;
                            break;
                        case "edge-fast":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.EDGE_ONLY;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
                            break;
                        case "edge", "edge-full":
                            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.EDGE_ONLY;
                            generatorConfig.errorCorrection.distanceCheckMode = GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.ALWAYS_CHECK_DISTANCE;
                            break;
                        case "help":
                            IO.println("""
                                    
                                    ERROR CORRECTION MODES
                                      auto-fast
                                        \tDetects inversion artifacts and distance errors that do not affect edges by range testing.
                                      auto-full
                                        \tDetects inversion artifacts and distance errors that do not affect edges by exact distance evaluation.
                                      auto-mixed (default)
                                        \tDetects inversions by distance evaluation and distance errors that do not affect edges by range testing.
                                      disabled
                                        \tDisables error correction.
                                      distance-fast
                                        \tDetects distance errors by range testing. Does not care if edges and corners are affected.
                                      distance-full
                                        \tDetects distance errors by exact distance evaluation. Does not care if edges and corners are affected, slow.
                                      edge-fast
                                        \tDetects inversion artifacts only by range testing.
                                      edge-full
                                        \tDetects inversion artifacts only by exact distance evaluation.
                                      help
                                        \tDisplays this help.
                                    """);
                            System.exit(0);
                        default:
                            System.err.println("Unknown error correction mode. Use -errorcorrection help for more information.");
                            break;
                    }
                    explicitErrorCorrectionMode = true;
                    continue;
                case "-errordeviationratio":
                    if (args.length < argPos + 1) break;
                    try {
                        var edr = Double.parseDouble(args[argPos++]);
                        if (edr <= 0) throw new NumberFormatException();
                        generatorConfig.errorCorrection.minDeviationRatio = edr;
                    } catch (NumberFormatException _) {
                        abort("Invalid error deviation ratio. Use -errordeviationratio <ratio> with a positive real number.");
                    }
                    continue;
                case "-errorimproveratio":
                    if (args.length < argPos + 1) break;
                    try {
                        var eir = Double.parseDouble(args[argPos++]);
                        if (eir <= 0) throw new NumberFormatException();
                        generatorConfig.errorCorrection.minImproveRatio = eir;
                    } catch (NumberFormatException _) {
                        abort("Invalid error improvement ratio. Use -errorimproveratio <ratio> with a positive real number.");
                    }
                    continue;
                case "-coloringstrategy", "-edgecoloring":
                    if (args.length < argPos + 1) break;
                    switch (args[argPos++]) {
                        case "simple":
                            edgeColoringInterface = EdgeColoring::edgeColoringSimple;
                            break;
                        case "inktrap":
                            edgeColoringInterface = EdgeColoring::edgeColoringInkTrap;
                            break;
                        case "distance":
                            edgeColoringInterface = EdgeColoring::edgeColoringByDistance;
                            break;
                        default:
                            System.err.println("Unknown coloring strategy specified.");
                            break;
                    }
                    continue;
                case "-edgecolors":
                    if (args.length < argPos + 1) break;
                    var colors = args[argPos++];
                    for (var i = 0; i < colors.length(); ++i) {
                        var c = colors.charAt(i);
                        if (" ?,cmwyCMWY".indexOf(c) == -1) {
                            abort("Invalid edge coloring sequence. Use -edgecolors <color sequence> with only the colors C, M, Y, and W. Separate contours by commas and use ? to keep the default assignment for a contour.");
                        }
                    }
                    edgeAssignment = colors;
                    continue;
                case "-distanceshift":
                    if (args.length < argPos + 1) break;
                    try {
                        outputDistanceShift = Float.parseFloat(args[argPos++]);
                    } catch (NumberFormatException _) {
                        abort("Invalid distance shift. Use -distanceshift <shift> with a real value.");
                    }
                    continue;
                case "-exportshape":
                    if (args.length < argPos + 1) break;
                    shapeExport = args[argPos++];
                    continue;
                case "-exportsvg":
                    if (args.length < argPos + 1) break;
                    svgExport = args[argPos++];
                    continue;
                case "-testrender":
                    if (args.length < argPos + 3) break;
                    testRender = args[argPos++];
                    try {
                        testWidth = Integer.parseInt(args[argPos++]);
                        testHeight = Integer.parseInt(args[argPos++]);
                        if (testWidth <= 0 || testHeight <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException _) {
                        abort("Invalid arguments for test render. Use -testrender <output." + DEFAULT_IMAGE_EXTENSION + "> <width> <height>.");
                    }
                    continue;
                case "-testrendermulti":
                    if (args.length < argPos + 3) break;
                    testRenderMulti = args[argPos++];
                    try {
                        testWidthM = Integer.parseInt(args[argPos++]);
                        testHeightM = Integer.parseInt(args[argPos++]);
                        if (testWidthM <= 0 || testHeightM <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException _) {
                        abort("Invalid arguments for test render. Use -testrendermulti <output." + DEFAULT_IMAGE_EXTENSION + "> <width> <height>.");
                    }
                    continue;
                case "-yflip":
                    yFlip = true;
                    continue;
                case "-printmetrics":
                    printMetrics = true;
                    continue;
                case "-estimateerror":
                    estimateError = true;
                    continue;
                case "-keepwinding", "-keeporder":
                    winding = Winding.KEEP;
                    continue;
                case "-reversewinding", "-reverseorder":
                    winding = Winding.REVERSE;
                    continue;
                case "-guesswinding", "-guessorder":
                    winding = Winding.GUESS;
                    continue;
                case "-seed":
                    if (args.length < argPos + 1) break;
                    try {
                        coloringSeed = Long.parseUnsignedLong(args[argPos++]);
                    } catch (NumberFormatException _) {
                        abort("Invalid seed. Use -seed <N> with N being a non-negative integer.");
                    }
                    continue;
                case "-version":
                    IO.println("""
                            MSDFgen v1.13.0
                            (c) 2014 - 2026 Viktor Chlumsky
                            """);
                    System.exit(0);
                case "-help":
                    IO.println("""
                            
                            Multi-channel signed distance field generator by Viktor Chlumsky v1.13.0
                            ------------------------------------------------------------------------
                              Usage: msdfgen <mode> <input specification> <options>
                            
                            MODES
                              sdf - Generate conventional monochrome (true) signed distance field.
                              psdf - Generate monochrome signed perpendicular distance field.
                              msdf - Generate multi-channel signed distance field. This is used by default if no mode is specified.
                              mtsdf - Generate combined multi-channel and true signed distance field in the alpha channel.
                              metrics - Report shape metrics only.
                            
                            INPUT SPECIFICATION
                              -defineshape <definition>
                                    Defines input shape using the ad-hoc text definition.
                              -font <filename.ttf> <character code>
                                    Loads a single glyph from the specified font file.
                                    Format of character code is '?', 63, 0x3F (Unicode value), or g34 (glyph index).
                              -shapedesc <filename.txt>
                                    Loads text shape description from a file.
                              -stdin
                                    Reads text shape description from the standard input.
                              -svg <filename.svg>
                                    Loads the last vector path found in the specified SVG file.
                              -varfont <filename and variables> <character code>
                                    Loads a single glyph from a variable font. Specify axis values as x.ttf?var1=0.5&var2=1
                                    To print the available variation axes, use -varfont <filename> printvars
                            
                            OPTIONS
                              -angle <angle>
                                    Specifies the minimum angle between adjacent edges to be considered a corner. Append D for degrees.
                              -apxrange <outermost distance> <innermost distance>
                                    Specifies the outermost (negative) and innermost representable distance in pixels.
                              -arange <outermost distance> <innermost distance>
                                    Specifies the outermost (negative) and innermost representable distance in shape units.
                              -ascale <x scale> <y scale>
                                    Sets the scale used to convert shape units to pixels asymmetrically.
                              -autoframe
                                    Automatically scales (unless specified) and translates the shape to fit.
                              -coloringstrategy <simple / inktrap / distance>
                                    Selects the strategy of the edge coloring heuristic.
                              -dimensions <width> <height>
                                    Sets the dimensions of the output image.
                              -edgecolors <sequence>
                                    Overrides automatic edge coloring with the specified color sequence.
                              -emnormalize
                                    Before applying scale, normalizes font glyph coordinates so that 1 = 1 em.
                              -errorcorrection <mode>
                                    Changes the MSDF/MTSDF error correction mode. Use -errorcorrection help for a list of valid modes.
                              -errordeviationratio <ratio>
                                    Sets the minimum ratio between the actual and maximum expected distance delta to be considered an error.
                              -errorimproveratio <ratio>
                                    Sets the minimum ratio between the pre-correction distance error and the post-correction distance error.
                              -estimateerror
                                    Computes and prints the distance field's estimated fill error to the standard output.
                              -exportshape <filename.txt>
                                    Saves the shape description into a text file that can be edited and loaded using -shapedesc.
                              -exportsvg <filename.svg>
                                    Saves the shape geometry into a simple SVG file.
                              -fillrule <nonzero / evenodd / positive / negative>
                                    Sets the fill rule for the scanline pass. Default is nonzero.
                              -format <png / bmp / tiff / rgba / fl32 / text / textfloat / bin / binfloat / binfloatbe>
                                    Specifies the output format of the distance field. Otherwise it is chosen based on output file extension.
                              -guesswinding
                                    Attempts to detect if shape contours have the wrong winding and generates the SDF with the right one.
                              -help
                                    Displays this help.
                              -legacy
                                    Uses the original (legacy) distance field algorithms.
                              -noemnormalize
                                    Raw integer font glyph coordinates will be used. Without this option, legacy scaling will be applied.
                              -nooverlap
                                    Disables resolution of overlapping contours.
                              -noscanline
                                    Disables the scanline pass, which corrects the distance field's signs according to the selected fill rule.
                              -o <filename>
                                    Sets the output file name. The default value is "output.png".
                              -printmetrics
                                    Prints relevant metrics of the shape to the standard output.
                              -pxrange <range>
                                    Sets the width of the range between the lowest and highest signed distance in pixels.
                              -pxtranslate <x> <y>
                                    Sets the translation of the shape in output pixels.
                              -range <range>
                                    Sets the width of the range between the lowest and highest signed distance in shape units.
                              -reversewinding
                                    Generates the distance field as if the shape's vertices were in reverse order.
                              -scale <scale>
                                    Sets the scale used to convert shape units to pixels.
                              -seed <n>
                                    Sets the random seed for edge coloring heuristic.
                              -stdout
                                    Prints the output instead of storing it in a file. Only text formats are supported.
                              -testrender <filename.png> <width> <height>
                                    Renders an image preview using the generated distance field and saves it as a PNG file.
                              -testrendermulti <filename.png> <width> <height>
                                    Renders an image preview without resolving the color channels.
                              -translate <x> <y>
                                    Sets the translation of the shape in shape units.
                              -version
                                    Prints the version of the program.
                              -windingpreprocess
                                    Attempts to fix only the contour windings assuming no self-intersections and even-odd fill rule.
                              -yflip
                                    Inverts the Y-axis in the output distance field. The default orientation is upward.
                            
                            """);
                    System.exit(0);
                default:
                    break;
            }
            System.err.println("Unknown setting or insufficient parameters: " + arg);
            suggestHelp = true;
        }
        if (suggestHelp) System.err.println("Use -help for more information.");

        var svgViewBox = new Shape.Bounds();
        var glyphAdvance = 0.0;
        if (inputType == InputType.NONE || input.isEmpty()) {
            abort("No input specified! Use either -svg <file.svg> or -font <file.ttf/otf> <character code>, or see -help.");
        }
        var colorsSpecified = new boolean[1];
        var shape = new Shape();
        switch (inputType) {
            case SVG:
                var svgImportFlags = ImportSvg.loadSvgShape(shape, svgViewBox, input);
                if ((svgImportFlags & ImportSvg.SVG_IMPORT_SUCCESS_FLAG) == 0) {
                    abort("Failed to load shape from SVG file.");
                }
                if ((svgImportFlags & ImportSvg.SVG_IMPORT_PARTIAL_FAILURE_FLAG) != 0) {
                    System.err.println("Warning: Failed to load part of SVG file.");
                }
                if ((svgImportFlags & ImportSvg.SVG_IMPORT_INCOMPLETE_FLAG) != 0) {
                    System.err.println("Warning: SVG file contains multiple paths or shapes but this version is only able to load one.");
                } else if ((svgImportFlags & ImportSvg.SVG_IMPORT_UNSUPPORTED_FEATURE_FLAG) != 0) {
                    System.err.println("Warning: SVG file likely contains elements that are unsupported.");
                }
                if ((svgImportFlags & ImportSvg.SVG_IMPORT_TRANSFORMATION_IGNORED_FLAG) != 0) {
                    System.err.println("Warning: SVG path transformation ignored.\n");
                }
                break;
            case FONT, VAR_FONT, VAR_FONT_AXIS_PRINTOUT:
                if (inputType != InputType.VAR_FONT_AXIS_PRINTOUT && !glyphIndexSpecified && unicode == 0L) {
                    abort("No character specified! Use -font <file.ttf/otf> <character code>. Character code can be a Unicode index (65, 0x41), a character in apostrophes ('A'), or a glyph index prefixed by g (g36, g0x24).");
                }
                class FreetypeFontGuard {
                    ImportFont.@Nullable FreetypeHandle ft;
                    ImportFont.@Nullable FontHandle font;

                    void free() {
                        if (ft != null) {
                            if (font != null) ImportFont.destroyFont(font);
                            ImportFont.deinitializeFreetype(ft);
                        }
                    }
                }

                var guard = new FreetypeFontGuard();
                guard.ft = ImportFont.initializeFreetype();
                if (guard.ft == null) {
                    abort("Failed to initialize FreeType library.");
                } else {
                    if (inputType == InputType.VAR_FONT || inputType == InputType.VAR_FONT_AXIS_PRINTOUT) {
                        var badAxesSpecified = new boolean[]{false};
                        guard.font = loadVarFont(guard.ft, input, badAxesSpecified);
                        if (guard.font != null) {
                            if (inputType == InputType.VAR_FONT_AXIS_PRINTOUT) {
                                printVarFontAxisList(System.out, guard.ft, guard.font);
                                System.exit(0);
                            } else if (badAxesSpecified[0]) {
                                printVarFontAxisList(System.err, guard.ft, guard.font);
                            }
                        }
                    }
                    guard.font = ImportFont.loadFont(guard.ft, input);
                    if (guard.font == null) abort("Failed to load font file.");
                    if (unicode != 0L) glyphIndex = ImportFont.getGlyphIndex(guard.font, unicode);
                    var glyph = ImportFont.loadGlyph(shape, guard.font, glyphIndex, fontCoordinateScaling);
                    if (glyph.isEmpty()) abort("Failed to load glyph from font file.");
                    else glyphAdvance = glyph.get();

                    guard.free();
                    if (!fontCoordinateScalingSpecified && (!autoFrame || scaleSpecified || rangeMode == RangeMode.RANGE_UNIT || mode == Mode.METRICS || printMetrics || !shapeExport.isEmpty() || !svgExport.isEmpty())) {
                        System.err.print("""
                                Warning: Using legacy font coordinate conversion for compatibility reasons.
                                         The implicit scaling behavior will likely change in a future version resulting in different output.
                                         To silence this warning, use one of the following options:
                                           -noemnormalize to switch to the correct native font coordinates,
                                           -emnormalize to switch to coordinates normalized to 1 em, or
                                           -legacyfontscaling to keep current behavior and make sure it will not change.
                                """);
                    }
                }
                break;
            case DESCRIPTION_ARG:
                if (!ShapeDescription.readShapeDescription(input, shape, colorsSpecified)) {
                    abort("Parse error in shape description.");
                }
                skipColoring = colorsSpecified[0];
                break;
            case DESCRIPTION_STDIN: {
                try {
                    if (!ShapeDescription.readShapeDescription(System.in, shape, colorsSpecified)) {
                        abort("Parse error in shape description.");
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Parse error in shape description.", e);
                }
                skipColoring = colorsSpecified[0];
                break;
            }
            case DESCRIPTION_FILE: {
                try {
                    if (!ShapeDescription.readShapeDescription(new File(input), shape, colorsSpecified)) {
                        abort("Parse error in shape description.");
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Parse error in shape description.", e);
                }
                break;
            }
        }

        if (!shape.validate()) abort("The geometry of the loaded shape is invalid.");
        switch (geometryPreproc) {
            case NO_PREPROCESS:
                break;
            case WINDING_PREPROCESS:
                shape.orientContours();
                break;
            case FULL_PREPROCESS:
                abort("Shape geometry preprocessing (-preprocess) is not available in this version because the Skia library is not present.");
                break;
        }
        shape.normalize();
        if (yFlip) shape.inverseYAxis = !shape.inverseYAxis;
        translate.add(Vector2.divide(pxTranslate, scale));
        var avgScale = 0.5 * (scale.x + scale.y);
        var bounds = new Shape.Bounds();
        if (autoFrame || mode == Mode.METRICS || printMetrics || winding == Winding.GUESS || !svgExport.isEmpty()) {
            bounds = shape.getBounds();
        }
        if (winding == Winding.GUESS) {
            var p = new Vector2(bounds.l - (bounds.r - bounds.l) - 1, bounds.b - (bounds.t - bounds.b) - 1);
            var distance = ShapeDistanceFinder.oneShotDistance(
                    shape, p,
                    new ContourCombiner.SimpleContourCombiner<>(new EdgeSelector.TrueDistanceSelector()),
                    EdgeSelector.TrueDistanceSelector.EdgeCache::new
            );
            winding = distance <= 0 ? Winding.KEEP : Winding.REVERSE;
        }
        if (winding == Winding.REVERSE) {
            for (var contour : shape.contours) {
                contour.reverse();
            }
        }
        if (outputDistanceShift != 0) {
            var rangeRef = rangeMode == RangeMode.RANGE_PX ? pxRange : range;
            var rangeShift = -outputDistanceShift * (rangeRef.upper - rangeRef.lower);
            rangeRef.lower += rangeShift;
            rangeRef.upper += rangeShift;
        }
        if (autoFrame) {
            double l = bounds.l, b = bounds.b, r = bounds.r, t = bounds.t;
            var frame = new Vector2(width, height);
            if (!scaleSpecified) {
                if (rangeMode == RangeMode.RANGE_UNIT) {
                    l += range.lower;
                    b += range.lower;
                    r -= range.lower;
                    t -= range.lower;
                } else frame.add(new Vector2(2 * pxRange.lower));
            }
            if (l >= r || b >= t) {
                l = 0;
                b = 0;
                r = 1;
                t = 1;
            }
            if (frame.x <= 0 || frame.y <= 0) abort("Cannot fit the specified pixel range.");
            var dims = new Vector2(r - l, t - b);
            if (scaleSpecified) {
                translate = Vector2.subtract(
                        Vector2.multiply(
                                0.5,
                                Vector2.subtract(
                                        Vector2.divide(frame, scale),
                                        dims
                                )
                        ),
                        new Vector2(l, b)
                );
            } else {
                if (dims.x * frame.y < dims.y * frame.x) {
                    translate.set(0.5 * (frame.x / frame.y * dims.y - dims.x) - l, -b);
                    avgScale = frame.y / dims.y;
                } else {
                    translate.set(-l, 0.5 * (frame.y / frame.x * dims.x - dims.y) - b);
                    avgScale = frame.x / dims.x;
                }
                scale = new Vector2(avgScale);
            }
            if (rangeMode == RangeMode.RANGE_PX && !scaleSpecified) {
                translate.subtract(Vector2.divide(pxRange.lower, scale));
            }
        }
        if (rangeMode == RangeMode.RANGE_PX) {
            range = pxRange.divide(Math.min(scale.y, scale.x));
        }
        if (mode == Mode.METRICS || printMetrics) {
            var out = System.out;
            if (mode == Mode.METRICS && outputSpecified) {
                try {
                    out = new PrintStream(new FileOutputStream(output));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException("Failed to write output file.", e);
                }
            }
            switch (shape.getYAxisOrientation()) {
                case Y_UPWARD:
                    out.println("Y-axis upward");
                    break;
                case Y_DOWNWARD:
                    out.println("Y-axis downward");
                    break;
            }
            if (svgViewBox.l < svgViewBox.r && svgViewBox.b < svgViewBox.t)
                out.printf("view box = %.17g, %.17g, %.17g, %.17g\n", svgViewBox.l, svgViewBox.b, svgViewBox.r, svgViewBox.t);
            if (bounds.l < bounds.r && bounds.b < bounds.t)
                out.printf("bounds = %.17g, %.17g, %.17g, %.17g\n", bounds.l, bounds.b, bounds.r, bounds.t);
            if (glyphAdvance != 0)
                out.printf("advance = %.17g\n", glyphAdvance);
            if (autoFrame) {
                if (!scaleSpecified)
                    out.printf("scale = %.17g\n", avgScale);
                out.printf("translate = %.17g, %.17g\n", translate.x, translate.y);
            }
            if (rangeMode == RangeMode.RANGE_PX)
                out.printf("range %.17g to %.17g\n", range.lower, range.upper);
            if (mode == Mode.METRICS && outputSpecified)
                out.close();
        }
        var transformation = new SDFTransformation(new Projection(scale, translate), range);
        Bitmap<Float> bitmap = null;
        var ecc = new GeneratorConfig.ErrorCorrectionConfig();
        ecc.mode = generatorConfig.errorCorrection.mode;
        ecc.distanceCheckMode = generatorConfig.errorCorrection.distanceCheckMode;
        ecc.minDeviationRatio = generatorConfig.errorCorrection.minDeviationRatio;
        ecc.minImproveRatio = generatorConfig.errorCorrection.minImproveRatio;
        var postErrorCorrectionConfig = new GeneratorConfig.MSDFGeneratorConfig(
                generatorConfig.overlapSupport, ecc
        );
        if (scanlinePass) {
            if (explicitErrorCorrectionMode
                    && generatorConfig.errorCorrection.distanceCheckMode
                    != GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE) {
                var fallbackModeName = switch (generatorConfig.errorCorrection.mode) {
                    case DISABLED -> "disabled";
                    case INDISCRIMINATE -> "distance-fast";
                    case EDGE_PRIORITY -> "auto-fast";
                    case EDGE_ONLY -> "edge-fast";
                };
                System.err.printf(
                        "Selected error correction mode not compatible with scanline pass, falling back to %s.\n",
                        fallbackModeName
                );
            }
            generatorConfig.errorCorrection.mode = GeneratorConfig.ErrorCorrectionConfig.Mode.DISABLED;
            postErrorCorrectionConfig.errorCorrection.distanceCheckMode =
                    GeneratorConfig.ErrorCorrectionConfig.DistanceCheckMode.DO_NOT_CHECK_DISTANCE;
        }
        switch (mode) {
            case SINGLE: {
                bitmap = new Bitmap<>(width, height, 1, Float[]::new);
                if (legacyMode) MSDFGen.generateSDF_legacy(bitmap.toBitmapSection(), shape, range, scale, translate);
                else MSDFGen.generateSDF(bitmap.toBitmapSection(), shape, transformation, generatorConfig);
                break;
            }
            case PERPENDICULAR: {
                bitmap = new Bitmap<>(width, height, 1, Float[]::new);
                if (legacyMode) MSDFGen.generatePSDF_legacy(bitmap.toBitmapSection(), shape, range, scale, translate);
                else MSDFGen.generatePSDF(bitmap.toBitmapSection(), shape, transformation, generatorConfig);
                break;
            }
            case MULTI: {
                if (!skipColoring) edgeColoringInterface.edgeColoring(shape, angleThreshold, coloringSeed);
                if (!edgeAssignment.isEmpty()) parseColoring(shape, edgeAssignment);
                bitmap = new Bitmap<>(width, height, 3, Float[]::new);
                if (legacyMode) {
                    MSDFGen.generateMSDF_legacy(
                            bitmap.toBitmapSection(), shape, range, scale, translate, generatorConfig.errorCorrection
                    );
                } else MSDFGen.generateMSDF(bitmap.toBitmapSection(), shape, transformation, generatorConfig);
                break;
            }
            case MULTI_AND_TRUE: {
                if (!skipColoring) edgeColoringInterface.edgeColoring(shape, angleThreshold, coloringSeed);
                if (!edgeAssignment.isEmpty()) parseColoring(shape, edgeAssignment);
                bitmap = new Bitmap<>(width, height, 4, Float[]::new);
                if (legacyMode) {
                    MSDFGen.generateMTSDF_legacy(
                            bitmap.toBitmapSection(), shape, range, scale, translate, generatorConfig.errorCorrection
                    );
                } else MSDFGen.generateMTSDF(bitmap.toBitmapSection(), shape, transformation, generatorConfig);
                break;
            }
        }
        if (scanlinePass) {
            var sdfZeroValue = (float) (range.lower != range.upper ? range.lower / (range.lower - range.upper) : 0.5);
            switch (mode) {
                case SINGLE:
                case PERPENDICULAR:
                    Rasterization.distanceSignCorrection(
                            bitmap.toBitmapSection(), shape, transformation, sdfZeroValue, fillRule
                    );
                    break;
                case MULTI, MULTI_AND_TRUE:
                    Rasterization.distanceSignCorrection(
                            bitmap.toBitmapSection(), shape, transformation, sdfZeroValue, fillRule
                    );
                    MSDFErrorCorrection.msdfErrorCorrection(
                            bitmap.toBitmapSection(), shape, transformation, postErrorCorrectionConfig
                    );
                    break;
            }
        }

        if (!shapeExport.isEmpty()) {
            try (var fw = new FileWriter(shapeExport)) {
                ShapeDescription.writeShapeDescription(fw, shape);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write shape export file.", e);
            }
        }

        if (!svgExport.isEmpty()) {
            if (!ExportSvg.saveSvgShape(shape, bounds, svgExport)) {
                System.err.println("Failed to write shape SVG file.");
            }
        }

        if (bitmap != null) {
            var error = writeOutput(bitmap, output, format);
            if (error != null) {
                System.err.println(error);
                System.exit(1);
            }
        }
        var shouldSimulate = is8bitFormat(format) && (!testRenderMulti.isEmpty() || !testRender.isEmpty() || estimateError);
        switch (mode) {
            case SINGLE, PERPENDICULAR:
                if (shouldSimulate) {
                    RenderSDF.simulate8bit1(bitmap.toBitmapSection());
                }
                if (estimateError) {
                    var sdfError = SdfErrorEstimation.estimateSDFError(
                            bitmap.toBitmapConstSection(),
                            shape, transformation, SDF_ERROR_ESTIMATE_PRECISION, fillRule
                    );
                    System.out.printf("SDF error ~ %e\n", sdfError);
                }
                if (!testRenderMulti.isEmpty()) {
                    var render = new Bitmap<>(testWidthM, testHeightM, 3, Float[]::new);
                    RenderSDF.renderSDF3_1(
                            render.toBitmapSection(), bitmap.toBitmapConstSection(), Range.multiply(avgScale, range)
                    );
                    if (!cmpExtension(testRenderMulti, "." + DEFAULT_IMAGE_EXTENSION)) {
                        System.err.println(
                                "Warning: -testrendermulti specified with an extension other than ."
                                        + DEFAULT_IMAGE_EXTENSION +
                                        " but will be saved in that format anyway."
                        );
                    }
                    if (!SavePng.savePngFloat(render.toBitmapConstSection(), testRenderMulti))
                        System.err.println("Failed to write test render file.");
                }
                if (!testRender.isEmpty()) {
                    var render = new Bitmap<>(testWidth, testHeight, 1, Float[]::new);
                    RenderSDF.renderSDF1_1(render.toBitmapSection(), bitmap.toBitmapConstSection(), Range.multiply(avgScale, range));
                    if (!cmpExtension(testRender, "." + DEFAULT_IMAGE_EXTENSION))
                        System.err.println("Warning: -testrender specified with an extension other than ." + DEFAULT_IMAGE_EXTENSION + " but will be saved in that format anyway.");
                    if (!SavePng.savePngFloat(render.toBitmapConstSection(), testRender))
                        System.err.println("Failed to write test render file.");
                }
                break;
            case MULTI:
                if (shouldSimulate) {
                    RenderSDF.simulate8bit3(bitmap.toBitmapSection());
                }
                if (estimateError) {
                    var sdfError = SdfErrorEstimation.estimateSDFError(
                            bitmap.toBitmapConstSection(),
                            shape, transformation, SDF_ERROR_ESTIMATE_PRECISION, fillRule
                    );
                    System.out.printf("SDF error ~ %e\n", sdfError);
                }
                if (!testRenderMulti.isEmpty()) {
                    var render = new Bitmap<>(testWidthM, testHeightM, 3, Float[]::new);
                    RenderSDF.renderSDF3_3(
                            render.toBitmapSection(), bitmap.toBitmapConstSection(), Range.multiply(avgScale, range)
                    );
                    if (!cmpExtension(testRenderMulti, "." + DEFAULT_IMAGE_EXTENSION)) {
                        System.err.println(
                                "Warning: -testrendermulti specified with an extension other than ."
                                        + DEFAULT_IMAGE_EXTENSION +
                                        " but will be saved in that format anyway."
                        );
                    }
                    if (!SavePng.savePngFloat(render.toBitmapConstSection(), testRenderMulti))
                        System.err.println("Failed to write test render file.");
                }
                if (!testRender.isEmpty()) {
                    var render = new Bitmap<>(testWidth, testHeight, 1, Float[]::new);
                    RenderSDF.renderSDF1_3(render.toBitmapSection(), bitmap.toBitmapConstSection(), Range.multiply(avgScale, range));
                    if (!cmpExtension(testRender, "." + DEFAULT_IMAGE_EXTENSION))
                        System.err.println("Warning: -testrender specified with an extension other than ." + DEFAULT_IMAGE_EXTENSION + " but will be saved in that format anyway.");
                    if (!SavePng.savePngFloat(render.toBitmapConstSection(), testRender))
                        System.err.println("Failed to write test render file.");
                }
                break;
            case MULTI_AND_TRUE:
                if (shouldSimulate) {
                    RenderSDF.simulate8bit4(bitmap.toBitmapSection());
                }
                if (estimateError) {
                    var sdfError = SdfErrorEstimation.estimateSDFError(
                            bitmap.toBitmapConstSection(),
                            shape, transformation, SDF_ERROR_ESTIMATE_PRECISION, fillRule
                    );
                    System.out.printf("SDF error ~ %e\n", sdfError);
                }
                if (!testRenderMulti.isEmpty()) {
                    var render = new Bitmap<>(testWidthM, testHeightM, 4, Float[]::new);
                    RenderSDF.renderSDF4_4(
                            render.toBitmapSection(), bitmap.toBitmapConstSection(), Range.multiply(avgScale, range)
                    );
                    if (!cmpExtension(testRenderMulti, "." + DEFAULT_IMAGE_EXTENSION)) {
                        System.err.println(
                                "Warning: -testrendermulti specified with an extension other than ."
                                        + DEFAULT_IMAGE_EXTENSION +
                                        " but will be saved in that format anyway."
                        );
                    }
                    if (!SavePng.savePngFloat(render.toBitmapConstSection(), testRenderMulti))
                        System.err.println("Failed to write test render file.");
                }
                if (!testRender.isEmpty()) {
                    var render = new Bitmap<>(testWidth, testHeight, 1, Float[]::new);
                    RenderSDF.renderSDF1_4(render.toBitmapSection(), bitmap.toBitmapConstSection(), Range.multiply(avgScale, range));
                    if (!cmpExtension(testRender, "." + DEFAULT_IMAGE_EXTENSION))
                        System.err.println("Warning: -testrender specified with an extension other than ." + DEFAULT_IMAGE_EXTENSION + " but will be saved in that format anyway.");
                    if (!SavePng.savePngFloat(render.toBitmapConstSection(), testRender))
                        System.err.println("Failed to write test render file.");
                }
                break;
        }
    }

    private static Optional<Integer> parseUnsignedDecOrHex(String arg) {
        if (arg.isEmpty()) return Optional.empty();

        var radix = 10;
        var numStr = arg;

        if (arg.startsWith("0x") || arg.startsWith("0X")) {
            radix = 16;
            numStr = arg.substring(2);
        }

        if (numStr.isEmpty()) return Optional.empty();

        try {
            return Optional.of(Integer.parseUnsignedInt(numStr, radix));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static void parseColoring(Shape shape, String edgeAssignment) {
        int c = 0, e = 0;
        var contours = shape.contours;
        var contour = contours.get(c);
        var change = false;
        var clear = true;
        for (var i = 0; i < edgeAssignment.length(); i++) {
            var ch = edgeAssignment.charAt(i);
            switch (ch) {
                case ',':
                    if (change)
                        e++;
                    if (clear) {
                        var edges = contour.edges;
                        while (e < edges.size()) {
                            edges.get(e).get().color = EdgeColor.WHITE;
                            e++;
                        }
                    }
                    c++;
                    e = 0;
                    if (contours.size() <= c) return;
                    contour = contours.get(c);
                    change = false;
                    clear = true;
                    break;
                case '?':
                    clear = false;
                    break;
                case 'C':
                case 'M':
                case 'W':
                case 'Y':
                case 'c':
                case 'm':
                case 'w':
                case 'y':
                    if (change) {
                        e++;
                        change = false;
                    }
                    if (e < contour.edges.size()) {
                        EdgeColor color;
                        if (ch == 'C' || ch == 'c')
                            color = EdgeColor.CYAN;
                        else if (ch == 'M' || ch == 'm')
                            color = EdgeColor.MAGENTA;
                        else if (ch == 'Y' || ch == 'y')
                            color = EdgeColor.YELLOW;
                        else
                            color = EdgeColor.WHITE;
                        contour.edges.get(e).get().color = color;
                        change = true;
                    }
                    break;
            }
        }
    }

    public static Optional<Long> parseUnicode(String arg) {
        if (arg.isEmpty()) return Optional.empty();

        var unsignedValue = parseUnsignedDecOrHex(arg);
        if (unsignedValue.isPresent()) return Optional.of(unsignedValue.get().longValue());

        if (arg.length() == 4
                && arg.charAt(0) == '\''
                && arg.charAt(2) == '\''
                && arg.charAt(1) != '\0') {
            return Optional.of(((long) arg.charAt(1)) & 0xFF);
        }

        return Optional.empty();
    }

    enum Format {
        AUTO,
        PNG,
        BMP,
        TIFF,
        RGBA,
        FL32,
        TEXT,
        TEXT_FLOAT,
        BINARY,
        BINARY_FLOAT,
        BINARY_FLOAT_BE
    }

    private static void abort(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    private static ImportFont.@Nullable FontHandle loadVarFont(ImportFont.FreetypeHandle library, String filename, boolean[] badAxes) {
        var qmark = filename.indexOf('?');
        var filePath = qmark >= 0 ? filename.substring(0, qmark) : filename;
        var font = ImportFont.loadFont(library, filePath);

        if (font != null && qmark >= 0) {
            var query = filename.substring(qmark + 1);
            var params = query.split("&");
            for (var param : params) {
                var eq = param.indexOf('=');
                if (eq >= 0) {
                    var name = param.substring(0, eq);
                    var valueStr = param.substring(eq + 1);
                    try {
                        var value = Double.parseDouble(valueStr);
                        boolean success;
                        if (name.length() == 4) {
                            success = ImportFont.setFontVariationAxis(library, font, new ImportFont.FontVariationAxis.Tag(name), value)
                                    || ImportFont.setFontVariationAxis(library, font, name, value);
                        } else {
                            success = ImportFont.setFontVariationAxis(library, font, name, value);
                        }
                        if (!success) {
                            badAxes[0] = true;
                            System.err.printf("Font variation axis \"%s\" not found.\n", name);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return font;
    }

    private static void printVarFontAxisList(PrintStream output, ImportFont.FreetypeHandle library, ImportFont.FontHandle font) {
        var axes = new ArrayList<ImportFont.FontVariationAxis>();
        ImportFont.listFontVariationAxes(axes, library, font);
        if (axes.isEmpty()) output.print("The selected font doesn't appear to contain any variation axes.\n");
        else {
            output.print("Available font variation axes:\n");
            for (var axis : axes)
                output.printf("\t[%c%c%c%c] \"%s\" (%.17g to %.17g), default = %.17g\n",
                        axis.tag.characters[0], axis.tag.characters[1],
                        axis.tag.characters[2], axis.tag.characters[3],
                        axis.name, axis.minValue, axis.maxValue, axis.defaultValue);
        }
    }

    private static boolean is8bitFormat(Format format) {
        return format == Format.PNG || format == Format.BMP || format == Format.RGBA
                || format == Format.TEXT || format == Format.BINARY;
    }

    private static @Nullable String writeOutput(Bitmap<Float> bitmap, String filename, Format format) {
        if (!filename.isEmpty()) {
            if (format == Format.AUTO) {
                if (filename.endsWith(".png")) format = Format.PNG;
                else if (filename.endsWith(".bmp")) format = Format.BMP;
                else if (filename.endsWith(".tiff") || filename.endsWith(".tif")) format = Format.TIFF;
                else if (filename.endsWith(".rgba")) format = Format.RGBA;
                else if (filename.endsWith(".fl32")) format = Format.FL32;
                else if (filename.endsWith(".txt")) format = Format.TEXT;
                else if (filename.endsWith(".bin")) format = Format.BINARY;
                else return "Could not deduce format from output file name.";
            }
            return switch (format) {
                case PNG ->
                        SavePng.savePngFloat(bitmap.toBitmapConstSection(), filename) ? null : "Failed to write output PNG image.";
                case BMP ->
                        SaveBmp.saveBmpFloat(bitmap.toBitmapConstSection(), filename) ? null : "Failed to write output BMP image.";
                case TIFF ->
                        SaveTiff.saveTiff(bitmap.toBitmapConstSection(), filename) ? null : "Failed to write output TIFF image.";
                case RGBA ->
                        SaveRgba.saveRgbaFloat(bitmap.toBitmapConstSection(), filename) ? null : "Failed to write output RGBA image.";
                case FL32 ->
                        SaveFl32.saveFl32(bitmap.toBitmapConstSection(), filename) ? null : "Failed to write output FL32 image.";
                case TEXT, TEXT_FLOAT -> {
                    try (var pw = new PrintWriter(filename)) {
                        if (format == Format.TEXT) writeTextBitmap(pw, bitmap);
                        else writeTextBitmapFloat(pw, bitmap);
                    } catch (FileNotFoundException e) {
                        yield "Failed to write output text file.";
                    }
                    yield null;
                }
                case BINARY, BINARY_FLOAT, BINARY_FLOAT_BE -> {
                    try (var fos = new FileOutputStream(filename)) {
                        if (format == Format.BINARY) writeBinBitmap(fos, bitmap);
                        else writeBinBitmapFloat(fos, bitmap, format != Format.BINARY_FLOAT);
                    } catch (IOException e) {
                        yield "Failed to write output binary file.";
                    }
                    yield null;
                }
                default -> null;
            };
        } else if (format == Format.AUTO || format == Format.TEXT) {
            writeTextBitmap(new PrintWriter(System.out, true), bitmap);
        } else if (format == Format.TEXT_FLOAT) {
            writeTextBitmapFloat(new PrintWriter(System.out, true), bitmap);
        } else {
            return "Unsupported format for standard output.";
        }
        return null;
    }

    private static void writeTextBitmap(PrintWriter out, Bitmap<Float> bitmap) {
        var w = bitmap.width;
        var h = bitmap.height;
        var channels = bitmap.channels;
        for (var y = 0; y < h; y++) {
            for (var x = 0; x < w; x++) {
                if (x > 0) out.print(' ');
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < channels; c++) {
                    out.printf("%02X", pixelFloatToByte(bitmap.pixels[base + c]));
                }
            }
            out.println();
        }
    }

    private static void writeTextBitmapFloat(PrintWriter out, Bitmap<Float> bitmap) {
        var w = bitmap.width;
        var h = bitmap.height;
        var channels = bitmap.channels;
        for (var y = 0; y < h; y++) {
            for (var x = 0; x < w; x++) {
                if (x > 0) out.print(' ');
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < channels; c++) {
                    if (c > 0) out.print(' ');
                    out.printf("%.9g", bitmap.pixels[base + c]);
                }
            }
            out.println();
        }
    }

    private static void writeBinBitmap(OutputStream out, Bitmap<Float> bitmap) throws IOException {
        var w = bitmap.width;
        var h = bitmap.height;
        var channels = bitmap.channels;
        for (var y = 0; y < h; y++) {
            for (var x = 0; x < w; x++) {
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < channels; c++) {
                    out.write(pixelFloatToByte(bitmap.pixels[base + c]));
                }
            }
        }
    }

    private static void writeBinBitmapFloat(OutputStream out, Bitmap<Float> bitmap, boolean bigEndian) throws IOException {
        var w = bitmap.width;
        var h = bitmap.height;
        var channels = bitmap.channels;
        var buf = ByteBuffer.allocate(4 * w * channels);
        if (bigEndian) buf.order(ByteOrder.BIG_ENDIAN);
        else buf.order(ByteOrder.LITTLE_ENDIAN);
        for (var y = 0; y < h; y++) {
            buf.clear();
            for (var x = 0; x < w; x++) {
                var base = bitmap.getPixelIndex(x, y);
                for (var c = 0; c < channels; c++) {
                    buf.putFloat(bitmap.pixels[base + c]);
                }
            }
            out.write(buf.array(), 0, buf.position());
        }
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "SameParameterValue"})
    private static boolean cmpExtension(String path, String ext) {
        var pathLen = path.length();
        var extLen = ext.length();
        if (pathLen < extLen) return false;
        for (var i = 1; i <= extLen; i++) {
            var a = path.charAt(pathLen - i);
            var b = ext.charAt(extLen - i);
            if (Character.toUpperCase(a) != Character.toUpperCase(b))
                return false;
        }
        return true;
    }
}
