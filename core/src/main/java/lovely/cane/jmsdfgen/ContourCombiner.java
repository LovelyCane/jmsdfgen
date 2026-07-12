package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface ContourCombiner<S extends EdgeSelector<D, C, S>, D, C>
        permits
        ContourCombiner.SimpleContourCombiner,
        ContourCombiner.OverlappingContourCombiner {
    void reset(Vector2 p);

    S edgeSelector(int i);

    D distance();

    final class SimpleContourCombiner<S extends EdgeSelector<D, C, S>, D, C> implements ContourCombiner<S, D, C> {
        private final S shapeEdgeSelector;

        public SimpleContourCombiner(S edgeSelector) {
            shapeEdgeSelector = edgeSelector;
        }

        @Override
        public void reset(Vector2 p) {
            shapeEdgeSelector.reset(p);
        }

        @Override
        public S edgeSelector(int i) {
            return shapeEdgeSelector;
        }

        @Override
        public D distance() {
            return shapeEdgeSelector.distance();
        }
    }

    final class OverlappingContourCombiner<S extends EdgeSelector<D, C, S>, D, C> implements ContourCombiner<S, D, C> {
        private final Supplier<D> initDistance;
        private final Supplier<S> newEdgeSelector;
        private final Function<D, Double> resolveDistance;
        private Vector2 p = new Vector2();
        private final List<Integer> windings;
        private final List<S> edgeSelectors;

        public OverlappingContourCombiner(
                Shape shape, Supplier<S> newEdgeSelector, Supplier<D> initDistance, Function<D, Double> resolveDistance
        ) {
            this.initDistance = initDistance;
            this.newEdgeSelector = newEdgeSelector;
            this.resolveDistance = resolveDistance;

            var size = shape.contours.size();
            windings = new ArrayList<>(size);
            edgeSelectors = new ArrayList<>(size);
            for (var contour : shape.contours)
                windings.add(contour.winding());
            for (var i = 0; i < size; i++) {
                edgeSelectors.add(newEdgeSelector.get());
            }
        }

        @Override
        public void reset(Vector2 p) {
            this.p = p;
            for (var s : edgeSelectors)
                s.reset(p);
        }

        @Override
        public S edgeSelector(int i) {
            return edgeSelectors.get(i);
        }

        @Override
        public D distance() {
            var contourCount = edgeSelectors.size();
            var shapeSel = newEdgeSelector.get();
            var innerSel = newEdgeSelector.get();
            var outerSel = newEdgeSelector.get();
            shapeSel.reset(p);
            innerSel.reset(p);
            outerSel.reset(p);

            for (var i = 0; i < contourCount; i++) {
                var edgeDist = edgeSelectors.get(i).distance();
                shapeSel.merge(edgeSelectors.get(i));
                if (windings.get(i) > 0 && resolveDistance(edgeDist) >= 0.0) {
                    innerSel.merge(edgeSelectors.get(i));
                }
                if (windings.get(i) < 0 && resolveDistance(edgeDist) <= 0.0) {
                    outerSel.merge(edgeSelectors.get(i));
                }
            }

            var shapeDistance = shapeSel.distance();
            var innerDistance = innerSel.distance();
            var outerDistance = outerSel.distance();
            var innerScalar = resolveDistance(innerDistance);
            var outerScalarDistance = resolveDistance(outerDistance);

            var distance = initDistance.get();
            int winding;
            if (innerScalar >= 0.0 && Math.abs(innerScalar) <= Math.abs(outerScalarDistance)) {
                distance = innerDistance;
                winding = 1;
                for (var i = 0; i < contourCount; i++) {
                    if (windings.get(i) > 0) {
                        var contourDistance = edgeSelectors.get(i).distance();
                        if (Math.abs(resolveDistance(contourDistance)) < Math.abs(outerScalarDistance) &&
                                resolveDistance(contourDistance) > resolveDistance(distance))
                            distance = contourDistance;
                    }
                }
            } else if (outerScalarDistance <= 0.0 && Math.abs(outerScalarDistance) < Math.abs(innerScalar)) {
                distance = outerDistance;
                winding = -1;
                for (var i = 0; i < contourCount; i++) {
                    if (windings.get(i) < 0) {
                        var contourDistance = edgeSelectors.get(i).distance();
                        if (Math.abs(resolveDistance(contourDistance)) < Math.abs(innerScalar)
                                && resolveDistance(contourDistance) < resolveDistance(distance))
                            distance = contourDistance;
                    }
                }
            } else return shapeDistance;

            for (var i = 0; i < contourCount; i++) {
                if (windings.get(i) != winding) {
                    var contourDistance = edgeSelectors.get(i).distance();
                    if (resolveDistance(contourDistance) * resolveDistance(distance) >= 0.0 &&
                            Math.abs(resolveDistance(contourDistance)) < Math.abs(resolveDistance(distance)))
                        distance = contourDistance;
                }
            }
            if (resolveDistance(distance) == resolveDistance(shapeDistance))
                distance = shapeDistance;
            return distance;
        }

        private double resolveDistance(D distance) {
            return resolveDistance.apply(distance);
        }
    }
}
