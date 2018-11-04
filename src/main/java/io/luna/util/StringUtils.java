package io.luna.util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A static-utility class that contains functions for manipulating strings.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class StringUtils {

    /**
     * An empty array of strings.
     */
    public static final String[] EMPTY_ARRAY = {};

    /**
     * An array containing valid {@code char}s.
     */
    public static final ImmutableList<Character> VALID_CHARACTERS = ImmutableList.of('_', 'a', 'b', 'c', 'd', 'e',
            'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '-',
            '+', '=', ':', ';', '.', '>', '<', ',', '"', '[', ']', '|', '?', '/', '`');

    /**
     * A {@link Joiner} that joins strings together with a ",".
     */
    public static final Joiner COMMA_JOINER = Joiner.on(", ").skipNulls();

    /**
     * Computes the indefinite article of {@code thing}.
     *
     * @param thing The thing to compute for.
     * @return The article.
     */
    public static String computeArticle(Object thing) {
        switch (Character.toLowerCase(thing.toString().charAt(0))) {
            case 'a':
            case 'e':
            case 'i':
            case 'o':
            case 'u':
                return "an";
        }

        return "a";
    }

    /**
     * Appends the indefinite article of {@code thing} to {@code thing}.
     *
     * @param thing The thing to compute for and append.
     * @return The appended article.
     */
    public static String addArticle(Object thing) {
        String asString = thing.toString();
        return computeArticle(asString) + " " + asString;
    }

    /**
     * Encodes {@code s} to a base-37 {@code long}.
     *
     * @param string The string to encode.
     * @return The encoded string.
     */
    public static long encodeToBase37(String string) {
        long l = 0L;
        for (int i = 0; i < string.length() && i < 12; i++) {
            char c = string.charAt(i);
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
     * Decodes {@code value} from a base-37 {@code long}.
     *
     * @param value The long to decode.
     * @return The decoded long.
     */
    public static String decodeFromBase37(long value) {
        checkArgument(value > 0L &&
                value < 6582952005840035281L &&
                value % 37L != 0L, "Invalid long value.");

        int offset = 0;
        char name[] = new char[12];
        while (value != 0L) {
            long n = value;
            value /= 37L;
            name[11 - offset++] = VALID_CHARACTERS.get((int) (n - value * 37L));
        }
        return new String(name, 12 - offset, offset);
    }


    /**
     * Capitalizes a String value.
     *
     * @param s The String to capitalize.
     * @return The capitalized String.
     */
    public static String capitalize(String s) {
        if (!s.isEmpty()) {
            String capital = s.substring(0, 1).toUpperCase();
            StringBuilder builder = new StringBuilder(s);

            builder.setCharAt(0, capital.charAt(0));
            return builder.toString();
        }
        return s;
    }

    /**
     * A private constructor to discourage external instantiation.
     */
    private StringUtils() {
    }
}
