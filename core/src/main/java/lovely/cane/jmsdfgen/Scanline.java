package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scanline {
    private List<Intersection> intersections;
    private int lastIndex;

    public Scanline() {
        intersections = new ArrayList<>();
        lastIndex = 0;
    }

    public void setIntersections(List<Intersection> intersections) {
        this.intersections = new ArrayList<>(intersections);
        preprocess();
    }

    public int countIntersections(double x) {
        return moveTo(x) + 1;
    }

    public int sumIntersections(double x) {
        var index = moveTo(x);
        if (index >= 0) return intersections.get(index).direction;
        return 0;
    }

    public boolean filled(double x, FillRule fillRule) {
        return interpretFillRule(sumIntersections(x), fillRule);
    }

    private void preprocess() {
        lastIndex = 0;
        if (!intersections.isEmpty()) {
            intersections.sort(Comparator.comparingDouble(a -> a.x));
            var totalDirection = 0;
            for (var inter : intersections) {
                totalDirection += inter.direction;
                inter.direction = totalDirection;
            }
        }
    }

    private int moveTo(double x) {
        if (intersections.isEmpty()) return -1;
        var index = lastIndex;
        if (x < intersections.get(index).x) {
            do {
                if (index == 0) {
                    lastIndex = 0;
                    return -1;
                }
                --index;
            } while (x < intersections.get(index).x);
        } else {
            while (index < intersections.size() - 1 && x >= intersections.get(index + 1).x) ++index;
        }
        lastIndex = index;
        return index;
    }

    public static boolean interpretFillRule(int intersections, FillRule fillRule) {
        return switch (fillRule) {
            case FILL_NONZERO -> intersections != 0;
            case FILL_ODD -> (intersections & 1) != 0;
            case FILL_POSITIVE -> intersections > 0;
            case FILL_NEGATIVE -> intersections < 0;
        };
    }

    public static double overlap(Scanline a, Scanline b, double xFrom, double xTo, FillRule fillRule) {
        double total = 0;
        boolean aInside = false, bInside = false;
        int ai = 0, bi = 0;
        var aIntersections = a.intersections;
        var bIntersections = b.intersections;
        var ax = !aIntersections.isEmpty() ? aIntersections.get(ai).x : xTo;
        var bx = !bIntersections.isEmpty() ? bIntersections.get(bi).x : xTo;
        while (ax < xFrom || bx < xFrom) {
            var xNext = Math.min(ax, bx);
            if (ax == xNext && ai < aIntersections.size()) {
                aInside = interpretFillRule(aIntersections.get(ai).direction, fillRule);
                ai++;
                ax = ai < aIntersections.size() ? aIntersections.get(ai).x : xTo;
            }
            if (bx == xNext && bi < bIntersections.size()) {
                bInside = interpretFillRule(bIntersections.get(bi).direction, fillRule);
                bi++;
                bx = bi < bIntersections.size() ? bIntersections.get(bi).x : xTo;
            }
        }
        var x = xFrom;
        while (ax < xTo || bx < xTo) {
            var xNext = Math.min(ax, bx);
            if (aInside == bInside) total += xNext - x;
            if (ax == xNext && ai < aIntersections.size()) {
                aInside = interpretFillRule(aIntersections.get(ai).direction, fillRule);
                ai++;
                ax = ai < aIntersections.size() ? aIntersections.get(ai).x : xTo;
            }
            if (bx == xNext && bi < bIntersections.size()) {
                bInside = interpretFillRule(bIntersections.get(bi).direction, fillRule);
                bi++;
                bx = bi < bIntersections.size() ? bIntersections.get(bi).x : xTo;
            }
            x = xNext;
        }
        if (aInside == bInside) total += xTo - x;
        return total;
    }

    public enum FillRule {
        FILL_NONZERO,
        FILL_ODD,
        FILL_POSITIVE,
        FILL_NEGATIVE
    }

    public static class Intersection {
        public final double x;
        public int direction;

        public Intersection(double x, int direction) {
            this.x = x;
            this.direction = direction;
        }
    }
}
