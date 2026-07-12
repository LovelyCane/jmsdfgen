package lovely.cane.jmsdfgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ShapeDistanceFinder<C extends ContourCombiner<S, D, Cache>, S extends EdgeSelector<D, Cache, S>, D, Cache> {
    private final Shape shape;
    private final C combiner;
    private final List<Cache> cacheList;

    public ShapeDistanceFinder(Shape shape, C combiner, Supplier<Cache> newCache) {
        this.shape = shape;
        this.combiner = combiner;
        var n = shape.edgeCount();
        cacheList = new ArrayList<>(n);
        for (var i = 0; i < n; i++) cacheList.add(newCache.get());
    }

    public D distance(Vector2 origin) {
        combiner.reset(origin);
        var ci = 0;
        for (var contourIndex = 0; contourIndex < shape.contours.size(); contourIndex++) {
            var contour = shape.contours.get(contourIndex);
            var n = contour.edges.size();
            if (n == 0) continue;
            var selector = combiner.edgeSelector(contourIndex);
            var prev = Math.max(n - 2, 0);
            var cur = n - 1;
            for (var next = 0; next < n; next++) {
                var edgeSeg = contour.edges.get(cur).get();
                var prevSeg = contour.edges.get(prev).get();
                var nextSeg = contour.edges.get(next).get();
                var color = edgeSeg.color;
                selector.addEdge(cacheList.get(ci), prevSeg, edgeSeg, nextSeg, color);
                ci++;
                prev = cur;
                cur = next;
            }
        }
        return combiner.distance();
    }

    public static <C extends ContourCombiner<S, D, Cache>, S extends EdgeSelector<D, Cache, S>, D, Cache> D oneShotDistance(
            Shape shape, Vector2 origin, C combiner, Supplier<Cache> newCache) {
        combiner.reset(origin);
        for (var contourIndex = 0; contourIndex < shape.contours.size(); contourIndex++) {
            var contour = shape.contours.get(contourIndex);
            var n = contour.edges.size();
            if (n == 0) continue;
            var selector = combiner.edgeSelector(contourIndex);
            var prev = Math.max(n - 2, 0);
            var cur = n - 1;
            var dummy = newCache.get();
            for (var next = 0; next < n; next++) {
                var prevSeg = contour.edges.get(prev).get();
                var curSeg = contour.edges.get(cur).get();
                var nextSeg = contour.edges.get(next).get();
                var color = curSeg.color;
                selector.addEdge(dummy, prevSeg, curSeg, nextSeg, color);
                prev = cur;
                cur = next;
            }
        }
        return combiner.distance();
    }
}
