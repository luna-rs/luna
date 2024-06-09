package io.luna.util;

import com.google.common.base.Joiner;
import io.luna.net.codec.ByteMessage;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A static-utility class that contains functions for manipulating strings.
 *
 * @author lare96
 */
public final class StringUtils {

    /**
     * A {@link Joiner} that joins strings together with a ",".
     */
    public static final Joiner COMMA_JOINER = Joiner.on(", ").skipNulls();

    /**
     * An empty array of strings.
     */
    public static final String[] EMPTY_ARRAY = {};

    /**
     * An array containing valid {@code char}s.
     */
    public static final char[] VALID_CHARACTERS = new char[] {
        '_', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
        'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6', '7', '8',
        '9', '!', '@', '#', '$', '%', '^', '&', '*',
        '(', ')', '-', '+', '=', ':', ';', '.', '>',
        '<', ',', '"', '[', ']', '|', '?', '/', '`'
    };

    /**
     * The character table that will aid in unpacking text.
     */
    private static final char[] FREQUENCY_ORDERED_CHARS = new char[]{
        ' ', 'e', 't', 'a', 'o', 'i', 'h', 'n', 's', 'r',
        'd', 'l', 'u', 'm', 'w', 'c', 'y', 'f', 'g', 'p',
        'b', 'v', 'k', 'x', 'j', 'q', 'z', '0', '1', '2',
        '3', '4', '5', '6', '7', '8', '9', ' ', '!', '?',
        '.', ',', ':', ';', '(', ')', '-', '&', '*', '\\',
        '\'', '@', '#', '+', '=', '\243', '$', '%', '"', '[', ']'
    };
    
    /**
     * A private constructor to discourage external instantiation.
     */
    private StringUtils() {
    }
    
    /**
     * Unpacks text received from the client.
     *
     * @param message The message, in bytes.
     * @return The unpacked text.
     */
    public static String unpackText(byte[] message) {
        int size = message.length;
        char[] decodeBuf = new char[size * 2];
        int idx = 0, highNibble = -1;
        for (int i = 0; i < size * 2; i++) {
            int val = message[i / 2] >> (4 - 4 * (i % 2)) & 0xf;
            if (highNibble == -1) {
                if (val < 13)
                    decodeBuf[idx++] = FREQUENCY_ORDERED_CHARS[val];
                else
                    highNibble = val;
            } else {
                decodeBuf[idx++] = FREQUENCY_ORDERED_CHARS[((highNibble << 4) + val) - 195];
                highNibble = -1;
            }
        }
        return new String(decodeBuf, 0, idx);
    }
    public static void packText(String str, ByteMessage buf) {
        if (str.length() > 80)
            str = str.substring(0, 80);
        str = str.toLowerCase();

        int carry = -1;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = 0;
            for (int j = 0; j < FREQUENCY_ORDERED_CHARS.length; j++) {
                if (c != FREQUENCY_ORDERED_CHARS[j])
                    continue;
                index = j;
                break;
            }

            if (index > 12)
                index += 195;
            if (carry == -1) {
                if (index < 13)
                    carry = index;
                else
                    buf.put(index);
            } else if (index < 13) {
                buf.put((carry << 4) + index);
                carry = -1;
            } else {
                buf.put((carry << 4) + (index >> 4));
                carry = index & 0xf;
            }
        }

        if (carry != -1)
            buf.put(carry << 4);
    }
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
            name[11 - offset++] = VALID_CHARACTERS[(int) (n - value * 37L)];
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
        if (s.isEmpty()) {
            return s;
        }
        
        StringBuilder builder = new StringBuilder(s);
        builder.setCharAt(0, Character.toUpperCase(s.charAt(0)));
        return builder.toString();
    }
}
