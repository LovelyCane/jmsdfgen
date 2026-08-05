package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;

import java.io.*;

public final class ShapeDescription {
    private ShapeDescription() {
    }

    public static boolean readShapeDescription(String input, Shape output, boolean[] colorsSpecified) {
        var locColorsSpec = new boolean[]{false};
        output.contours.clear();
        output.setYAxisOrientation(YAxisOrientation.Y_UPWARD);
        var pos = new int[]{0};
        var p = new Vector2(0, 0);
        var result = readCoord(input, pos, p);
        if (result == 2) {
            return readContour(input, pos, output.addContour(), p, -1, locColorsSpec);
        } else if (result == 1) return false;
        else {
            var c = readChar(input, pos);
            if (c == '@') {
                if (matchString(input, pos, "y-down"))
                    output.setYAxisOrientation(YAxisOrientation.Y_DOWNWARD);
                else if (matchString(input, pos, "y-up"))
                    output.setYAxisOrientation(YAxisOrientation.Y_UPWARD);
                else if (matchString(input, pos, "invert-y"))
                    output.inverseYAxis = true;
                else return false;
                c = readChar(input, pos);
            }
            while (c == '{') {
                if (!readContour(input, pos, output.addContour(), null, '}', locColorsSpec))
                    return false;
                c = readChar(input, pos);
            }
            if (colorsSpecified.length > 0)
                colorsSpecified[0] = locColorsSpec[0];
            return c == -1;
        }
    }

    public static boolean readShapeDescription(File file, Shape output, boolean[] colorsSpecified) throws IOException {
        var sb = new StringBuilder();
        try (var reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return readShapeDescription(sb.toString(), output, colorsSpecified);
    }

    public static boolean readShapeDescription(InputStream input, Shape output, boolean[] colorsSpecified) throws IOException {
        var sb = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append('\n');
        }
        return readShapeDescription(sb.toString(), output, colorsSpecified);
    }

    public static boolean writeShapeDescription(Writer writer, Shape shape) throws IOException {
        if (!shape.validate()) return false;
        var writeColors = isColored(shape);
        var orientation = shape.getYAxisOrientation();
        if (orientation == YAxisOrientation.Y_UPWARD) writer.write("@y-up\n");
        else if (orientation == YAxisOrientation.Y_DOWNWARD) writer.write("@y-down\n");

        for (var contour : shape.contours) {
            writer.write("{\n");
            if (!contour.edges.isEmpty()) {
                for (var edge : contour.edges) {
                    var colorCode = '\0';
                    if (writeColors) {
                        switch (edge.get().color) {
                            case YELLOW:
                                colorCode = 'y';
                                break;
                            case MAGENTA:
                                colorCode = 'm';
                                break;
                            case CYAN:
                                colorCode = 'c';
                                break;
                            case WHITE:
                                colorCode = 'w';
                                break;
                            default:
                        }
                    }
                    var pts = edge.get().controlPoints();
                    var type = edge.get().type();
                    if (type == EdgeSegment.LinearSegment.EDGE_TYPE) {
                        writer.write("\t");
                        writeCoord(writer, pts[0]);
                        writer.write(";\n");
                        if (colorCode != '\0') writer.write("\t\t" + colorCode + ";\n");
                    } else if (type == EdgeSegment.QuadraticSegment.EDGE_TYPE) {
                        writer.write("\t");
                        writeCoord(writer, pts[0]);
                        writer.write(";\n\t\t");
                        if (colorCode != '\0') writer.write(colorCode);
                        writer.write("(");
                        writeCoord(writer, pts[1]);
                        writer.write(");\n");
                    } else if (type == EdgeSegment.CubicSegment.EDGE_TYPE) {
                        writer.write("\t");
                        writeCoord(writer, pts[0]);
                        writer.write(";\n\t\t");
                        if (colorCode != '\0') writer.write(colorCode);
                        writer.write("(");
                        writeCoord(writer, pts[1]);
                        writer.write("; ");
                        writeCoord(writer, pts[2]);
                        writer.write(");\n");
                    }
                }
                writer.write("\t#\n");
            }
            writer.write("}\n");
        }
        writer.flush();
        return true;
    }

    private static void writeCoord(Writer writer, Vector2 coord) throws IOException {
        writer.write(String.format("%.12g, %.12g", coord.x, coord.y));
    }

    private static boolean isColored(Shape shape) {
        for (var contour : shape.contours) {
            for (var edge : contour.edges) {
                if (edge.get().color != EdgeColor.WHITE) return true;
            }
        }
        return false;
    }


    private static int readChar(String input, int[] pos) {
        while (pos[0] < input.length()) {
            var c = input.charAt(pos[0]++);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                return c;
            }
        }
        pos[0] = Integer.MAX_VALUE;
        return -1;
    }

    private static int readCoord(String input, int[] pos, Vector2 coord) {
        var start = pos[0];
        while (start < input.length() && (input.charAt(start) == ' ' || input.charAt(start) == '\t' || input.charAt(start) == '\n' || input.charAt(start) == '\r'))
            start++;
        if (start >= input.length()) return 0;
        var end = start;
        while (end < input.length() && input.charAt(end) != ',' && input.charAt(end) != ' ' && input.charAt(end) != '\t' && input.charAt(end) != '\n' && input.charAt(end) != '\r')
            end++;
        try {
            coord.x = Double.parseDouble(input.substring(start, end));
            pos[0] = end;
        } catch (NumberFormatException e) {
            return 0;
        }
        while (pos[0] < input.length() && (input.charAt(pos[0]) == ' ' || input.charAt(pos[0]) == '\t' || input.charAt(pos[0]) == '\n' || input.charAt(pos[0]) == '\r'))
            pos[0]++;
        if (pos[0] >= input.length() || input.charAt(pos[0]) != ',') return 1;
        do pos[0]++;
        while (pos[0] < input.length() && (input.charAt(pos[0]) == ' ' || input.charAt(pos[0]) == '\t' || input.charAt(pos[0]) == '\n' || input.charAt(pos[0]) == '\r'));
        start = pos[0];
        while (pos[0] < input.length() && input.charAt(pos[0]) != ' ' && input.charAt(pos[0]) != '\t' && input.charAt(pos[0]) != '\n' && input.charAt(pos[0]) != '\r' && input.charAt(pos[0]) != ';' && input.charAt(pos[0]) != ')' && input.charAt(pos[0]) != '#')
            pos[0]++;
        try {
            coord.y = Double.parseDouble(input.substring(start, pos[0]));
            return 2;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static boolean matchString(String input, int[] pos, String str) {
        if (input.startsWith(str, pos[0])) {
            pos[0] += str.length();
            return true;
        }
        return false;
    }

    private static int readControlPoints(String input, int[] pos, Vector2[] output) {
        var result = readCoord(input, pos, output[0]);
        if (result == 2) {
            switch (readChar(input, pos)) {
                case ')':
                    return 1;
                case ';':
                    break;
                default:
                    return -1;
            }
            result = readCoord(input, pos, output[1]);
            if (result == 2 && readChar(input, pos) == ')') return 2;
        } else if (result != 1 && readChar(input, pos) == ')') {
            return 0;
        }
        return -1;
    }

    private static boolean readContour(String input, int[] pos, Contour output, @Nullable Vector2 first,
                                       int terminator, boolean[] colorsSpecified) {
        var p = new Vector2[4];
        for (var i = 0; i < 4; i++) p[i] = new Vector2(0, 0);
        Vector2 start;
        if (first != null) {
            p[0] = Vector2.copy(first);
        } else {
            var res = readCoord(input, pos, p[0]);
            if (res != 2) return res != 1 && readChar(input, pos) == terminator;
        }
        start = p[0];
        int c;
        while ((c = readChar(input, pos)) != terminator) {
            if (c != ';') return false;
            var color = EdgeColor.WHITE;
            var res = readCoord(input, pos, p[1]);
            if (res == 2) {
                output.addEdge(new EdgeHolder(Vector2.copy(p[0]), Vector2.copy(p[1]), color));
                p[0] = Vector2.copy(p[1]);
                continue;
            } else if (res == 1) return false;

            var controlPoints = 0;
            var finishNow = false;
            outer:
            while (true) {
                switch (c = readChar(input, pos)) {
                    case '#':
                        output.addEdge(new EdgeHolder(Vector2.copy(p[0]), Vector2.copy(start), color));
                        p[0] = Vector2.copy(start);
                        break outer;
                    case ';':
                        controlPoints = 0;
                        finishNow = true;
                        break outer;
                    case '(':
                        var cp = new Vector2[]{p[1], p[2]};
                        controlPoints = readControlPoints(input, pos, cp);
                        if (controlPoints < 0) return false;
                        finishNow = true;
                        break outer;
                    case 'C':
                    case 'c':
                        color = EdgeColor.CYAN;
                        colorsSpecified[0] = true;
                        break;
                    case 'M':
                    case 'm':
                        color = EdgeColor.MAGENTA;
                        colorsSpecified[0] = true;
                        break;
                    case 'Y':
                    case 'y':
                        color = EdgeColor.YELLOW;
                        colorsSpecified[0] = true;
                        break;
                    case 'W':
                    case 'w':
                        color = EdgeColor.WHITE;
                        colorsSpecified[0] = true;
                        break;
                    default:
                        return c == terminator;
                }
                switch (readChar(input, pos)) {
                    case ';':
                        controlPoints = 0;
                        finishNow = true;
                        break outer;
                    case '(': {
                        var cp2 = new Vector2[]{p[1], p[2]};
                        controlPoints = readControlPoints(input, pos, cp2);
                        if (controlPoints < 0) return false;
                        finishNow = true;
                        break outer;
                    }
                    default:
                        return false;
                }
            }
            if (!finishNow) continue;

            if (readChar(input, pos) != ';') return false;
            res = readCoord(input, pos, p[1 + controlPoints]);
            if (res != 2) {
                if (res == 1) return false;
                else {
                    if (readChar(input, pos) == '#')
                        p[1 + controlPoints] = Vector2.copy(start);
                    else return false;
                }
            }
            switch (controlPoints) {
                case 0:
                    output.addEdge(new EdgeHolder(Vector2.copy(p[0]), Vector2.copy(p[1]), color));
                    p[0] = Vector2.copy(p[1]);
                    break;
                case 1:
                    output.addEdge(new EdgeHolder(Vector2.copy(p[0]), Vector2.copy(p[1]), Vector2.copy(p[2]), color));
                    p[0] = Vector2.copy(p[2]);
                    break;
                case 2:
                    output.addEdge(new EdgeHolder(Vector2.copy(p[0]), Vector2.copy(p[1]), Vector2.copy(p[2]), Vector2.copy(p[3]), color));
                    p[0] = Vector2.copy(p[3]);
                    break;
            }
        }
        return true;
    }
}
