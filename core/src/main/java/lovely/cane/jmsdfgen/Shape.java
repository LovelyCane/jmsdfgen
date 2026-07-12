package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static lovely.cane.jmsdfgen.ConvergentCurveOrdering.convergentCurveOrdering;

public class Shape {
    private static final double MSDFGEN_CORNER_DOT_EPSILON = 0.000001;
    private static final double DECONVERGE_OVERSHOOT = 1.11111111111111111;

    public final List<Contour> contours = new ArrayList<>();
    public boolean inverseYAxis = false;

    public void addContour(Contour contour) {
        contours.add(contour);
    }

    public Contour addContour() {
        var contour = new Contour();
        contours.add(contour);
        return contour;
    }

    public boolean validate() {
        for (var contour : contours) {
            if (!contour.edges.isEmpty()) {
                var corner = contour.edges.getLast().get().point(1);
                for (var edge : contour.edges) {
                    if (!Vector2.equals(edge.get().point(0), corner))
                        return false;
                    corner = edge.get().point(1);
                }
            }
        }
        return true;
    }

    public void normalize() {
        for (var contour : contours) {
            if (contour.edges.size() == 1) {
                var parts = contour.edges.getFirst().get().splitInThirds();
                contour.edges.clear();
                contour.edges.add(new EdgeHolder(parts[0]));
                contour.edges.add(new EdgeHolder(parts[1]));
                contour.edges.add(new EdgeHolder(parts[2]));
            } else if (!contour.edges.isEmpty()) {
                var prevEdge = contour.edges.getLast();
                for (var i = 0; i < contour.edges.size(); i++) {
                    var edge = contour.edges.get(i);
                    var prevDir = prevEdge.get().direction(1).normalize();
                    var curDir = edge.get().direction(0).normalize();
                    var dot = Vector2.dotProduct(prevDir, curDir);
                    if (dot < MSDFGEN_CORNER_DOT_EPSILON - 1) {
                        var factor = DECONVERGE_OVERSHOOT * Math.sqrt(1 - (MSDFGEN_CORNER_DOT_EPSILON - 1) * (MSDFGEN_CORNER_DOT_EPSILON - 1))
                                / (MSDFGEN_CORNER_DOT_EPSILON - 1);
                        var axis = Vector2.multiply(factor, Vector2.subtract(curDir, prevDir).normalize());
                        if (convergentCurveOrdering(prevEdge.get(), edge.get()) < 0)
                            axis = Vector2.negate(axis);
                        deconvergeEdge(prevEdge, 1, axis.getOrthogonal(true));
                        deconvergeEdge(edge, 0, axis.getOrthogonal(false));
                    }
                    prevEdge = edge;
                }
            }
        }
    }

    public void bound(double[] xMin, double[] yMin, double[] xMax, double[] yMax) {
        for (var contour : contours) contour.bound(xMin, yMin, xMax, yMax);
    }

    public void boundMiters(double[] xMin, double[] yMin, double[] xMax, double[] yMax,
                            double border, double miterLimit, int polarity) {
        for (var contour : contours) contour.boundMiters(xMin, yMin, xMax, yMax, border, miterLimit, polarity);
    }

    public Bounds getBounds() {
        return getBounds(0, 0, 0);
    }

    public Bounds getBounds(double border, double miterLimit, int polarity) {
        var LARGE_VALUE = 1e240;
        var bounds = new Bounds();
        bounds.l = LARGE_VALUE;
        bounds.b = LARGE_VALUE;
        bounds.r = -LARGE_VALUE;
        bounds.t = -LARGE_VALUE;
        var xMin = new double[]{bounds.l};
        var yMin = new double[]{bounds.b};
        var xMax = new double[]{bounds.r};
        var yMax = new double[]{bounds.t};
        bound(xMin, yMin, xMax, yMax);
        bounds.l = xMin[0];
        bounds.b = yMin[0];
        bounds.r = xMax[0];
        bounds.t = yMax[0];
        if (border > 0) {
            bounds.l -= border;
            bounds.b -= border;
            bounds.r += border;
            bounds.t += border;
            if (miterLimit > 0)
                boundMiters(xMin, yMin, xMax, yMax, border, miterLimit, polarity);
            bounds.l = xMin[0];
            bounds.b = yMin[0];
            bounds.r = xMax[0];
            bounds.t = yMax[0];
        }
        return bounds;
    }

    public void scanline(Scanline line, double y) {
        List<Scanline.Intersection> intersections = new ArrayList<>();
        var x = new double[3];
        var dy = new int[3];
        for (var contour : contours) {
            for (var edge : contour.edges) {
                var n = edge.get().scanlineIntersections(x, dy, y);
                for (var i = 0; i < n; i++) {
                    intersections.add(new Scanline.Intersection(x[i], dy[i]));
                }
            }
        }
        line.setIntersections(intersections);
    }

    public int edgeCount() {
        var total = 0;
        for (var contour : contours)
            total += contour.edges.size();
        return total;
    }

    public void orientContours() {
        class Intersection {
            double x;
            int direction;
            int contourIndex;
        }

        var ratio = 0.5 * (Math.sqrt(5) - 1);
        List<Integer> orientations = new ArrayList<>(Collections.nCopies(contours.size(), 0));
        List<Intersection> intersections = new ArrayList<>();

        for (var i = 0; i < contours.size(); i++) {
            if (orientations.get(i) != 0 || contours.get(i).edges.isEmpty())
                continue;

            var y0 = contours.get(i).edges.getFirst().get().point(0).y;
            var y1 = y0;
            for (var edge : contours.get(i).edges) {
                if (y0 != y1) break;
                y1 = edge.get().point(1).y;
            }
            for (var edge : contours.get(i).edges) {
                if (y0 != y1) break;
                y1 = edge.get().point(ratio).y;
            }
            var y = Arithmetic.mix(y0, y1, ratio);

            var xs = new double[3];
            var dy = new int[3];
            for (var j = 0; j < contours.size(); j++) {
                for (var edge : contours.get(j).edges) {
                    var n = edge.get().scanlineIntersections(xs, dy, y);
                    for (var k = 0; k < n; k++) {
                        var inter = new Intersection();
                        inter.x = xs[k];
                        inter.direction = dy[k];
                        inter.contourIndex = j;
                        intersections.add(inter);
                    }
                }
            }

            if (!intersections.isEmpty()) {
                intersections.sort(Comparator.comparingDouble(a -> a.x));
                for (var j = 1; j < intersections.size(); j++) {
                    if (intersections.get(j).x == intersections.get(j - 1).x) {
                        intersections.get(j).direction = 0;
                        intersections.get(j - 1).direction = 0;
                    }
                }
                for (var j = 0; j < intersections.size(); j++) {
                    if (intersections.get(j).direction != 0) {
                        var inc = 2 * ((j & 1) ^ (intersections.get(j).direction > 0 ? 1 : 0)) - 1;
                        orientations.set(intersections.get(j).contourIndex,
                                orientations.get(intersections.get(j).contourIndex) + inc);
                    }
                }
                intersections.clear();
            }
        }

        for (var i = 0; i < contours.size(); i++) {
            if (orientations.get(i) < 0)
                contours.get(i).reverse();
        }
    }

    public YAxisOrientation getYAxisOrientation() {
        return inverseYAxis ? YAxisOrientation.Y_DOWNWARD : YAxisOrientation.Y_UPWARD;
    }

    public void setYAxisOrientation(YAxisOrientation orientation) {
        inverseYAxis = (orientation != YAxisOrientation.Y_UPWARD);
    }

    public static class Bounds {
        public double l, b, r, t;
    }

    private static void deconvergeEdge(EdgeHolder edgeHolder, int param, Vector2 vector) {
        var seg = edgeHolder.get();
        if (seg.type() == EdgeSegment.QuadraticSegment.EDGE_TYPE) {
            edgeHolder.set(new EdgeHolder(((EdgeSegment.QuadraticSegment) seg).convertToCubic()));
            seg = edgeHolder.get();
        }
        if (seg.type() == 3) {
            var cubic = (EdgeSegment.CubicSegment) seg;
            if (param == 0) {
                var dir = Vector2.subtract(cubic.p[1], cubic.p[0]);
                var len = dir.length();
                cubic.p[1] = Vector2.add(cubic.p[1], Vector2.multiply(len, vector));
            } else if (param == 1) {
                var dir = Vector2.subtract(cubic.p[2], cubic.p[3]);
                var len = dir.length();
                cubic.p[2] = Vector2.add(cubic.p[2], Vector2.multiply(len, vector));
            }
        }
    }
}
