package io.luna.util;

import java.util.List;

/**
 * A static-utility class that contains functions for manipulating strings.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class StringUtils {

    /**
     * An array containing valid {@code char}s.
     */
    public static final char VALID_CHARACTERS[] = { '_', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
            'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-', '+', '=', ':', ';', '.', '>', '<', ',',
            '"', '[', ']', '|', '?', '/', '`' };

    /**
     * Joins the {@code elements} using a ',' as the delimiter, with
     * {@code prefix} and {@code suffix}. On the last element, an "and" is
     * added.
     * 
     * @param prefix The prefix of the String.
     * @param suffix The suffix of the String.
     * @param elements The String elements to join together.
     * @return The newly joined String.
     */
    public static String joinWithAnd(String prefix, String suffix, List<?> elements) {
        StringBuilder sb = new StringBuilder(prefix);

        for (int idx = 0; idx < elements.size(); idx++) {
            if ((idx + 1) == elements.size()) {
                sb.append("and " + elements.get(idx)); // On last index, add
                                                       // "and" instead of ",".
                continue;
            }
            sb.append(elements.get(idx) + ", ");
        }

        sb.append(suffix);
        return sb.toString();
    }

    /**
     * Encodes {@code s} to a base-37 {@code long}.
     * 
     * @param s The {@link String} to encode.
     * @return The encoded {@code String}.
     */
    public static long encodeToBase37(String s) {
        long l = 0L;
        for (int i = 0; i < s.length() && i < 12; i++) {
            char c = s.charAt(i);
            l *= 37L;
            if (c >= 'A' && c <= 'Z') {
                l += (1 + c) - 65;
            } else if (c >= 'a' && c <= 'z') {
                l += (1 + c) - 97;
            } else if (c >= '0' && c <= '9') {
                l += (27 + c) - 48;
            }
        }
        while (l % 37L == 0L && l != 0L) {
            l /= 37L;
        }
        return l;
    }

    /**
     * Decodes {@code l} into a {@link String}.
     * 
     * @param l The base-37 {@code long} to decode.
     * @return The decoded {@code l}.
     */
    public static String decodeFromBase37(long l) {
        int i = 0;
        char ac[] = new char[12];
        while (l != 0L) {
            long l1 = l;
            l /= 37L;
            ac[11 - i++] = VALID_CHARACTERS[(int) (l1 - l * 37L)];
        }
        return new String(ac, 12 - i, i);
    }

    /**
     * A private constructor to discourage external instantiation.
     */
    private StringUtils() {}
}
