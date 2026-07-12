package lovely.cane.jmsdfgen;

public class SignedDistance implements Comparable<SignedDistance> {

    public double distance;
    public double dot;

    public SignedDistance() {
        distance = -Double.MAX_VALUE;
        dot = 0;
    }

    public SignedDistance(double dist, double d) {
        distance = dist;
        dot = d;
    }

    @Override
    public int compareTo(SignedDistance other) {
        var absThis = Math.abs(distance);
        var absOther = Math.abs(other.distance);
        if (absThis < absOther) return -1;
        if (absThis > absOther) return 1;
        return Double.compare(dot, other.dot);
    }
}
