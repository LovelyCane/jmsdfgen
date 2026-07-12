package lovely.cane.jmsdfgen;

public class EdgeHolder {
    private EdgeSegment edgeSegment;

    public EdgeHolder(EdgeSegment segment) {
        edgeSegment = segment;
    }

    public EdgeHolder(Vector2 p0, Vector2 p1) {
        this(p0, p1, EdgeColor.WHITE);
    }

    public EdgeHolder(Vector2 p0, Vector2 p1, EdgeColor edgeColor) {
        edgeSegment = EdgeSegment.create(p0, p1, edgeColor);
    }

    public EdgeHolder(Vector2 p0, Vector2 p1, Vector2 p2) {
        this(p0, p1, p2, EdgeColor.WHITE);
    }

    public EdgeHolder(Vector2 p0, Vector2 p1, Vector2 p2, EdgeColor edgeColor) {
        edgeSegment = EdgeSegment.create(p0, p1, p2, edgeColor);
    }

    public EdgeHolder(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3) {
        this(p0, p1, p2, p3, EdgeColor.WHITE);
    }

    public EdgeHolder(Vector2 p0, Vector2 p1, Vector2 p2, Vector2 p3, EdgeColor edgeColor) {
        edgeSegment = EdgeSegment.create(p0, p1, p2, p3, edgeColor);
    }

    public EdgeHolder(EdgeHolder orig) {
        edgeSegment = orig.edgeSegment.copy();
    }

    public void set(EdgeHolder orig) {
        if (this != orig) edgeSegment = orig.edgeSegment.copy();
    }

    public EdgeSegment get() {
        return edgeSegment;
    }
}
