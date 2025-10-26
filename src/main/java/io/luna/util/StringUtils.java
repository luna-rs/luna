package io.luna.util;

import com.google.common.base.Joiner;
import io.luna.net.codec.ByteMessage;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A static utility class containing helper methods for manipulating, encoding, and formatting strings.
 * <p>
 * This class includes tools for base-37 name encoding/decoding, text compression used in RuneScape’s chat
 * system, article and plural computation, IP address packing, and general string formatting operations.
 * </p>
 * All methods are stateless and thread-safe.
 *
 * @author lare96
 */
public final class StringUtils {

    /**
     * A {@link Joiner} that concatenates elements using a comma and space ({@code ", "}). Null elements are
     * skipped automatically.
     */
    public static final Joiner COMMA_JOINER = Joiner.on(", ").skipNulls();

    /**
     * An immutable empty {@code String[]} used as a constant.
     */
    public static final String[] EMPTY_ARRAY = {};

    /**
     * A table of valid characters used for base-37 decoding and name parsing.
     */
    public static final char[] VALID_CHARACTERS = new char[]{
            '_', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
            'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', '!', '@', '#', '$', '%', '^', '&', '*',
            '(', ')', '-', '+', '=', ':', ';', '.', '>',
            '<', ',', '"', '[', ']', '|', '?', '/', '`'
    };

    /**
     * A table of characters ordered by frequency, used for RuneScape's text compression scheme.
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
     * Private constructor to prevent instantiation.
     */
    private StringUtils() {
    }

    /**
     * Unpacks text received from the client using RuneScape's text compression format.
     *
     * @param message The packed byte array.
     * @return The unpacked, readable string.
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

    /**
     * Packs a string into RuneScape's compressed text format for transmission to the client.
     * <p>
     * - Input strings longer than 80 characters are truncated.<br>
     * - Input is automatically lowercased.<br>
     * - The resulting bytes are written to the given {@link ByteMessage}.
     * </p>
     *
     * @param str The text to pack.
     * @param buf The destination buffer to write to.
     */
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
     * Computes the appropriate indefinite article ("a" or "an") for the given object.
     *
     * @param thing The object or word to evaluate.
     * @return Either {@code "a"} or {@code "an"} depending on the leading vowel.
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
     * Returns the provided word or object prefixed with its correct indefinite article ("a"/"an").
     *
     * @param thing The object or word to evaluate.
     * @return The full phrase with article included.
     */
    public static String addArticle(Object thing) {
        String asString = thing.toString();
        return computeArticle(asString) + " " + asString;
    }

    /**
     * Encodes a string into a base-37 {@code long} value used for player names in RuneScape’s protocol.
     * <p>
     * - Only the first 12 characters are encoded.<br>
     * - Letters are case-insensitive.<br>
     * - Unsupported characters are ignored.
     * </p>
     *
     * @param string The input string to encode.
     * @return The encoded 64-bit representation.
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
     * Decodes a base-37 encoded {@code long} value into its string representation.
     *
     * @param value The encoded long value.
     * @return The decoded string.
     * @throws IllegalArgumentException If the input value is outside the valid range.
     */
    public static String decodeFromBase37(long value) {
        checkArgument(value > 0L &&
                value < 6582952005840035281L &&
                value % 37L != 0L, "Invalid long value.");
        int offset = 0;
        char[] name = new char[12];
        while (value != 0L) {
            long n = value;
            value /= 37L;
            name[11 - offset++] = VALID_CHARACTERS[(int) (n - value * 37L)];
        }
        return new String(name, 12 - offset, offset);
    }

    /**
     * Capitalizes the first character of a string, preserving the rest of the text.
     *
     * @param s The string to capitalize.
     * @return The capitalized version, or the original string if empty.
     */
    public static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }

        StringBuilder builder = new StringBuilder(s);
        builder.setCharAt(0, Character.toUpperCase(s.charAt(0)));
        return builder.toString();
    }

    /**
     * Converts a dotted-decimal IP address (e.g., {@code "127.0.0.1"}) into a single packed integer.
     * <p>
     * Each section must be between 0 and 255, and the input must contain exactly four segments separated by dots.
     * </p>
     *
     * @param address The IP address to pack.
     * @return The packed 32-bit integer representation.
     * @throws IllegalStateException If the input is malformed or contains out-of-range values.
     */
    public static int packIpAddress(String address) {
        int start = 24;
        int minus = 8;
        int last = 0;
        String[] splitAddress = address.split("\\.");
        if (splitAddress.length != 4) {
            throw new IllegalStateException("Invalid IP address (does not contain 4 integers separated by '.')");
        }
        int[] splitIntAddress = new int[splitAddress.length];
        for (int index = 0; index < splitIntAddress.length; index++) {
            String section = splitAddress[index];
            int sectionInt = Integer.parseInt(section);
            if (sectionInt < 0 || sectionInt > 255) {
                throw new IllegalStateException("Invalid section! Integers within IP addresses must be between 0-255");
            }
            int total = sectionInt << start;
            start -= minus;
            splitIntAddress[index] = total;
        }
        for (int section : splitIntAddress) {
            last = last == 0 ? section : last | section;
        }
        return last;
    }

    /**
     * Adds an {@code 's'} to the end of a string if it is not already plural.
     * <p>
     * This method trims whitespace before evaluating.
     * </p>
     *
     * @param text The input word.
     * @return The pluralized form, or the original string if empty.
     */
    public static String addPlural(String text) {
        text = text.strip();
        if (text.isEmpty()) {
            return text;
        } else if (text.charAt(text.length() - 1) != 's') {
            return text + 's';
        } else return text;
    }
}
