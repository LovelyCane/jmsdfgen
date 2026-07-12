package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ImportSvg {
    private static final double ARC_SEGMENTS_PER_PI = 2;
    private static final double ENDPOINT_SNAP_RANGE_PROPORTION = 1.0 / 16384.0;

    public static final int SVG_IMPORT_FAILURE = 0x00;
    public static final int SVG_IMPORT_SUCCESS_FLAG = 0x01;
    public static final int SVG_IMPORT_PARTIAL_FAILURE_FLAG = 0x02;
    public static final int SVG_IMPORT_INCOMPLETE_FLAG = 0x04;
    public static final int SVG_IMPORT_UNSUPPORTED_FEATURE_FLAG = 0x08;
    public static final int SVG_IMPORT_TRANSFORMATION_IGNORED_FLAG = 0x10;

    private ImportSvg() {
    }

    public static boolean buildShapeFromSvgPath(Shape shape, String pathDef, double endpointSnapRange) {
        var nodeTypeHolder = new char[1];
        var prevNodeType = '\0';
        var prevNode = new Vector2(0, 0);
        var nodeTypePreread = false;
        var pos = new int[]{0};

        while (nodeTypePreread || readNodeType(nodeTypeHolder, pathDef, pos)) {
            nodeTypePreread = false;
            var nodeType = nodeTypeHolder[0];
            var contour = shape.addContour();
            var contourStart = true;

            var startPoint = new Vector2(0, 0);
            var controlPoint = new Vector2[2];
            var node = new Vector2(0, 0);

            while (pos[0] < pathDef.length()) {
                switch (nodeType) {
                    case 'M':
                    case 'm':
                        if (!contourStart) {
                            nodeTypePreread = true;
                            break;
                        }
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 'm') {
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        startPoint = node;
                        nodeType--;
                        break;
                    case 'Z':
                    case 'z':
                        if (contourStart) return false;
                        break;
                    case 'L':
                    case 'l':
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 'l') {
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        contour.addEdge(new EdgeHolder(prevNode, node));
                        break;
                    case 'H':
                    case 'h':
                        var x = new double[1];
                        if (!readDouble(x, pathDef, pos)) return false;
                        node.x = x[0];
                        if (nodeType == 'h') {
                            node.x += prevNode.x;
                        }
                        node.y = prevNode.y;
                        contour.addEdge(new EdgeHolder(prevNode, node));
                        break;
                    case 'V':
                    case 'v':
                        var y = new double[1];
                        if (!readDouble(y, pathDef, pos)) return false;
                        node.y = y[0];
                        if (nodeType == 'v') {
                            node.y += prevNode.y;
                        }
                        node.x = prevNode.x;
                        contour.addEdge(new EdgeHolder(prevNode, node));
                        break;
                    case 'Q':
                    case 'q':
                        if (!readCoord(controlPoint[0], pathDef, pos)) return false;
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 'q') {
                            controlPoint[0].x += prevNode.x;
                            controlPoint[0].y += prevNode.y;
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        contour.addEdge(new EdgeHolder(prevNode, controlPoint[0], node));
                        break;
                    case 'T':
                    case 't':
                        if (prevNodeType == 'Q' || prevNodeType == 'q' ||
                                prevNodeType == 'T' || prevNodeType == 't') {
                            controlPoint[0] = new Vector2(node.x + node.x - controlPoint[0].x,
                                    node.y + node.y - controlPoint[0].y);
                        } else {
                            controlPoint[0] = node;
                        }
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 't') {
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        contour.addEdge(new EdgeHolder(prevNode, controlPoint[0], node));
                        break;
                    case 'C':
                    case 'c':
                        if (!readCoord(controlPoint[0], pathDef, pos)) return false;
                        if (!readCoord(controlPoint[1], pathDef, pos)) return false;
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 'c') {
                            controlPoint[0].x += prevNode.x;
                            controlPoint[0].y += prevNode.y;
                            controlPoint[1].x += prevNode.x;
                            controlPoint[1].y += prevNode.y;
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        contour.addEdge(new EdgeHolder(prevNode, controlPoint[0], controlPoint[1], node));
                        break;
                    case 'S':
                    case 's':
                        if (prevNodeType == 'C' || prevNodeType == 'c' ||
                                prevNodeType == 'S' || prevNodeType == 's') {
                            controlPoint[0] = new Vector2(node.x + node.x - controlPoint[1].x,
                                    node.y + node.y - controlPoint[1].y);
                        } else {
                            controlPoint[0] = node;
                        }
                        if (!readCoord(controlPoint[1], pathDef, pos)) return false;
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 's') {
                            controlPoint[1].x += prevNode.x;
                            controlPoint[1].y += prevNode.y;
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        contour.addEdge(new EdgeHolder(prevNode, controlPoint[0], controlPoint[1], node));
                        break;
                    case 'A':
                    case 'a': {
                        var radius = new Vector2(0, 0);
                        double angle;
                        boolean[] largeArc = new boolean[1], sweep = new boolean[1];
                        if (!readCoord(radius, pathDef, pos)) return false;
                        var angleArr = new double[1];
                        if (!readDouble(angleArr, pathDef, pos)) return false;
                        angle = angleArr[0];
                        if (!readBool(largeArc, pathDef, pos)) return false;
                        if (!readBool(sweep, pathDef, pos)) return false;
                        if (!readCoord(node, pathDef, pos)) return false;
                        if (nodeType == 'a') {
                            node.x += prevNode.x;
                            node.y += prevNode.y;
                        }
                        angle *= Math.PI / 180.0;
                        addArcApproximate(contour, prevNode, node, radius, angle, largeArc[0], sweep[0]);
                        break;
                    }
                    default:
                        return false;
                }
                if (nodeType == 'Z' || nodeType == 'z') break;
                contourStart &= (nodeType == 'M' || nodeType == 'm');
                prevNode = node;
                prevNodeType = nodeType;
                if (!readNodeType(nodeTypeHolder, pathDef, pos)) break;
                nodeType = nodeTypeHolder[0];
            }

            if (!contour.edges.isEmpty()) {
                var first = contour.edges.getFirst().get().point(0);
                var last = contour.edges.getLast().get().point(1);
                if (last.x != startPoint.x || last.y != startPoint.y) {
                    var dist = new Vector2(last.x - first.x, last.y - first.y).length();
                    if (dist < endpointSnapRange) {
                        contour.edges.getLast().get().moveEndPoint(first);
                    } else {
                        contour.addEdge(new EdgeHolder(last, startPoint));
                    }
                }
            }
            prevNode = startPoint;
            prevNodeType = '\0';
        }
        return true;
    }

    public static boolean loadSvgShape(Shape output, String filename, int pathIndex, Vector2 dimensions) {
        try (var is = Files.newInputStream(Paths.get(filename))) {
            var factory = SAXParserFactory.newInstance();
            var parser = factory.newSAXParser();
            var handler = new SvgPathHandler();
            parser.parse(is, handler);

            if (handler.pathDefs.isEmpty()) return false;

            var idx = pathIndex;
            if (idx == 0) idx = handler.pathDefs.size() - 1;
            else if (idx > 0) idx = idx - 1;
            else idx = handler.pathDefs.size() + idx;
            if (idx < 0 || idx >= handler.pathDefs.size()) return false;

            var dims = new Vector2(handler.dimensions.x, handler.dimensions.y);
            if (handler.viewBox != null) {
                var parts = handler.viewBox.trim().split("[\\s,]+");
                if (parts.length == 4) {
                    dims.x = Double.parseDouble(parts[2]);
                    dims.y = Double.parseDouble(parts[3]);
                }
            }
            dimensions.x = dims.x;
            dimensions.y = dims.y;
            output.contours.clear();
            output.setYAxisOrientation(YAxisOrientation.Y_DOWNWARD);
            return buildShapeFromSvgPath(output, handler.pathDefs.get(idx),
                    ENDPOINT_SNAP_RANGE_PROPORTION * dims.length());

        } catch (Exception e) {
            return false;
        }
    }

    public static int loadSvgShape(Shape output, Shape.Bounds viewBox, String filename) {
        try (var is = Files.newInputStream(Paths.get(filename))) {
            var factory = SAXParserFactory.newInstance();
            var parser = factory.newSAXParser();
            var handler = new SvgPathHandler();
            parser.parse(is, handler);

            if (handler.pathDefs.isEmpty()) return SVG_IMPORT_FAILURE;

            viewBox.l = 0;
            viewBox.b = 0;
            var dims = new Vector2(handler.dimensions.x, handler.dimensions.y);
            if (handler.viewBox != null) {
                var parts = handler.viewBox.trim().split("[\\s,]+");
                if (parts.length == 4) {
                    viewBox.l = Double.parseDouble(parts[0]);
                    viewBox.b = Double.parseDouble(parts[1]);
                    dims.x = Double.parseDouble(parts[2]);
                    dims.y = Double.parseDouble(parts[3]);
                }
            }
            viewBox.r = viewBox.l + dims.x;
            viewBox.t = viewBox.b + dims.y;

            output.contours.clear();
            output.setYAxisOrientation(YAxisOrientation.Y_DOWNWARD);
            if (!buildShapeFromSvgPath(output, handler.pathDefs.getLast(),
                    ENDPOINT_SNAP_RANGE_PROPORTION * dims.length()))
                return SVG_IMPORT_FAILURE;

            return handler.flags | SVG_IMPORT_SUCCESS_FLAG;

        } catch (Exception e) {
            return SVG_IMPORT_FAILURE;
        }
    }


    private static void skipExtraChars(String str, int[] pos) {
        while (pos[0] < str.length() &&
                (str.charAt(pos[0]) == ',' || str.charAt(pos[0]) == ' ' ||
                        str.charAt(pos[0]) == '\t' || str.charAt(pos[0]) == '\r' ||
                        str.charAt(pos[0]) == '\n'))
            pos[0]++;
    }

    private static boolean readNodeType(char[] output, String str, int[] pos) {
        skipExtraChars(str, pos);
        if (pos[0] >= str.length()) return false;
        var c = str.charAt(pos[0]);
        if (c != '+' && c != '-' && c != '.' && c != ',' && (c < '0' || c > '9')) {
            output[0] = c;
            pos[0]++;
            return true;
        }
        return false;
    }

    private static boolean readDouble(double[] output, String str, int[] pos) {
        skipExtraChars(str, pos);
        var start = pos[0];
        var end = start;
        while (end < str.length() && (Character.isDigit(str.charAt(end)) ||
                str.charAt(end) == '.' || str.charAt(end) == '+' || str.charAt(end) == '-' ||
                str.charAt(end) == 'e' || str.charAt(end) == 'E')) end++;
        if (end > start) {
            try {
                output[0] = Double.parseDouble(str.substring(start, end));
                pos[0] = end;
                return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static boolean readCoord(Vector2 output, String str, int[] pos) {
        double[] x = new double[1], y = new double[1];
        if (readDouble(x, str, pos) && readDouble(y, str, pos)) {
            output.x = x[0];
            output.y = y[0];
            return true;
        }
        return false;
    }

    private static boolean readBool(boolean[] output, String str, int[] pos) {
        skipExtraChars(str, pos);
        var start = pos[0];
        var end = start;
        while (end < str.length() && (str.charAt(end) >= '0' && str.charAt(end) <= '9')) end++;
        if (end > start) {
            output[0] = Integer.parseInt(str.substring(start, end)) != 0;
            pos[0] = end;
            return true;
        }
        return false;
    }

    private static double arcAngle(Vector2 u, Vector2 v) {
        var dot = Vector2.dotProduct(u, v) / (u.length() * v.length());
        dot = Arithmetic.clamp(dot, -1.0, 1.0);
        return Arithmetic.nonZeroSign(Vector2.crossProduct(u, v)) * Math.acos(dot);
    }

    private static Vector2 rotateVector(Vector2 v, Vector2 direction) {
        return new Vector2(direction.x * v.x - direction.y * v.y,
                direction.y * v.x + direction.x * v.y);
    }

    private static void addArcApproximate(Contour contour, Vector2 startPoint, Vector2 endPoint,
                                          Vector2 radius, double rotation, boolean largeArc, boolean sweep) {
        if (endPoint.x == startPoint.x && endPoint.y == startPoint.y) return;
        if (radius.x == 0 || radius.y == 0) {
            contour.addEdge(new EdgeHolder(startPoint, endPoint));
            return;
        }

        radius.x = Math.abs(radius.x);
        radius.y = Math.abs(radius.y);
        var axis = new Vector2(Math.cos(rotation), Math.sin(rotation));

        var rm = rotateVector(new Vector2(0.5 * (startPoint.x - endPoint.x), 0.5 * (startPoint.y - endPoint.y)),
                new Vector2(axis.x, -axis.y));
        var rm2 = new Vector2(rm.x * rm.x, rm.y * rm.y);
        var radius2 = new Vector2(radius.x * radius.x, radius.y * radius.y);
        var radiusGap = rm2.x / radius2.x + rm2.y / radius2.y;
        if (radiusGap > 1) {
            var s = Math.sqrt(radiusGap);
            radius = new Vector2(radius.x * s, radius.y * s);
            radius2 = new Vector2(radius.x * radius.x, radius.y * radius.y);
        }
        var dq = radius2.x * rm2.y + radius2.y * rm2.x;
        var pq = radius2.x * radius2.y / dq - 1;
        var q = (largeArc == sweep ? -1 : 1) * Math.sqrt(Math.max(pq, 0));
        var rc = new Vector2(q * radius.x * rm.y / radius.y, -q * radius.y * rm.x / radius.x);
        var center = new Vector2(0.5 * (startPoint.x + endPoint.x) + rotateVector(rc, axis).x,
                0.5 * (startPoint.y + endPoint.y) + rotateVector(rc, axis).y);

        var u = new Vector2((rm.x - rc.x) / radius.x, (rm.y - rc.y) / radius.y);
        var v = new Vector2((-rm.x - rc.x) / radius.x, (-rm.y - rc.y) / radius.y);
        var angleStart = arcAngle(new Vector2(1, 0), u);
        var angleExtent = arcAngle(u, v);

        if (!sweep && angleExtent > 0) angleExtent -= 2 * Math.PI;
        else if (sweep && angleExtent < 0) angleExtent += 2 * Math.PI;

        var segments = (int) Math.ceil(ARC_SEGMENTS_PER_PI / Math.PI * Math.abs(angleExtent));
        var angleIncrement = angleExtent / segments;
        var cl = 4.0 / 3.0 * Math.sin(0.5 * angleIncrement) / (1 + Math.cos(0.5 * angleIncrement));

        var prevNode = startPoint;
        var angle = angleStart;
        for (var i = 0; i < segments; i++) {
            var d = new Vector2(Math.cos(angle), Math.sin(angle));
            var c0 = new Vector2(center.x + rotateVector(new Vector2((d.x - cl * d.y) * radius.x, (d.y + cl * d.x) * radius.y), axis).x,
                    center.y + rotateVector(new Vector2((d.x - cl * d.y) * radius.x, (d.y + cl * d.x) * radius.y), axis).y);
            angle += angleIncrement;
            d = new Vector2(Math.cos(angle), Math.sin(angle));
            var c1 = new Vector2(center.x + rotateVector(new Vector2((d.x + cl * d.y) * radius.x, (d.y - cl * d.x) * radius.y), axis).x,
                    center.y + rotateVector(new Vector2((d.x + cl * d.y) * radius.x, (d.y - cl * d.x) * radius.y), axis).y);
            var node = (i == segments - 1) ? endPoint :
                    new Vector2(center.x + rotateVector(new Vector2(d.x * radius.x, d.y * radius.y), axis).x,
                            center.y + rotateVector(new Vector2(d.x * radius.x, d.y * radius.y), axis).y);
            contour.addEdge(new EdgeHolder(prevNode, c0, c1, node));
            prevNode = node;
        }
    }


    private static class SvgPathHandler extends DefaultHandler {
        int flags = 0;
        final Vector2 dimensions = new Vector2(0, 0);
        @Nullable String viewBox = null;
        final List<String> pathDefs = new ArrayList<>();
        boolean inIgnored = false;
        int ignoredDepth = 0;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            if (ignoredDepth > 0) {
                ignoredDepth++;
                return;
            }
            var name = qName.isEmpty() ? localName : qName;
            switch (name) {
                case "svg":
                    var w = attributes.getValue("width");
                    if (w != null) dimensions.x = Double.parseDouble(w);
                    var h = attributes.getValue("height");
                    if (h != null) dimensions.y = Double.parseDouble(h);
                    viewBox = attributes.getValue("viewBox");
                    break;
                case "path":
                    var d = attributes.getValue("d");
                    if (d != null) pathDefs.add(d);
                    if (attributes.getValue("transform") != null)
                        flags |= SVG_IMPORT_TRANSFORMATION_IGNORED_FLAG;
                    break;
                case "g":
                    if (attributes.getValue("transform") != null)
                        flags |= SVG_IMPORT_TRANSFORMATION_IGNORED_FLAG;
                    break;
                case "rect":
                case "circle":
                case "ellipse":
                case "polygon":
                    flags |= SVG_IMPORT_INCOMPLETE_FLAG;
                    ignoredDepth = 1;
                    inIgnored = true;
                    break;
                case "mask":
                case "use":
                    flags |= SVG_IMPORT_UNSUPPORTED_FEATURE_FLAG;
                    ignoredDepth = 1;
                    inIgnored = true;
                    break;
                default:
                    ignoredDepth = 1;
                    inIgnored = true;
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (ignoredDepth > 0) {
                ignoredDepth--;
                if (ignoredDepth == 0) inIgnored = false;
            }
        }
    }
}
