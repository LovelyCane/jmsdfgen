package lovely.cane.jmsdfgen;

public class SDFTransformation extends Projection {
    public final DistanceMapping distanceMapping;

    public SDFTransformation() {
        super();
        distanceMapping = new DistanceMapping();
    }

    public SDFTransformation(Projection projection, DistanceMapping distanceMapping) {
        super(projection.getScale(), projection.getTranslate());
        this.distanceMapping = distanceMapping;
    }

    public SDFTransformation(Projection projection, Range range) {
        this(projection, new DistanceMapping(range));
    }
}
