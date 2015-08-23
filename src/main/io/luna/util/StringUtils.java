package io.luna.util;

import java.util.List;

/**
 * A utility class that provides functionality for manipulating strings.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public class StringUtils {

    /**
     * Joins the {@code elements} using a ',' as the delimiter, with
     * {@code prefix} and {@code suffix}. On the last element, an "and" is
     * added.
     * 
     * @param prefix
     *            The prefix of the String.
     * @param suffix
     *            The suffix of the String.
     * @param elements
     *            The String elements to join together.
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
     * A private constructor to discourage external instantiation.
     */
    private StringUtils() {}
}
