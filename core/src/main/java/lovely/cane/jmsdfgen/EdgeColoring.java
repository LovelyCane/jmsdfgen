package lovely.cane.jmsdfgen;

import java.util.*;

public final class EdgeColoring {
    private static final int[] FIRST_POSSIBLE_COLOR = new int[]{-1, 0, 1, 0, 2, 2, 1, 0};
    private static final int MSDFGEN_EDGE_LENGTH_PRECISION = 4;
    private static final int MAX_RECOLOR_STEPS = 16;
    private static final int EDGE_DISTANCE_PRECISION = 16;

    private EdgeColoring() {
    }

    private static int symmetricalTrichotomy(int position, int n) {
        return (int) (3 + 2.875 * position / (n - 1) - 1.4375 + 0.5) - 3;
    }

    private static boolean isCorner(Vector2 aDir, Vector2 bDir, double crossThreshold) {
        return Vector2.dotProduct(aDir, bDir) <= 0 || Math.abs(Vector2.crossProduct(aDir, bDir)) > crossThreshold;
    }

    private static double estimateEdgeLength(EdgeSegment edge) {
        double len = 0;
        var prev = edge.point(0);
        for (var i = 1; i <= MSDFGEN_EDGE_LENGTH_PRECISION; ++i) {
            var cur = edge.point(1.0 / MSDFGEN_EDGE_LENGTH_PRECISION * i);
            len += Vector2.subtract(cur, prev).length();
            prev = cur;
        }
        return len;
    }

    private static int seedExtract2(long[] seed) {
        var v = (int) (seed[0] & 1);
        seed[0] >>>= 1;
        return v;
    }

    private static int seedExtract3(long[] seed) {
        var v = (int) (seed[0] % 3);
        seed[0] /= 3;
        return v;
    }

    private static EdgeColor initColor(long[] seed) {
        var colors = new EdgeColor[]{EdgeColor.CYAN, EdgeColor.MAGENTA, EdgeColor.YELLOW};
        return colors[seedExtract3(seed)];
    }

    private static void switchColor(EdgeColor[] color, long[] seed) {
        var shifted = color[0].ordinal() << (1 + seedExtract2(seed));
        color[0] = EdgeColor.fromMask((shifted | (shifted >>> 3)) & EdgeColor.WHITE.ordinal());
    }

    private static void switchColor(EdgeColor[] color, long[] seed, EdgeColor banned) {
        var combined = EdgeColor.fromMask(color[0].ordinal() & banned.ordinal());
        if (combined == EdgeColor.RED || combined == EdgeColor.GREEN || combined == EdgeColor.BLUE)
            color[0] = EdgeColor.fromMask(combined.ordinal() ^ EdgeColor.WHITE.ordinal());
        else
            switchColor(color, seed);
    }

    public static void edgeColoringSimple(Shape shape, double angleThreshold) {
        edgeColoringSimple(shape, angleThreshold, 0);
    }

    public static void edgeColoringSimple(Shape shape, double angleThreshold, long seed) {
        var crossThreshold = Math.sin(angleThreshold);
        var seedRef = new long[]{seed};
        var color = new EdgeColor[]{initColor(seedRef)};
        var corners = new ArrayList<Integer>();

        for (var contour : shape.contours) {
            if (contour.edges.isEmpty()) continue;

            {
                corners.clear();
                var prevDirection = contour.edges.getLast().get().direction(1);
                var index = 0;
                for (var eh : contour.edges) {
                    if (isCorner(prevDirection.normalize(), eh.get().direction(0).normalize(), crossThreshold))
                        corners.add(index);
                    prevDirection = eh.get().direction(1);
                    ++index;
                }
            }

            if (corners.isEmpty()) {
                switchColor(color, seedRef);
                for (var edge : contour.edges)
                    edge.get().color = color[0];
            } else if (corners.size() == 1) {
                var colors = new EdgeColor[3];
                switchColor(color, seedRef);
                colors[0] = color[0];
                colors[1] = EdgeColor.WHITE;
                switchColor(color, seedRef);
                colors[2] = color[0];
                int corner = corners.getFirst();
                var m = contour.edges.size();
                if (m >= 3) {
                    for (var i = 0; i < m; ++i)
                        contour.edges.get((corner + i) % m).get().color = colors[1 + symmetricalTrichotomy(i, m)];
                } else if (m >= 1) {
                    var parts = new EdgeSegment[7];
                    var thirds0 = contour.edges.get(0).get().splitInThirds();
                    System.arraycopy(thirds0, 0, parts, 3 * corner, 3);
                    if (m == 2) {
                        var thirds1 = contour.edges.get(1).get().splitInThirds();
                        System.arraycopy(thirds1, 0, parts, 3 - 3 * corner, 3);
                        parts[0].color = colors[0];
                        parts[1].color = colors[0];
                        parts[2].color = colors[1];
                        parts[3].color = colors[1];
                        parts[4].color = colors[2];
                        parts[5].color = colors[2];
                    } else {
                        parts[0].color = colors[0];
                        parts[1].color = colors[1];
                        parts[2].color = colors[2];
                    }
                    contour.edges.clear();
                    for (var seg : parts) {
                        if (seg != null) contour.edges.add(new EdgeHolder(seg));
                    }
                }
            } else {
                var cornerCount = corners.size();
                var spline = 0;
                int start = corners.getFirst();
                var m = contour.edges.size();
                switchColor(color, seedRef);
                var initialColor = color[0];
                for (var i = 0; i < m; ++i) {
                    var idx = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1) == idx) {
                        ++spline;
                        var banned = (spline == cornerCount - 1) ? initialColor : EdgeColor.BLACK;
                        switchColor(color, seedRef, banned);
                    }
                    contour.edges.get(idx).get().color = color[0];
                }
            }
        }
    }

    public static void edgeColoringInkTrap(Shape shape, double angleThreshold) {
        edgeColoringInkTrap(shape, angleThreshold, 0);
    }

    public static void edgeColoringInkTrap(Shape shape, double angleThreshold, long seed) {
        var crossThreshold = Math.sin(angleThreshold);
        var seedRef = new long[]{seed};
        var color = new EdgeColor[]{initColor(seedRef)};
        List<InkTrapCorner> corners = new ArrayList<>();

        for (var contour : shape.contours) {
            if (contour.edges.isEmpty()) continue;
            double splineLength = 0;

            corners.clear();
            var prevDirection = contour.edges.getLast().get().direction(1);
            var index = 0;
            for (var eh : contour.edges) {
                if (isCorner(prevDirection.normalize(), eh.get().direction(0).normalize(), crossThreshold)) {
                    var c = new InkTrapCorner();
                    c.index = index;
                    c.prevEdgeLengthEstimate = splineLength;
                    c.minor = false;
                    c.color = EdgeColor.BLACK;
                    corners.add(c);
                    splineLength = 0;
                }
                splineLength += estimateEdgeLength(eh.get());
                prevDirection = eh.get().direction(1);
                ++index;
            }

            if (corners.isEmpty()) {
                switchColor(color, seedRef);
                for (var eh : contour.edges)
                    eh.get().color = color[0];
            } else if (corners.size() == 1) {
                var colors = new EdgeColor[3];
                switchColor(color, seedRef);
                colors[0] = color[0];
                colors[1] = EdgeColor.WHITE;
                switchColor(color, seedRef);
                colors[2] = color[0];
                var corner = corners.getFirst().index;
                var m = contour.edges.size();
                if (m >= 3) {
                    for (var i = 0; i < m; ++i)
                        contour.edges.get((corner + i) % m).get().color = colors[1 + symmetricalTrichotomy(i, m)];
                } else if (m >= 1) {
                    var parts = new EdgeSegment[7];
                    var thirds0 = contour.edges.get(0).get().splitInThirds();
                    System.arraycopy(thirds0, 0, parts, 3 * corner, 3);
                    if (m == 2) {
                        var thirds1 = contour.edges.get(1).get().splitInThirds();
                        System.arraycopy(thirds1, 0, parts, 3 - 3 * corner, 3);
                        parts[0].color = colors[0];
                        parts[1].color = colors[0];
                        parts[2].color = colors[1];
                        parts[3].color = colors[1];
                        parts[4].color = colors[2];
                        parts[5].color = colors[2];
                    } else {
                        parts[0].color = colors[0];
                        parts[1].color = colors[1];
                        parts[2].color = colors[2];
                    }
                    contour.edges.clear();
                    for (var seg : parts) {
                        if (seg != null) contour.edges.add(new EdgeHolder(seg));
                    }
                }
            } else {
                var cornerCount = corners.size();
                var majorCornerCount = cornerCount;
                if (cornerCount > 3) {
                    var first = corners.getFirst();
                    first.prevEdgeLengthEstimate += splineLength;
                    for (var i = 0; i < cornerCount; ++i) {
                        var cur = corners.get(i);
                        var next = corners.get((i + 1) % cornerCount);
                        var next2 = corners.get((i + 2) % cornerCount);
                        if (cur.prevEdgeLengthEstimate > next.prevEdgeLengthEstimate &&
                                next.prevEdgeLengthEstimate < next2.prevEdgeLengthEstimate) {
                            cur.minor = true;
                            --majorCornerCount;
                        }
                    }
                }
                var initialColor = EdgeColor.BLACK;
                for (var i = 0; i < cornerCount; ++i) {
                    var cur = corners.get(i);
                    if (!cur.minor) {
                        --majorCornerCount;
                        var banned = (majorCornerCount == 0) ? initialColor : EdgeColor.BLACK;
                        switchColor(color, seedRef, banned);
                        cur.color = color[0];
                        if (initialColor == EdgeColor.BLACK)
                            initialColor = color[0];
                    }
                }
                for (var i = 0; i < cornerCount; ++i) {
                    var cur = corners.get(i);
                    if (cur.minor) {
                        var nextColor = corners.get((i + 1) % cornerCount).color;
                        cur.color = EdgeColor.fromMask((color[0].ordinal() & nextColor.ordinal()) ^ EdgeColor.WHITE.ordinal());
                    } else {
                        color[0] = cur.color;
                    }
                }
                var spline = 0;
                var start = corners.getFirst().index;
                var m = contour.edges.size();
                color[0] = corners.getFirst().color;
                for (var i = 0; i < m; ++i) {
                    var idx = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1).index == idx)
                        color[0] = corners.get(++spline).color;
                    contour.edges.get(idx).get().color = color[0];
                }
            }
        }
    }

    public static void edgeColoringByDistance(Shape shape, double angleThreshold) {
        edgeColoringByDistance(shape, angleThreshold, 0);
    }

    public static void edgeColoringByDistance(Shape shape, double angleThreshold, long seed) {
        List<EdgeSegment> edgeSegments = new ArrayList<>();
        List<Integer> splineStarts = new ArrayList<>();

        var crossThreshold = Math.sin(angleThreshold);
        List<Integer> corners = new ArrayList<>();

        for (var contour : shape.contours) {
            if (contour.edges.isEmpty()) continue;

            corners.clear();
            var prevDirection = contour.edges.getLast().get().direction(1);
            var index = 0;
            for (var eh : contour.edges) {
                if (isCorner(prevDirection.normalize(), eh.get().direction(0).normalize(), crossThreshold))
                    corners.add(index);
                prevDirection = eh.get().direction(1);
                ++index;
            }

            splineStarts.add(edgeSegments.size());
            if (corners.isEmpty()) {
                for (var eh : contour.edges)
                    edgeSegments.add(eh.get());
            } else if (corners.size() == 1) {
                int corner = corners.getFirst();
                var m = contour.edges.size();
                if (m >= 3) {
                    for (var i = 0; i < m; ++i) {
                        if (i == m / 2)
                            splineStarts.add(edgeSegments.size());
                        if (symmetricalTrichotomy(i, m) != 0)
                            edgeSegments.add(contour.edges.get((corner + i) % m).get());
                        else
                            contour.edges.get((corner + i) % m).get().color = EdgeColor.WHITE;
                    }
                } else if (m >= 1) {
                    var parts = new EdgeSegment[7];
                    var thirds0 = contour.edges.get(0).get().splitInThirds();
                    System.arraycopy(thirds0, 0, parts, 3 * corner, 3);
                    if (m == 2) {
                        var thirds1 = contour.edges.get(1).get().splitInThirds();
                        System.arraycopy(thirds1, 0, parts, 3 - 3 * corner, 3);
                        edgeSegments.add(parts[0]);
                        edgeSegments.add(parts[1]);
                        parts[2].color = EdgeColor.WHITE;
                        parts[3].color = EdgeColor.WHITE;
                        splineStarts.add(edgeSegments.size());
                        edgeSegments.add(parts[4]);
                        edgeSegments.add(parts[5]);
                    } else {
                        edgeSegments.add(parts[0]);
                        parts[1].color = EdgeColor.WHITE;
                        splineStarts.add(edgeSegments.size());
                        edgeSegments.add(parts[2]);
                    }
                    contour.edges.clear();
                    for (var seg : parts) {
                        if (seg != null) contour.edges.add(new EdgeHolder(seg));
                    }
                }
            } else {
                var cornerCount = corners.size();
                var spline = 0;
                int start = corners.getFirst();
                var m = contour.edges.size();
                for (var i = 0; i < m; ++i) {
                    var idx = (start + i) % m;
                    if (spline + 1 < cornerCount && corners.get(spline + 1) == idx) {
                        splineStarts.add(edgeSegments.size());
                        ++spline;
                    }
                    edgeSegments.add(contour.edges.get(idx).get());
                }
            }
        }
        splineStarts.add(edgeSegments.size());

        var segmentCount = edgeSegments.size();
        var splineCount = splineStarts.size() - 1;
        if (splineCount == 0) return;

        var distanceMatrix = new double[splineCount][splineCount];
        for (var i = 0; i < splineCount; ++i) {
            distanceMatrix[i][i] = -1;
            for (var j = i + 1; j < splineCount; ++j) {
                var dist = splineToSplineDistance(edgeSegments, splineStarts.get(i), splineStarts.get(i + 1),
                        splineStarts.get(j), splineStarts.get(j + 1), EDGE_DISTANCE_PRECISION);
                distanceMatrix[i][j] = dist;
                distanceMatrix[j][i] = dist;
            }
        }

        List<EdgeDistance> graphEdgeDistances = new ArrayList<>();
        for (var i = 0; i < splineCount; ++i)
            for (var j = i + 1; j < splineCount; ++j)
                graphEdgeDistances.add(new EdgeDistance(distanceMatrix[i][j], i, j));

        graphEdgeDistances.sort(Comparator.comparingDouble(ed -> ed.distance));

        var edgeMatrix = new int[splineCount][splineCount];
        var nextEdge = 0;
        while (nextEdge < graphEdgeDistances.size() && graphEdgeDistances.get(nextEdge).distance == 0) {
            var ed = graphEdgeDistances.get(nextEdge);
            edgeMatrix[ed.row][ed.col] = 1;
            edgeMatrix[ed.col][ed.row] = 1;
            ++nextEdge;
        }

        var coloring = new int[splineCount];
        var coloringBuffer = new int[splineCount];
        var seedRef = new long[]{seed};
        colorSecondDegreeGraph(coloring, edgeMatrix, splineCount, seedRef);

        for (; nextEdge < graphEdgeDistances.size(); ++nextEdge) {
            var ed = graphEdgeDistances.get(nextEdge);
            tryAddEdge(coloring, edgeMatrix, splineCount, ed.row, ed.col, coloringBuffer);
        }

        var colors = new EdgeColor[]{EdgeColor.YELLOW, EdgeColor.CYAN, EdgeColor.MAGENTA};
        var spline = -1;
        for (var i = 0; i < segmentCount; ++i) {
            if (spline + 1 < splineStarts.size() && splineStarts.get(spline + 1) == i)
                ++spline;
            edgeSegments.get(i).color = colors[coloring[spline]];
        }
    }

    private static void colorSecondDegreeGraph(int[] coloring, int[][] edgeMatrix, int vertexCount, long[] seed) {
        for (var i = 0; i < vertexCount; ++i) {
            var possibleColors = 7;
            for (var j = 0; j < i; ++j) {
                if (edgeMatrix[i][j] != 0)
                    possibleColors &= ~(1 << coloring[j]);
            }
            var col = switch (possibleColors) {
                case 2 -> 1;
                case 3 -> seedExtract2(seed);
                case 4 -> 2;
                case 5 -> (seedExtract2(seed) == 0) ? 2 : 0;
                case 6 -> seedExtract2(seed) + 1;
                case 7 -> (seedExtract3(seed) + i) % 3;
                default -> 0;
            };
            coloring[i] = col;
        }
    }

    private static int vertexPossibleColors(int[] coloring, int[] edgeVector, int vertexCount) {
        var usedColors = 0;
        for (var i = 0; i < vertexCount; ++i)
            if (edgeVector[i] != 0)
                usedColors |= 1 << coloring[i];
        return 7 & ~usedColors;
    }

    private static void uncolorSameNeighbors(Queue<Integer> uncolored, int[] coloring, int[][] edgeMatrix,
                                             int vertex, int vertexCount) {
        for (var i = vertex + 1; i < vertexCount; ++i) {
            if (edgeMatrix[vertex][i] != 0 && coloring[i] == coloring[vertex]) {
                coloring[i] = -1;
                uncolored.offer(i);
            }
        }
        for (var i = 0; i < vertex; ++i) {
            if (edgeMatrix[vertex][i] != 0 && coloring[i] == coloring[vertex]) {
                coloring[i] = -1;
                uncolored.offer(i);
            }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private static boolean tryAddEdge(int[] coloring, int[][] edgeMatrix, int vertexCount,
                                      int vertexA, int vertexB, int[] coloringBuffer) {
        edgeMatrix[vertexA][vertexB] = 1;
        edgeMatrix[vertexB][vertexA] = 1;
        if (coloring[vertexA] != coloring[vertexB])
            return true;

        var bPossible = vertexPossibleColors(coloring, edgeMatrix[vertexB], vertexCount);
        if (bPossible != 0) {
            coloring[vertexB] = FIRST_POSSIBLE_COLOR[bPossible];
            return true;
        }

        System.arraycopy(coloring, 0, coloringBuffer, 0, vertexCount);
        var uncolored = new ArrayDeque<Integer>();
        {
            @SuppressWarnings("UnnecessaryLocalVariable")
            var c = coloringBuffer;
            c[vertexB] = FIRST_POSSIBLE_COLOR[7 & ~(1 << coloring[vertexA])];
            uncolorSameNeighbors(uncolored, c, edgeMatrix, vertexB, vertexCount);
            var step = 0;
            while (!uncolored.isEmpty() && step < MAX_RECOLOR_STEPS) {
                int i = uncolored.poll();
                var possible = vertexPossibleColors(c, edgeMatrix[i], vertexCount);
                if (possible != 0) {
                    c[i] = FIRST_POSSIBLE_COLOR[possible];
                    continue;
                }
                do {
                    c[i] = step++ % 3;
                } while (edgeMatrix[i][vertexA] != 0 && c[i] == c[vertexA]);
                uncolorSameNeighbors(uncolored, c, edgeMatrix, i, vertexCount);
            }
        }
        if (!uncolored.isEmpty()) {
            edgeMatrix[vertexA][vertexB] = 0;
            edgeMatrix[vertexB][vertexA] = 0;
            return false;
        }
        System.arraycopy(coloringBuffer, 0, coloring, 0, vertexCount);
        return true;
    }

    private static double splineToSplineDistance(List<EdgeSegment> edgeSegments,
                                                 int aStart, int aEnd, int bStart, int bEnd,
                                                 @SuppressWarnings("SameParameterValue") int precision
    ) {
        var minDist = Double.MAX_VALUE;
        for (var ai = aStart; ai < aEnd; ++ai) {
            for (var bi = bStart; bi < bEnd && minDist != 0; ++bi) {
                var d = edgeToEdgeDistance(edgeSegments.get(ai), edgeSegments.get(bi), precision);
                if (d < minDist) minDist = d;
            }
        }
        return minDist;
    }

    private static double edgeToEdgeDistance(EdgeSegment a, EdgeSegment b, int precision) {
        if (Vector2.equals(a.point(0), b.point(0)) ||
                Vector2.equals(a.point(0), b.point(1)) ||
                Vector2.equals(a.point(1), b.point(0)) ||
                Vector2.equals(a.point(1), b.point(1)))
            return 0;
        var iFac = 1.0 / precision;
        var minDist = Vector2.subtract(b.point(0), a.point(0)).length();
        for (var i = 0; i <= precision; ++i) {
            var t = iFac * i;
            var d = Math.abs(a.signedDistance(b.point(t), new double[]{t}).distance);
            if (d < minDist) minDist = d;
        }
        for (var i = 0; i <= precision; ++i) {
            var t = iFac * i;
            var d = Math.abs(b.signedDistance(a.point(t), new double[]{t}).distance);
            if (d < minDist) minDist = d;
        }
        return minDist;
    }

    private static class InkTrapCorner {
        int index;
        double prevEdgeLengthEstimate;
        boolean minor;
        EdgeColor color = EdgeColor.BLACK;
    }

    private record EdgeDistance(double distance, int row, int col) {
    }
}