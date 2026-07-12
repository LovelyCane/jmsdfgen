package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static lovely.cane.jmsdfgen.Arithmetic.boundPoint;
import static lovely.cane.jmsdfgen.Arithmetic.sign;
import static lovely.cane.jmsdfgen.Vector2.*;

public class Contour {
    public final List<EdgeHolder> edges = new ArrayList<>();

    public void addEdge(EdgeHolder edge) {
        edges.add(edge);
    }

    public void bound(double[] xMin, double[] yMin, double[] xMax, double[] yMax) {
        for (var edge : edges) edge.get().bound(xMin, yMin, xMax, yMax);
    }

    public void boundMiters(double[] xMin, double[] yMin, double[] xMax, double[] yMax,
                            double border, double miterLimit, int polarity) {
        if (edges.isEmpty()) return;
        var prevDir = edges.getLast().get().direction(1).normalize(true);
        for (var edge : edges) {
            var seg = edge.get();
            var dir = negate(seg.direction(0).normalize(true));
            if (polarity * crossProduct(prevDir, dir) >= 0) {
                var miterLength = miterLimit;
                var q = 0.5 * (1 - dotProduct(prevDir, dir));
                if (q > 0) miterLength = Math.min(1.0 / Math.sqrt(q), miterLimit);
                var sumDir = add(prevDir, dir);
                var normalizedSum = sumDir.normalize(true);
                var scaledMiter = multiply(border * miterLength, normalizedSum);
                var point0 = seg.point(0);
                var miter = add(point0, scaledMiter);
                boundPoint(xMin, yMin, xMax, yMax, miter);
            }
            prevDir = seg.direction(1).normalize(true);
        }
    }

    public int winding() {
        if (edges.isEmpty()) return 0;
        var total = 0.0;
        if (edges.size() == 1) {
            var a = edges.getFirst().get().point(0);
            var b = edges.getFirst().get().point(1.0 / 3.0);
            var c = edges.getFirst().get().point(2.0 / 3.0);
            total += shoelace(a, b);
            total += shoelace(b, c);
            total += shoelace(c, a);
        } else if (edges.size() == 2) {
            var a = edges.get(0).get().point(0);
            var b = edges.get(0).get().point(0.5);
            var c = edges.get(1).get().point(0);
            var d = edges.get(1).get().point(0.5);
            total += shoelace(a, b);
            total += shoelace(b, c);
            total += shoelace(c, d);
            total += shoelace(d, a);
        } else {
            var prev = edges.getLast().get().point(0);
            for (var edge : edges) {
                var cur = edge.get().point(0);
                total += shoelace(prev, cur);
                prev = cur;
            }
        }
        return sign(total);
    }

    public void reverse() {
        for (var i = edges.size() / 2; i > 0; --i) Collections.swap(edges, i - 1, edges.size() - i);
        for (var edge : edges) edge.get().reverse();
    }

    private static double shoelace(Vector2 a, Vector2 b) {
        return (b.x - a.x) * (a.y + b.y);
    }
}
