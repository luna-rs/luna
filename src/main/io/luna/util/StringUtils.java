package io.luna.util;

import java.util.List;

public class StringUtils {

    // TODO: Documentation.

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

}
