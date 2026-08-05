package lovely.cane.jmsdfgen;

import org.jspecify.annotations.Nullable;

public sealed interface EdgeSelector<D, C, S extends EdgeSelector<D, C, S>>
        permits
        EdgeSelector.TrueDistanceSelector,
        EdgeSelector.MultiDistanceSelector,
        EdgeSelector.PerpendicularDistanceSelector,
        EdgeSelector.MultiAndTrueDistanceSelector {
    void reset(Vector2 p);

    void addEdge(C cache, EdgeSegment prev, EdgeSegment edge, EdgeSegment next, EdgeColor color);

    void merge(S other);

    D distance();

    final class TrueDistanceSelector implements EdgeSelector<Double, TrueDistanceSelector.EdgeCache, TrueDistanceSelector> {
        private static final double DISTANCE_DELTA_FACTOR = 1.001;

        private Vector2 p = new Vector2();
        private SignedDistance minDistance = new SignedDistance();

        @Override
        public void reset(Vector2 p) {
            var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, this.p).length();
            minDistance.distance += Arithmetic.nonZeroSign(minDistance.distance) * delta;
            this.p = p;
        }

        @Override
        public void addEdge(EdgeCache cache, EdgeSegment prev, EdgeSegment edge, EdgeSegment next, EdgeColor color) {
            var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, cache.point).length();
            if (cache.absDistance - delta <= Math.abs(minDistance.distance)) {
                var paramOut = new double[1];
                var distance = edge.signedDistance(p, paramOut);
                if (distance.compareTo(minDistance) < 0) {
                    minDistance = distance;
                }
                cache.point = new Vector2(p.x, p.y);
                cache.absDistance = Math.abs(distance.distance);
            }
        }

        @Override
        public void merge(TrueDistanceSelector other) {
            if (other.minDistance.compareTo(minDistance) < 0) {
                minDistance = other.minDistance;
            }
        }

        @Override
        public Double distance() {
            return minDistance.distance;
        }

        public static class EdgeCache {
            public Vector2 point = new Vector2();
            public double absDistance;
        }
    }

    class PerpendicularDistanceSelectorBase {
        public SignedDistance minTrueDistance = new SignedDistance();
        public double minNegativePerpendicularDistance;
        public double minPositivePerpendicularDistance;
        public @Nullable EdgeSegment nearEdge;
        public double nearEdgeParam;

        public PerpendicularDistanceSelectorBase() {
            minNegativePerpendicularDistance = -Math.abs(minTrueDistance.distance);
            minPositivePerpendicularDistance = Math.abs(minTrueDistance.distance);
        }

        public void reset(double delta) {
            minTrueDistance.distance += Arithmetic.nonZeroSign(minTrueDistance.distance) * delta;
            minNegativePerpendicularDistance = -Math.abs(minTrueDistance.distance);
            minPositivePerpendicularDistance = Math.abs(minTrueDistance.distance);
            nearEdge = null;
            nearEdgeParam = 0.0;
        }

        public boolean isEdgeRelevant(EdgeCache cache, Vector2 p) {
            var delta = 1.001 * Vector2.subtract(p, cache.point).length();
            return isEdgeRelevant(cache, p, delta);
        }

        public boolean isEdgeRelevant(EdgeCache cache, Vector2 p, double delta) {
            return cache.absDistance - delta <= Math.abs(minTrueDistance.distance)
                    || Math.abs(cache.aDomainDistance) < delta
                    || Math.abs(cache.bDomainDistance) < delta
                    || (cache.aDomainDistance > 0.0
                    && ((cache.aPerpendicularDistance < 0.0)
                    ? cache.aPerpendicularDistance + delta >= minNegativePerpendicularDistance
                    : cache.aPerpendicularDistance - delta <= minPositivePerpendicularDistance))
                    || (cache.bDomainDistance > 0.0
                    && ((cache.bPerpendicularDistance < 0.0)
                    ? cache.bPerpendicularDistance + delta >= minNegativePerpendicularDistance
                    : cache.bPerpendicularDistance - delta <= minPositivePerpendicularDistance));
        }

        public void addEdgeTrueDistance(EdgeSegment edge, SignedDistance distance, double param) {
            if (distance.compareTo(minTrueDistance) < 0) {
                minTrueDistance = distance;
                nearEdge = edge;
                nearEdgeParam = param;
            }
        }

        public void addEdgePerpendicularDistance(double distance) {
            if (distance <= 0.0 && distance > minNegativePerpendicularDistance) {
                minNegativePerpendicularDistance = distance;
            }
            if (distance >= 0.0 && distance < minPositivePerpendicularDistance) {
                minPositivePerpendicularDistance = distance;
            }
        }

        public void merge(PerpendicularDistanceSelectorBase other) {
            if (other.minTrueDistance.compareTo(minTrueDistance) < 0) {
                minTrueDistance = new SignedDistance(other.minTrueDistance.distance, other.minTrueDistance.dot);
                nearEdge = other.nearEdge;
                nearEdgeParam = other.nearEdgeParam;
            }
            if (other.minNegativePerpendicularDistance > minNegativePerpendicularDistance) {
                minNegativePerpendicularDistance = other.minNegativePerpendicularDistance;
            }
            if (other.minPositivePerpendicularDistance < minPositivePerpendicularDistance) {
                minPositivePerpendicularDistance = other.minPositivePerpendicularDistance;
            }
        }

        public double computeDistance(Vector2 p) {
            var minDistance = minTrueDistance.distance < 0.0
                    ? minNegativePerpendicularDistance
                    : minPositivePerpendicularDistance;
            if (nearEdge != null) {
                var distance = new SignedDistance(minTrueDistance.distance, minTrueDistance.dot);
                nearEdge.distanceToPerpendicularDistance(distance, p, nearEdgeParam);
                if (Math.abs(distance.distance) < Math.abs(minDistance)) {
                    minDistance = distance.distance;
                }
            }
            return minDistance;
        }

        public SignedDistance trueDistance() {
            return minTrueDistance;
        }

        public static boolean getPerpendicularDistance(double[] distanceHolder, Vector2 ep, Vector2 edgeDir) {
            var ts = Vector2.dotProduct(ep, edgeDir);
            if (ts > 0.0) {
                var perpendicularDistance = Vector2.crossProduct(ep, edgeDir);
                if (Math.abs(perpendicularDistance) < Math.abs(distanceHolder[0])) {
                    distanceHolder[0] = perpendicularDistance;
                    return true;
                }
            }
            return false;
        }

        public static class EdgeCache {
            public Vector2 point = new Vector2();
            public double absDistance;
            public double aDomainDistance;
            public double bDomainDistance;
            public double aPerpendicularDistance;
            public double bPerpendicularDistance;

            private boolean geometryReady;
            private Vector2 startPoint;
            private Vector2 endPoint;
            private Vector2 startDirN;
            private Vector2 endDirN;
            private Vector2 negStartDirN;
            private Vector2 startBisectorN;
            private Vector2 endBisectorN;

            public void ensureGeometry(EdgeSegment prev, EdgeSegment edge, EdgeSegment next) {
                if (geometryReady) return;
                startPoint = edge.startPoint();
                endPoint = edge.endPoint();
                startDirN = edge.startDirection().normalize(true);
                endDirN = edge.endDirection().normalize(true);
                negStartDirN = Vector2.negate(startDirN);
                var prevDirN = prev.endDirection().normalize(true);
                var nextDirN = next.startDirection().normalize(true);
                startBisectorN = Vector2.add(prevDirN, startDirN).normalize(true);
                endBisectorN = Vector2.add(endDirN, nextDirN).normalize(true);
                geometryReady = true;
            }
        }
    }

    final class PerpendicularDistanceSelector extends PerpendicularDistanceSelectorBase implements EdgeSelector<Double, PerpendicularDistanceSelectorBase.EdgeCache, PerpendicularDistanceSelector> {
        private static final double DISTANCE_DELTA_FACTOR = 1.001;

        private Vector2 p = new Vector2();

        @Override
        public void reset(Vector2 p) {
            var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, this.p).length();
            reset(delta);
            this.p = p;
        }

        @Override
        public void addEdge(EdgeCache cache, EdgeSegment prev, EdgeSegment edge, EdgeSegment next, EdgeColor color) {
            if (isEdgeRelevant(cache, p)) {
                cache.ensureGeometry(prev, edge, next);
                var paramOut = new double[1];
                var distance = edge.signedDistance(p, paramOut);
                var param = paramOut[0];
                addEdgeTrueDistance(edge, distance, param);
                cache.point = p;
                cache.absDistance = Math.abs(distance.distance);

                var ap = Vector2.subtract(p, cache.startPoint);
                var bp = Vector2.subtract(p, cache.endPoint);
                var add = Vector2.dotProduct(ap, cache.startBisectorN);
                var bdd = -Vector2.dotProduct(bp, cache.endBisectorN);
                if (add > 0.0) {
                    var pdHolder = new double[]{distance.distance};
                    if (PerpendicularDistanceSelectorBase.getPerpendicularDistance(pdHolder, ap, cache.negStartDirN)) {
                        pdHolder[0] = -pdHolder[0];
                        addEdgePerpendicularDistance(pdHolder[0]);
                    }
                    cache.aPerpendicularDistance = pdHolder[0];
                }
                if (bdd > 0.0) {
                    var pdHolder = new double[]{distance.distance};
                    if (PerpendicularDistanceSelectorBase.getPerpendicularDistance(pdHolder, bp, cache.endDirN)) {
                        addEdgePerpendicularDistance(pdHolder[0]);
                    }
                    cache.bPerpendicularDistance = pdHolder[0];
                }
                cache.aDomainDistance = add;
                cache.bDomainDistance = bdd;
            }
        }

        @Override
        public void merge(PerpendicularDistanceSelector other) {
            super.merge(other);
        }

        @Override
        public Double distance() {
            return computeDistance(p);
        }
    }

    final class MultiDistanceSelector implements EdgeSelector<MultiDistance, PerpendicularDistanceSelectorBase.EdgeCache, MultiDistanceSelector> {
        private static final double DISTANCE_DELTA_FACTOR = 1.001;

        private Vector2 p = new Vector2();
        private final PerpendicularDistanceSelectorBase r = new PerpendicularDistanceSelectorBase();
        private final PerpendicularDistanceSelectorBase g = new PerpendicularDistanceSelectorBase();
        private final PerpendicularDistanceSelectorBase b = new PerpendicularDistanceSelectorBase();

        @Override
        public void reset(Vector2 p) {
            var delta = DISTANCE_DELTA_FACTOR * Vector2.subtract(p, this.p).length();
            r.reset(delta);
            g.reset(delta);
            b.reset(delta);
            this.p = p;
        }

        @Override
        public void addEdge(PerpendicularDistanceSelectorBase.EdgeCache cache, EdgeSegment prev, EdgeSegment edge, EdgeSegment next, EdgeColor color) {
            var delta = 1.001 * Vector2.subtract(p, cache.point).length();
            if ((color.has(EdgeColor.RED) && r.isEdgeRelevant(cache, p, delta)) ||
                    (color.has(EdgeColor.GREEN) && g.isEdgeRelevant(cache, p, delta)) ||
                    (color.has(EdgeColor.BLUE) && b.isEdgeRelevant(cache, p, delta))) {
                cache.ensureGeometry(prev, edge, next);
                var paramOut = new double[1];
                var distance = edge.signedDistance(p, paramOut);
                var param = paramOut[0];
                if (color.has(EdgeColor.RED)) r.addEdgeTrueDistance(edge, distance, param);
                if (color.has(EdgeColor.GREEN)) g.addEdgeTrueDistance(edge, distance, param);
                if (color.has(EdgeColor.BLUE)) b.addEdgeTrueDistance(edge, distance, param);
                cache.point = p;
                cache.absDistance = Math.abs(distance.distance);

                var ap = Vector2.subtract(p, cache.startPoint);
                var bp = Vector2.subtract(p, cache.endPoint);
                var add = Vector2.dotProduct(ap, cache.startBisectorN);
                var bdd = -Vector2.dotProduct(bp, cache.endBisectorN);
                if (add > 0.0) {
                    var pdHolder = new double[]{distance.distance};
                    if (PerpendicularDistanceSelectorBase.getPerpendicularDistance(pdHolder, ap, cache.negStartDirN)) {
                        pdHolder[0] = -pdHolder[0];
                        if (color.has(EdgeColor.RED)) r.addEdgePerpendicularDistance(pdHolder[0]);
                        if (color.has(EdgeColor.GREEN)) g.addEdgePerpendicularDistance(pdHolder[0]);
                        if (color.has(EdgeColor.BLUE)) b.addEdgePerpendicularDistance(pdHolder[0]);
                    }
                    cache.aPerpendicularDistance = pdHolder[0];
                }
                if (bdd > 0.0) {
                    var pdHolder = new double[]{distance.distance};
                    if (PerpendicularDistanceSelectorBase.getPerpendicularDistance(pdHolder, bp, cache.endDirN)) {
                        if (color.has(EdgeColor.RED)) r.addEdgePerpendicularDistance(pdHolder[0]);
                        if (color.has(EdgeColor.GREEN)) g.addEdgePerpendicularDistance(pdHolder[0]);
                        if (color.has(EdgeColor.BLUE)) b.addEdgePerpendicularDistance(pdHolder[0]);
                    }
                    cache.bPerpendicularDistance = pdHolder[0];
                }
                cache.aDomainDistance = add;
                cache.bDomainDistance = bdd;
            }
        }

        @Override
        public void merge(MultiDistanceSelector other) {
            r.merge(other.r);
            g.merge(other.g);
            b.merge(other.b);
        }

        @Override
        public MultiDistance distance() {
            return new MultiDistance(r.computeDistance(p), g.computeDistance(p), b.computeDistance(p));
        }

        public SignedDistance trueDistance() {
            var d = r.trueDistance();
            if (g.trueDistance().compareTo(d) < 0) d = g.trueDistance();
            if (b.trueDistance().compareTo(d) < 0) d = b.trueDistance();
            return d;
        }
    }

    final class MultiAndTrueDistanceSelector implements EdgeSelector<MultiAndTrueDistance, PerpendicularDistanceSelectorBase.EdgeCache, MultiAndTrueDistanceSelector> {
        private final MultiDistanceSelector inner = new MultiDistanceSelector();

        @Override
        public void reset(Vector2 p) {
            inner.reset(p);
        }

        @Override
        public void addEdge(PerpendicularDistanceSelectorBase.EdgeCache cache, EdgeSegment prev, EdgeSegment edge, EdgeSegment next, EdgeColor color) {
            inner.addEdge(cache, prev, edge, next, color);
        }

        @Override
        public void merge(MultiAndTrueDistanceSelector other) {
            inner.merge(other.inner);
        }

        @Override
        public MultiAndTrueDistance distance() {
            var md = inner.distance();
            return new MultiAndTrueDistance(md.r(), md.g(), md.b(), inner.trueDistance().distance);
        }
    }
}
