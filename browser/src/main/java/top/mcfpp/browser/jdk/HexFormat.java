package top.mcfpp.browser.jdk;

/** Hexadecimal bytes used by SNBT literals. */
public final class HexFormat {
    private boolean upper;
    private HexFormat() { }
    public static HexFormat of() { return new HexFormat(); }
    public HexFormat withUpperCase() { var result = new HexFormat(); result.upper = true; return result; }
    public String toHexDigits(byte value) {
        String digits = upper ? "0123456789ABCDEF" : "0123456789abcdef";
        return "" + digits.charAt((value >>> 4) & 15) + digits.charAt(value & 15);
    }
    public byte[] parseHex(CharSequence text) {
        if (text.length() % 2 != 0) throw new IllegalArgumentException("Odd hexadecimal length");
        byte[] result = new byte[text.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) ((fromHexDigit(text.charAt(i * 2)) << 4) | fromHexDigit(text.charAt(i * 2 + 1)));
        return result;
    }
    public byte[] parseHex(String text) { return parseHex((CharSequence) text); }
    public String formatHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append("0123456789abcdef".charAt((value >>> 4) & 15)).append("0123456789abcdef".charAt(value & 15));
        return result.toString();
    }
    public static int fromHexDigit(int value) {
        int digit = Character.digit(value, 16);
        if (digit < 0) throw new IllegalArgumentException("Invalid hexadecimal digit");
        return digit;
    }
    public static int fromHexDigits(CharSequence text) {
        if (text.length() > 8) throw new IllegalArgumentException("Hexadecimal integer too long");
        int value = 0;
        for (int i = 0; i < text.length(); i++) value = (value << 4) | fromHexDigit(text.charAt(i));
        return value;
    }
}
