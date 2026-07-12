package lovely.cane.jmsdfgen;

public enum EdgeColor {
    BLACK,  // 0
    RED,    // 1
    GREEN,  // 2
    YELLOW, // 3
    BLUE,   // 4
    MAGENTA,// 5
    CYAN,   // 6
    WHITE;  // 7

    private static final EdgeColor[] VALUES = values();

    public int mask() {
        return ordinal();
    }

    public boolean has(EdgeColor flag) {
        return (ordinal() & flag.ordinal()) != 0;
    }

    public static EdgeColor fromMask(int mask) {
        return VALUES[mask & 7];
    }
}