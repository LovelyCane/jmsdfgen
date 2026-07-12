package lovely.cane.jmsdfgen;

import java.nio.file.Files;
import java.nio.file.Paths;

public final class ExportSvg {
    private ExportSvg() {
    }

    public static boolean saveSvgShape(Shape shape, String filename) {
        try {
            var sb = new StringBuilder();
            sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\"><path");
            if (shape.getYAxisOrientation() == YAxisOrientation.Y_UPWARD)
                sb.append(" transform=\"scale(1 -1)\"");
            sb.append(" d=\"");
            writeSvgPathDef(sb, shape);
            sb.append("\"/></svg>\n");
            Files.writeString(Paths.get(filename), sb.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean saveSvgShape(Shape shape, Shape.Bounds bounds, String filename) {
        try {
            var sb = new StringBuilder();
            sb.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"%.17g %.17g %.17g %.17g\"><path", bounds.l, bounds.b, bounds.r - bounds.l, bounds.t - bounds.b));
            if (shape.getYAxisOrientation() == YAxisOrientation.Y_UPWARD)
                sb.append(String.format(" transform=\"translate(0 %.17g) scale(1 -1)\"", bounds.b + bounds.t));
            sb.append(" d=\"");
            writeSvgPathDef(sb, shape);
            sb.append("\"/></svg>\n");
            Files.writeString(Paths.get(filename), sb.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private static void writeSvgCoord(StringBuilder sb, double x, double y) {
        sb.append(String.format("%.17g %.17g", x, y));
    }

    private static void writeSvgPathDef(StringBuilder sb, Shape shape) {
        var beginning = true;
        for (var c : shape.contours) {
            if (c.edges.isEmpty())
                continue;
            if (beginning)
                beginning = false;
            else
                sb.append(' ');
            sb.append("M ");
            writeSvgCoord(sb, c.edges.getFirst().get().controlPoints()[0].x, c.edges.getFirst().get().controlPoints()[0].y);
            for (var e : c.edges) {
                var cp = e.get().controlPoints();
                switch (e.get().type()) {
                    case EdgeSegment.LinearSegment.EDGE_TYPE:
                        sb.append(" L ");
                        writeSvgCoord(sb, cp[1].x, cp[1].y);
                        break;
                    case EdgeSegment.QuadraticSegment.EDGE_TYPE:
                        sb.append(" Q ");
                        writeSvgCoord(sb, cp[1].x, cp[1].y);
                        sb.append(' ');
                        writeSvgCoord(sb, cp[2].x, cp[2].y);
                        break;
                    case EdgeSegment.CubicSegment.EDGE_TYPE:
                        sb.append(" C ");
                        writeSvgCoord(sb, cp[1].x, cp[1].y);
                        sb.append(' ');
                        writeSvgCoord(sb, cp[2].x, cp[2].y);
                        sb.append(' ');
                        writeSvgCoord(sb, cp[3].x, cp[3].y);
                        break;
                }
            }
            sb.append(" Z");
        }
    }
}
