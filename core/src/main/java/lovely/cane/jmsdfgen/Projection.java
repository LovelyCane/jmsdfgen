package lovely.cane.jmsdfgen;

public class Projection {
    private final Vector2 scale;
    private final Vector2 translate;

    public Projection() {
        scale = new Vector2(1, 1);
        translate = new Vector2(0, 0);
    }

    public Projection(Vector2 scale, Vector2 translate) {
        this.scale = scale;
        this.translate = translate;
    }

    public Vector2 project(Vector2 coord) {
        return Vector2.multiply(scale, Vector2.add(coord, translate));
    }

    public Vector2 unproject(Vector2 coord) {
        return new Vector2(coord.x / scale.x - translate.x, coord.y / scale.y - translate.y);
    }

    public Vector2 projectVector(Vector2 vector) {
        return new Vector2(scale.x * vector.x, scale.y * vector.y);
    }

    public Vector2 unprojectVector(Vector2 vector) {
        return new Vector2(vector.x / scale.x, vector.y / scale.y);
    }

    public double projectX(double x) {
        return scale.x * (x + translate.x);
    }

    public double projectY(double y) {
        return scale.y * (y + translate.y);
    }

    public double unprojectX(double x) {
        return x / scale.x - translate.x;
    }

    public double unprojectY(double y) {
        return y / scale.y - translate.y;
    }

    public Vector2 getScale() {
        return scale;
    }

    public Vector2 getTranslate() {
        return translate;
    }
}
