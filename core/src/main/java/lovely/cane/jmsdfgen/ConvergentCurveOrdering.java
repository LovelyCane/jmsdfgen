package lovely.cane.jmsdfgen;

public class ConvergentCurveOrdering {
    public static int convergentCurveOrdering(EdgeSegment a, EdgeSegment b) {
        var aPoints = a.controlPoints();
        var bPoints = b.controlPoints();
        var aOrder = a.type();
        var bOrder = b.type();
        if (!(aOrder >= 1 && aOrder <= 3 && bOrder >= 1 && bOrder <= 3)) {
            return 0;
        }
        if (!Vector2.equals(aPoints[aOrder], bPoints[0])) {
            return 0;
        }
        var aCopy = new Vector2[aOrder + 1];
        System.arraycopy(aPoints, 0, aCopy, 0, aOrder + 1);
        var bCopy = new Vector2[bOrder + 1];
        System.arraycopy(bPoints, 0, bCopy, 0, bOrder + 1);
        aOrder = simplifyDegenerateCurve(aCopy, aOrder);
        bOrder = simplifyDegenerateCurve(bCopy, bOrder);
        var allPoints = new Vector2[aOrder + 1 + bOrder];
        System.arraycopy(aCopy, 0, allPoints, 0, aOrder + 1);
        System.arraycopy(bCopy, 1, allPoints, aOrder + 1, bOrder);
        var cornerIndex = aOrder;
        return convergentCurveOrdering(allPoints, cornerIndex, aOrder, bOrder);
    }

    private static int convergentCurveOrdering(Vector2[] pts, int cornerIdx, int controlPointsBefore, int controlPointsAfter) {
        if (!(controlPointsBefore > 0 && controlPointsAfter > 0)) {
            return 0;
        }
        var a1 = Vector2.subtract(pts[cornerIdx - 1], pts[cornerIdx]);
        var b1 = Vector2.subtract(pts[cornerIdx + 1], pts[cornerIdx]);
        var a2 = new Vector2();
        var a3 = new Vector2();
        var b2 = new Vector2();
        var b3 = new Vector2();
        if (controlPointsBefore >= 2) {
            a2 = Vector2.subtract(Vector2.subtract(pts[cornerIdx - 2], pts[cornerIdx - 1]), a1);
        }
        if (controlPointsAfter >= 2) {
            b2 = Vector2.subtract(Vector2.subtract(pts[cornerIdx + 2], pts[cornerIdx + 1]), b1);
        }
        if (controlPointsBefore >= 3) {
            var diff1 = Vector2.subtract(pts[cornerIdx - 3], pts[cornerIdx - 2]);
            var diff2 = Vector2.subtract(pts[cornerIdx - 2], pts[cornerIdx - 1]);
            a3 = Vector2.subtract(Vector2.subtract(diff1, diff2), a2);
            a2 = Vector2.multiply(a2, 3);
        }
        if (controlPointsAfter >= 3) {
            var diff1 = Vector2.subtract(pts[cornerIdx + 3], pts[cornerIdx + 2]);
            var diff2 = Vector2.subtract(pts[cornerIdx + 2], pts[cornerIdx + 1]);
            b3 = Vector2.subtract(Vector2.subtract(diff1, diff2), b2);
            b2 = Vector2.multiply(b2, 3);
        }
        a1 = Vector2.multiply(a1, controlPointsBefore);
        b1 = Vector2.multiply(b1, controlPointsAfter);
        if (a1.isNonZero() && b1.isNonZero()) {
            var as = a1.length();
            var bs = b1.length();
            var d = as * Vector2.crossProduct(a1, b2) + bs * Vector2.crossProduct(a2, b1);
            if (d != 0) {
                return Arithmetic.sign(d);
            }
            d = as * as * Vector2.crossProduct(a1, b3) + as * bs * Vector2.crossProduct(a2, b2) + bs * bs * Vector2.crossProduct(a3, b1);
            if (d != 0) {
                return Arithmetic.sign(d);
            }
            d = as * Vector2.crossProduct(a2, b3) + bs * Vector2.crossProduct(a3, b2);
            if (d != 0) {
                return Arithmetic.sign(d);
            }
            return Arithmetic.sign(Vector2.crossProduct(a3, b3));
        }
        var s = 1;
        if (a1.isNonZero()) {
            b1 = a1;
            a1 = b2;
            b2 = a2;
            a2 = a1;
            a1 = b3;
            b3 = a3;
            a3 = a1;
            s = -1;
        }
        if (b1.isNonZero()) {
            var d = Vector2.crossProduct(a3, b1);
            if (d != 0) {
                return s * Arithmetic.sign(d);
            }
            d = Vector2.crossProduct(a2, b2);
            if (d != 0) {
                return s * Arithmetic.sign(d);
            }
            d = Vector2.crossProduct(a3, b2);
            if (d != 0) {
                return s * Arithmetic.sign(d);
            }
            d = Vector2.crossProduct(a2, b3);
            if (d != 0) {
                return s * Arithmetic.sign(d);
            }
            return s * Arithmetic.sign(Vector2.crossProduct(a3, b3));
        }
        var d = Math.sqrt(a2.length()) * Vector2.crossProduct(a2, b3) + Math.sqrt(b2.length()) * Vector2.crossProduct(a3, b2);
        if (d != 0) {
            return Arithmetic.sign(d);
        }
        return Arithmetic.sign(Vector2.crossProduct(a3, b3));
    }

    private static int simplifyDegenerateCurve(Vector2[] controlPoints, int order) {
        if (order == 3) {
            var cond1 = Vector2.equals(controlPoints[1], controlPoints[0]) || Vector2.equals(controlPoints[1], controlPoints[3]);
            var cond2 = Vector2.equals(controlPoints[2], controlPoints[0]) || Vector2.equals(controlPoints[2], controlPoints[3]);
            if (cond1 && cond2) {
                controlPoints[1] = controlPoints[3];
                order = 1;
            }
        }
        if (order == 2) {
            if (Vector2.equals(controlPoints[1], controlPoints[0]) || Vector2.equals(controlPoints[1], controlPoints[2])) {
                controlPoints[1] = controlPoints[2];
                order = 1;
            }
        }
        if (order == 1 && Vector2.equals(controlPoints[0], controlPoints[1])) {
            order = 0;
        }
        return order;
    }
}
