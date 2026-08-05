package lovely.cane.jmsdfgen;

import static lovely.cane.jmsdfgen.Arithmetic.mix;

public abstract sealed class EdgeSegment
        permits
        EdgeSegment.LinearSegment,
        EdgeSegment.QuadraticSegment,
        EdgeSegment.CubicSegment {
    public static final int CUBIC_SEARCH_STARTS = 4;
    public static final int CUBIC_SEARCH_STEPS = 4;

    public EdgeColor color;

    protected EdgeSegment() {
        this(EdgeColor.WHITE);
    }

    protected EdgeSegment(EdgeColor edgeColor) {
        color = edgeColor;
    }

    public static EdgeSegment create(Vector2 p0, Vector2 p1, EdgeColor edgeColor) {
        return new LinearSegment(p0, p1, edgeColor);
    }

    public static EdgeSegment create(Vector2 p0, Vector2 p1, Vector2 p2, EdgeColor edgeColor) {
        if (Vector2.crossProduct(Vector2.subtract(p1, p0), Vector2.subtract(p2, p1)) == 0)
            return new LinearSegment(p0, p2, edgeColor);
        return new QuadraticSegment(p0, p1, p2, edgeColor);
    }

    public static EdgeSegment create(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3, EdgeColor edgeColor) {
        var p12 = Vector2.subtract(p2, p1);
        if (Vector2.crossProduct(Vector2.subtract(p1, p0), p12) == 0 &&
                Vector2.crossProduct(p12, Vector2.subtract(p3, p2)) == 0)
            return new LinearSegment(p0, p3, edgeColor);
        var reduced = Vector2.subtract(Vector2.multiply(1.5, p1), Vector2.multiply(0.5, p0));
        if (Vector2.equals(reduced, Vector2.subtract(Vector2.multiply(1.5, p2), Vector2.multiply(0.5, p3))))
            return new QuadraticSegment(p0, reduced, p3, edgeColor);
        return new CubicSegment(p0, p1, p2, p3, edgeColor);
    }

    public abstract EdgeSegment copy();

    public abstract int type();

    public abstract Vector2[] controlPoints();

    public abstract Vector2 point(double param);

    public abstract Vector2 direction(double param);

    public abstract Vector2 directionChange(double param);

    private Vector2 cachedStartPoint;
    private Vector2 cachedEndPoint;
    private Vector2 cachedStartDir;
    private Vector2 cachedEndDir;

    public Vector2 startPoint() {
        if (cachedStartPoint == null) cachedStartPoint = point(0);
        return cachedStartPoint;
    }

    public Vector2 endPoint() {
        if (cachedEndPoint == null) cachedEndPoint = point(1);
        return cachedEndPoint;
    }

    public Vector2 startDirection() {
        if (cachedStartDir == null) cachedStartDir = direction(0);
        return cachedStartDir;
    }

    public Vector2 endDirection() {
        if (cachedEndDir == null) cachedEndDir = direction(1);
        return cachedEndDir;
    }

    public abstract SignedDistance signedDistance(Vector2 origin, double[] param);

    public abstract int scanlineIntersections(double[] x, int[] dy, double y);

    public abstract void bound(double[] xMin, double[] yMin, double[] xMax, double[] yMax);

    public abstract void reverse();

    public abstract void moveStartPoint(Vector2 to);

    public abstract void moveEndPoint(Vector2 to);

    public abstract EdgeSegment[] splitInThirds();

    public void distanceToPerpendicularDistance(SignedDistance distance, Vector2 origin, double param) {
        if (param < 0) {
            var dir = startDirection().normalize();
            var aq = Vector2.subtract(origin, startPoint());
            var ts = Vector2.dotProduct(aq, dir);
            if (ts < 0) {
                var perpendicularDistance = Vector2.crossProduct(aq, dir);
                if (Math.abs(perpendicularDistance) <= Math.abs(distance.distance)) {
                    distance.distance = perpendicularDistance;
                    distance.dot = 0;
                }
            }
        } else if (param > 1) {
            var dir = endDirection().normalize();
            var bq = Vector2.subtract(origin, endPoint());
            var ts = Vector2.dotProduct(bq, dir);
            if (ts > 0) {
                var perpendicularDistance = Vector2.crossProduct(bq, dir);
                if (Math.abs(perpendicularDistance) <= Math.abs(distance.distance)) {
                    distance.distance = perpendicularDistance;
                    distance.dot = 0;
                }
            }
        }
    }

    public static final class LinearSegment extends EdgeSegment {
        public static final int EDGE_TYPE = 1;

        public final Vector2[] p;

        public LinearSegment(Vector2 p0, Vector2 p1, EdgeColor edgeColor) {
            super(edgeColor);
            p = new Vector2[]{p0, p1};
        }

        @Override
        public LinearSegment copy() {
            return new LinearSegment(p[0], p[1], color);
        }

        @Override
        public int type() {
            return EDGE_TYPE;
        }

        @Override
        public Vector2[] controlPoints() {
            return new Vector2[]{p[0], p[1]};
        }

        @Override
        public Vector2 point(double param) {
            return mix(p[0], p[1], param);
        }

        @Override
        public Vector2 direction(double param) {
            return Vector2.subtract(p[1], p[0]);
        }

        @Override
        public Vector2 directionChange(double param) {
            return new Vector2(0, 0);
        }

        public double length() {
            return Vector2.subtract(p[1], p[0]).length();
        }

        @Override
        public SignedDistance signedDistance(Vector2 origin, double[] param) {
            var aq = Vector2.subtract(origin, p[0]);
            var ab = Vector2.subtract(p[1], p[0]);
            param[0] = Vector2.dotProduct(aq, ab) / Vector2.dotProduct(ab, ab);
            var eq = Vector2.subtract(p[param[0] > 0.5 ? 1 : 0], origin);
            var endpointDistance = eq.length();
            if (param[0] > 0 && param[0] < 1) {
                var orthoDistance = Vector2.dotProduct(ab.getOrthonormal(false), aq);
                if (Math.abs(orthoDistance) < endpointDistance)
                    return new SignedDistance(orthoDistance, 0);
            }
            return new SignedDistance(
                    Arithmetic.nonZeroSign(Vector2.crossProduct(aq, ab)) * endpointDistance,
                    Math.abs(Vector2.dotProduct(ab.normalize(), eq.normalize()))
            );
        }

        @Override
        public int scanlineIntersections(double[] x, int[] dy, double y) {
            if ((y >= p[0].y && y < p[1].y) || (y >= p[1].y && y < p[0].y)) {
                var param = (y - p[0].y) / (p[1].y - p[0].y);
                x[0] = mix(p[0], p[1], param).x;
                dy[0] = Arithmetic.sign(p[1].y - p[0].y);
                return 1;
            }
            return 0;
        }

        @Override
        public void bound(double[] xMin, double[] yMin, double[] xMax, double[] yMax) {
            Arithmetic.boundPoint(xMin, yMin, xMax, yMax, p[0]);
            Arithmetic.boundPoint(xMin, yMin, xMax, yMax, p[1]);
        }

        @Override
        public void reverse() {
            var tmp = p[0];
            p[0] = p[1];
            p[1] = tmp;
        }

        @Override
        public void moveStartPoint(Vector2 to) {
            p[0] = to;
        }

        @Override
        public void moveEndPoint(Vector2 to) {
            p[1] = to;
        }

        @Override
        public EdgeSegment[] splitInThirds() {
            return new EdgeSegment[]{
                    new LinearSegment(p[0], point(1.0 / 3.0), color),
                    new LinearSegment(point(1.0 / 3.0), point(2.0 / 3.0), color),
                    new LinearSegment(point(2.0 / 3.0), p[1], color)
            };
        }
    }

    public static final class QuadraticSegment extends EdgeSegment {
        public static final int EDGE_TYPE = 2;

        public final Vector2[] p;

        public QuadraticSegment(Vector2 p0, Vector2 p1, Vector2 p2, EdgeColor edgeColor) {
            super(edgeColor);
            p = new Vector2[]{p0, p1, p2};
        }

        @Override
        public QuadraticSegment copy() {
            return new QuadraticSegment(p[0], p[1], p[2], color);
        }

        @Override
        public int type() {
            return EDGE_TYPE;
        }

        @Override
        public Vector2[] controlPoints() {
            return new Vector2[]{p[0], p[1], p[2]};
        }

        @Override
        public Vector2 point(double param) {
            return mix(mix(p[0], p[1], param), mix(p[1], p[2], param), param);
        }

        @Override
        public Vector2 direction(double param) {
            var tangent = mix(Vector2.subtract(p[1], p[0]), Vector2.subtract(p[2], p[1]), param);
            if (Vector2.isZero(tangent))
                return Vector2.subtract(p[2], p[0]);
            return tangent;
        }

        @Override
        public Vector2 directionChange(double param) {
            return Vector2.subtract(Vector2.subtract(p[2], p[1]), Vector2.subtract(p[1], p[0]));
        }

        public double length() {
            var ab = Vector2.subtract(p[1], p[0]);
            var br = Vector2.subtract(Vector2.subtract(p[2], p[1]), ab);
            var abab = Vector2.dotProduct(ab, ab);
            var abbr = Vector2.dotProduct(ab, br);
            var brbr = Vector2.dotProduct(br, br);
            var abLen = Math.sqrt(abab);
            var brLen = Math.sqrt(brbr);
            var crs = Vector2.crossProduct(ab, br);
            var h = Math.sqrt(abab + abbr + abbr + brbr);
            return (
                    brLen * ((abbr + brbr) * h - abbr * abLen) +
                            crs * crs * Math.log((brLen * h + abbr + brbr) / (brLen * abLen + abbr))
            ) / (brbr * brLen);
        }

        @Override
        public SignedDistance signedDistance(Vector2 origin, double[] param) {
            var originX = origin.x;
            var originY = origin.y;
            var qaX = p[0].x - originX;
            var qaY = p[0].y - originY;
            var abX = p[1].x - p[0].x;
            var abY = p[1].y - p[0].y;
            var brX = (p[2].x - p[1].x) - abX;
            var brY = (p[2].y - p[1].y) - abY;
            var a = brX * brX + brY * brY;
            var b = 3 * (abX * brX + abY * brY);
            var c = 2 * (abX * abX + abY * abY) + (qaX * brX + qaY * brY);
            var d = qaX * abX + qaY * abY;
            var t = new double[3];
            var solutions = EquationSolver.solveCubic(t, a, b, c, d);

            var epDir = startDirection();
            var epDirX = epDir.x;
            var epDirY = epDir.y;
            var minDistance = Arithmetic.nonZeroSign(epDirX * qaY - epDirY * qaX) * Math.sqrt(qaX * qaX + qaY * qaY);
            param[0] = -(qaX * epDirX + qaY * epDirY) / (epDirX * epDirX + epDirY * epDirY);
            {
                var dx = p[2].x - originX;
                var dy = p[2].y - originY;
                var distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < Math.abs(minDistance)) {
                    epDir = endDirection();
                    epDirX = epDir.x;
                    epDirY = epDir.y;
                    minDistance = Arithmetic.nonZeroSign(epDirX * dy - epDirY * dx) * distance;
                    param[0] = ((originX - p[1].x) * epDirX + (originY - p[1].y) * epDirY) / (epDirX * epDirX + epDirY * epDirY);
                }
            }
            for (var i = 0; i < solutions; ++i) {
                if (t[i] > 0 && t[i] < 1) {
                    var qeX = qaX + 2 * t[i] * abX + t[i] * t[i] * brX;
                    var qeY = qaY + 2 * t[i] * abY + t[i] * t[i] * brY;
                    var distance = Math.sqrt(qeX * qeX + qeY * qeY);
                    if (distance <= Math.abs(minDistance)) {
                        minDistance = Arithmetic.nonZeroSign((abX + t[i] * brX) * qeY - (abY + t[i] * brY) * qeX) * distance;
                        param[0] = t[i];
                    }
                }
            }

            if (param[0] >= 0 && param[0] <= 1)
                return new SignedDistance(minDistance, 0);
            if (param[0] < 0.5)
                return new SignedDistance(minDistance, Math.abs(Vector2.dotProduct(startDirection().normalize(), new Vector2(qaX, qaY).normalize())));
            else
                return new SignedDistance(minDistance, Math.abs(Vector2.dotProduct(endDirection().normalize(), Vector2.subtract(p[2], origin).normalize())));
        }

        @Override
        public int scanlineIntersections(double[] x, int[] dy, double y) {
            var total = 0;
            var nextDY = y > p[0].y ? 1 : -1;
            x[total] = p[0].x;
            if (p[0].y == y) {
                if (p[0].y < p[1].y || (p[0].y == p[1].y && p[0].y < p[2].y))
                    dy[total++] = 1;
                else
                    nextDY = 1;
            }
            {
                var ab = Vector2.subtract(p[1], p[0]);
                var br = Vector2.subtract(Vector2.subtract(p[2], p[1]), ab);
                var t = new double[2];
                var solutions = EquationSolver.solveQuadratic(t, br.y, 2 * ab.y, p[0].y - y);
                if (solutions >= 2 && t[0] > t[1]) {
                    var tmp = t[0];
                    t[0] = t[1];
                    t[1] = tmp;
                }
                for (var i = 0; i < solutions && total < 2; ++i) {
                    if (t[i] >= 0 && t[i] <= 1) {
                        x[total] = p[0].x + 2 * t[i] * ab.x + t[i] * t[i] * br.x;
                        if (nextDY * (ab.y + t[i] * br.y) >= 0) {
                            dy[total++] = nextDY;
                            nextDY = -nextDY;
                        }
                    }
                }
            }
            if (p[2].y == y) {
                if (nextDY > 0 && total > 0) {
                    --total;
                    nextDY = -1;
                }
                if ((p[2].y < p[1].y || (p[2].y == p[1].y && p[2].y < p[0].y)) && total < 2) {
                    x[total] = p[2].x;
                    if (nextDY < 0) {
                        dy[total++] = -1;
                        nextDY = 1;
                    }
                }
            }
            if (nextDY != (y >= p[2].y ? 1 : -1)) {
                if (total > 0)
                    --total;
                else {
                    if (Math.abs(p[2].y - y) < Math.abs(p[0].y - y))
                        x[total] = p[2].x;
                    dy[total++] = nextDY;
                }
            }
            return total;
        }

        @Override
        public void bound(double[] xMin, double[] yMin, double[] xMax, double[] yMax) {
            Arithmetic.boundPoint(xMin, yMin, xMax, yMax, p[0]);
            Arithmetic.boundPoint(xMin, yMin, xMax, yMax, p[2]);
            var bot = Vector2.subtract(Vector2.subtract(p[1], p[0]), Vector2.subtract(p[2], p[1]));
            if (bot.x != 0) {
                var param = (p[1].x - p[0].x) / bot.x;
                if (param > 0 && param < 1)
                    Arithmetic.boundPoint(xMin, yMin, xMax, yMax, point(param));
            }
            if (bot.y != 0) {
                var param = (p[1].y - p[0].y) / bot.y;
                if (param > 0 && param < 1)
                    Arithmetic.boundPoint(xMin, yMin, xMax, yMax, point(param));
            }
        }

        @Override
        public void reverse() {
            var tmp = p[0];
            p[0] = p[2];
            p[2] = tmp;
        }

        @Override
        public void moveStartPoint(Vector2 to) {
            var origSDir = Vector2.subtract(p[0], p[1]);
            var origP1 = p[1];
            var factor = Vector2.crossProduct(Vector2.subtract(p[0], p[1]), Vector2.subtract(to, p[0])) /
                    Vector2.crossProduct(Vector2.subtract(p[0], p[1]), Vector2.subtract(p[2], p[1]));
            p[1] = Vector2.add(p[1], Vector2.multiply(factor, Vector2.subtract(p[2], p[1])));
            p[0] = to;
            if (Vector2.dotProduct(origSDir, Vector2.subtract(p[0], p[1])) < 0)
                p[1] = origP1;
        }

        @Override
        public void moveEndPoint(Vector2 to) {
            var origEDir = Vector2.subtract(p[2], p[1]);
            var origP1 = p[1];
            var factor = Vector2.crossProduct(Vector2.subtract(p[2], p[1]), Vector2.subtract(to, p[2])) /
                    Vector2.crossProduct(Vector2.subtract(p[2], p[1]), Vector2.subtract(p[0], p[1]));
            p[1] = Vector2.add(p[1], Vector2.multiply(factor, Vector2.subtract(p[0], p[1])));
            p[2] = to;
            if (Vector2.dotProduct(origEDir, Vector2.subtract(p[2], p[1])) < 0)
                p[1] = origP1;
        }

        @Override
        public EdgeSegment[] splitInThirds() {
            return new EdgeSegment[]{
                    new QuadraticSegment(p[0], mix(p[0], p[1], 1.0 / 3.0), point(1.0 / 3.0), color),
                    new QuadraticSegment(point(1.0 / 3.0),
                            mix(mix(p[0], p[1], 5.0 / 9.0), mix(p[1], p[2], 4.0 / 9.0), 0.5),
                            point(2.0 / 3.0), color),
                    new QuadraticSegment(point(2.0 / 3.0), mix(p[1], p[2], 2.0 / 3.0), p[2], color)
            };
        }

        public CubicSegment convertToCubic() {
            return new CubicSegment(p[0], mix(p[0], p[1], 2.0 / 3.0), mix(p[1], p[2], 1.0 / 3.0), p[2], color);
        }
    }

    public static final class CubicSegment extends EdgeSegment {
        public static final int EDGE_TYPE = 3;

        public final Vector2[] p;

        public CubicSegment(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3, EdgeColor edgeColor) {
            super(edgeColor);
            p = new Vector2[]{p0, p1, p2, p3};
        }

        @Override
        public CubicSegment copy() {
            return new CubicSegment(p[0], p[1], p[2], p[3], color);
        }

        @Override
        public int type() {
            return EDGE_TYPE;
        }

        @Override
        public Vector2[] controlPoints() {
            return new Vector2[]{p[0], p[1], p[2], p[3]};
        }

        @Override
        public Vector2 point(double param) {
            var p12 = mix(p[1], p[2], param);
            return mix(mix(mix(p[0], p[1], param), p12, param), mix(p12, mix(p[2], p[3], param), param), param);
        }

        @Override
        public Vector2 direction(double param) {
            var tangent = mix(
                    mix(Vector2.subtract(p[1], p[0]), Vector2.subtract(p[2], p[1]), param),
                    mix(Vector2.subtract(p[2], p[1]), Vector2.subtract(p[3], p[2]), param),
                    param
            );
            if (Vector2.isZero(tangent)) {
                if (param == 0) return Vector2.subtract(p[2], p[0]);
                if (param == 1) return Vector2.subtract(p[3], p[1]);
            }
            return tangent;
        }

        @Override
        public Vector2 directionChange(double param) {
            return mix(
                    Vector2.subtract(Vector2.subtract(p[2], p[1]), Vector2.subtract(p[1], p[0])),
                    Vector2.subtract(Vector2.subtract(p[3], p[2]), Vector2.subtract(p[2], p[1])),
                    param
            );
        }

        @Override
        public SignedDistance signedDistance(Vector2 origin, double[] param) {
            var originX = origin.x;
            var originY = origin.y;
            var qaX = p[0].x - originX;
            var qaY = p[0].y - originY;
            var abX = p[1].x - p[0].x;
            var abY = p[1].y - p[0].y;
            var brX = (p[2].x - p[1].x) - abX;
            var brY = (p[2].y - p[1].y) - abY;
            var asX = ((p[3].x - p[2].x) - (p[2].x - p[1].x)) - brX;
            var asY = ((p[3].y - p[2].y) - (p[2].y - p[1].y)) - brY;

            var epDir = startDirection();
            var epDirX = epDir.x;
            var epDirY = epDir.y;
            var minDistance = Arithmetic.nonZeroSign(epDirX * qaY - epDirY * qaX) * Math.sqrt(qaX * qaX + qaY * qaY);
            param[0] = -(qaX * epDirX + qaY * epDirY) / (epDirX * epDirX + epDirY * epDirY);
            {
                var dx = p[3].x - originX;
                var dy = p[3].y - originY;
                var distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < Math.abs(minDistance)) {
                    epDir = endDirection();
                    epDirX = epDir.x;
                    epDirY = epDir.y;
                    minDistance = Arithmetic.nonZeroSign(epDirX * dy - epDirY * dx) * distance;
                    param[0] = ((epDirX - dx) * epDirX + (epDirY - dy) * epDirY) / (epDirX * epDirX + epDirY * epDirY);
                }
            }

            for (var i = 0; i <= CUBIC_SEARCH_STARTS; ++i) {
                var t = 1.0 / CUBIC_SEARCH_STARTS * i;
                var qeX = qaX + 3 * t * abX + 3 * t * t * brX + t * t * t * asX;
                var qeY = qaY + 3 * t * abY + 3 * t * t * brY + t * t * t * asY;
                var d1X = 3 * abX + 6 * t * brX + 3 * t * t * asX;
                var d1Y = 3 * abY + 6 * t * brY + 3 * t * t * asY;
                var d2X = 6 * brX + 6 * t * asX;
                var d2Y = 6 * brY + 6 * t * asY;
                var improvedT = t - (qeX * d1X + qeY * d1Y) / (d1X * d1X + d1Y * d1Y + qeX * d2X + qeY * d2Y);
                if (improvedT > 0 && improvedT < 1) {
                    var remainingSteps = CUBIC_SEARCH_STEPS;
                    do {
                        t = improvedT;
                        qeX = qaX + 3 * t * abX + 3 * t * t * brX + t * t * t * asX;
                        qeY = qaY + 3 * t * abY + 3 * t * t * brY + t * t * t * asY;
                        d1X = 3 * abX + 6 * t * brX + 3 * t * t * asX;
                        d1Y = 3 * abY + 6 * t * brY + 3 * t * t * asY;
                        if (--remainingSteps == 0) break;
                        d2X = 6 * brX + 6 * t * asX;
                        d2Y = 6 * brY + 6 * t * asY;
                        improvedT = t - (qeX * d1X + qeY * d1Y) / (d1X * d1X + d1Y * d1Y + qeX * d2X + qeY * d2Y);
                    } while (improvedT > 0 && improvedT < 1);
                    var distance = Math.sqrt(qeX * qeX + qeY * qeY);
                    if (distance < Math.abs(minDistance)) {
                        minDistance = Arithmetic.nonZeroSign(d1X * qeY - d1Y * qeX) * distance;
                        param[0] = t;
                    }
                }
            }

            if (param[0] >= 0 && param[0] <= 1)
                return new SignedDistance(minDistance, 0);
            if (param[0] < 0.5)
                return new SignedDistance(minDistance, Math.abs(Vector2.dotProduct(startDirection().normalize(), new Vector2(qaX, qaY).normalize())));
            else
                return new SignedDistance(minDistance, Math.abs(Vector2.dotProduct(endDirection().normalize(), Vector2.subtract(p[3], origin).normalize())));
        }

        @Override
        public int scanlineIntersections(double[] x, int[] dy, double y) {
            var total = 0;
            var nextDY = y > p[0].y ? 1 : -1;
            x[total] = p[0].x;
            if (p[0].y == y) {
                if (p[0].y < p[1].y || (p[0].y == p[1].y && (p[0].y < p[2].y || (p[0].y == p[2].y && p[0].y < p[3].y))))
                    dy[total++] = 1;
                else
                    nextDY = 1;
            }
            {
                var ab = Vector2.subtract(p[1], p[0]);
                var br = Vector2.subtract(Vector2.subtract(p[2], p[1]), ab);
                var as = Vector2.subtract(Vector2.subtract(Vector2.subtract(p[3], p[2]), Vector2.subtract(p[2], p[1])), br);
                var t = new double[3];
                var solutions = EquationSolver.solveCubic(t, as.y, 3 * br.y, 3 * ab.y, p[0].y - y);
                if (solutions >= 2) {
                    if (t[0] > t[1]) {
                        var tmp = t[0];
                        t[0] = t[1];
                        t[1] = tmp;
                    }
                    if (solutions >= 3 && t[1] > t[2]) {
                        var tmp = t[1];
                        t[1] = t[2];
                        t[2] = tmp;
                        if (t[0] > t[1]) {
                            tmp = t[0];
                            t[0] = t[1];
                            t[1] = tmp;
                        }
                    }
                }
                for (var i = 0; i < solutions && total < 3; ++i) {
                    if (t[i] >= 0 && t[i] <= 1) {
                        x[total] = p[0].x + 3 * t[i] * ab.x + 3 * t[i] * t[i] * br.x + t[i] * t[i] * t[i] * as.x;
                        if (nextDY * (ab.y + 2 * t[i] * br.y + t[i] * t[i] * as.y) >= 0) {
                            dy[total++] = nextDY;
                            nextDY = -nextDY;
                        }
                    }
                }
            }
            if (p[3].y == y) {
                if (nextDY > 0 && total > 0) {
                    --total;
                    nextDY = -1;
                }
                if ((p[3].y < p[2].y || (p[3].y == p[2].y && (p[3].y < p[1].y || (p[3].y == p[1].y && p[3].y < p[0].y)))) && total < 3) {
                    x[total] = p[3].x;
                    if (nextDY < 0) {
                        dy[total++] = -1;
                        nextDY = 1;
                    }
                }
            }
            if (nextDY != (y >= p[3].y ? 1 : -1)) {
                if (total > 0)
                    --total;
                else {
                    if (Math.abs(p[3].y - y) < Math.abs(p[0].y - y))
                        x[total] = p[3].x;
                    dy[total++] = nextDY;
                }
            }
            return total;
        }

        @Override
        public void bound(double[] xMin, double[] yMin, double[] xMax, double[] yMax) {
            Arithmetic.boundPoint(xMin, yMin, xMax, yMax, p[0]);
            Arithmetic.boundPoint(xMin, yMin, xMax, yMax, p[3]);
            var a0 = Vector2.subtract(p[1], p[0]);
            var a1 = Vector2.multiply(2, Vector2.subtract(Vector2.subtract(p[2], p[1]), a0));
            var a2 = Vector2.subtract(Vector2.add(Vector2.subtract(p[3], Vector2.multiply(3, p[2])), Vector2.multiply(3, p[1])), p[0]);
            var params = new double[2];
            var solutions = EquationSolver.solveQuadratic(params, a2.x, a1.x, a0.x);
            for (var i = 0; i < solutions; ++i)
                if (params[i] > 0 && params[i] < 1)
                    Arithmetic.boundPoint(xMin, yMin, xMax, yMax, point(params[i]));
            solutions = EquationSolver.solveQuadratic(params, a2.y, a1.y, a0.y);
            for (var i = 0; i < solutions; ++i)
                if (params[i] > 0 && params[i] < 1)
                    Arithmetic.boundPoint(xMin, yMin, xMax, yMax, point(params[i]));
        }

        @Override
        public void reverse() {
            var tmp = p[0];
            p[0] = p[3];
            p[3] = tmp;
            tmp = p[1];
            p[1] = p[2];
            p[2] = tmp;
        }

        @Override
        public void moveStartPoint(Vector2 to) {
            p[1] = Vector2.add(p[1], Vector2.subtract(to, p[0]));
            p[0] = to;
        }

        @Override
        public void moveEndPoint(Vector2 to) {
            p[2] = Vector2.add(p[2], Vector2.subtract(to, p[3]));
            p[3] = to;
        }

        @Override
        public EdgeSegment[] splitInThirds() {
            var p0 = p[0];
            var p1 = p[1];
            var p2 = p[2];
            var p3 = p[3];
            var p01_1_3 = Vector2.equals(p0, p1) ? p0 : mix(p0, p1, 1.0 / 3.0);
            var p12_1_3 = mix(p1, p2, 1.0 / 3.0);
            var p23_1_3 = mix(p2, p3, 1.0 / 3.0);
            var mid1 = mix(p01_1_3, p12_1_3, 1.0 / 3.0);
            var p1_3 = point(1.0 / 3.0);

            var p12_2_3 = mix(p1, p2, 2.0 / 3.0);
            var p23_2_3 = mix(p2, p3, 2.0 / 3.0);
            var mid2_first = mix(mix(p0, p1, 2.0 / 3.0), p12_2_3, 2.0 / 3.0);
            var mid2_second = mix(p12_2_3, p23_2_3, 2.0 / 3.0);
            var p2_3 = point(2.0 / 3.0);

            return new EdgeSegment[]{
                    new CubicSegment(p0, p01_1_3, mid1, p1_3, color),
                    new CubicSegment(p1_3, mix(mid1, mix(p12_1_3, p23_1_3, 1.0 / 3.0), 2.0 / 3.0),
                            mix(mid2_first, mid2_second, 1.0 / 3.0), p2_3, color),
                    new CubicSegment(p2_3, mix(p12_2_3, p23_2_3, 2.0 / 3.0),
                            Vector2.equals(p2, p3) ? p3 : mix(p2, p3, 2.0 / 3.0), p3, color)
            };
        }
    }
}
