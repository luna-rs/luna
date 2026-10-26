package io.luna.game.model.mob.dialogue;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Player;
import io.luna.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A static-utility class that contains functions for handling dialogues.
 *
 * @author lare96
 */
public final class DialogueUtils {

    /**
     * The pixel width of each printable character in the chat head font, starting from the space character.
     */
    private static final int[] CHAT_CHARACTER_WIDTHS = {
            7, 4, 6, 8, 8, 14, 11, 3, 4, 4, 9, 7, 3, 5, 4, 7, 9, 6, 9, 9, 8, 9, 9, 7, 9, 9, 4, 4, 6, 6, 6, 7,
            14, 9, 11, 10, 13, 12, 10, 11, 12, 7, 8, 12, 9, 15, 14, 12, 12, 12, 12, 9, 12, 11, 11, 16, 12, 11, 10, 4,
            7, 4, 5, 8, 4, 7, 7, 7, 8, 7, 5, 8, 7, 3, 4, 7, 3, 12, 9, 7, 7, 7, 7, 6, 5, 9, 7, 10, 8, 9, 7, 5, 3, 5, 8
    };

    /**
     * The widest a line of chat head text can be, in pixels.
     */
    private static final int CHAT_LINE_WIDTH = 380;

    /**
     * An immutable list of "Make item" model widgets.
     */
    private static final ImmutableList<int[]> MAKE_ITEM_MODELS = ImmutableList.of(
            new int[]{8884},
            new int[]{8869, 8870},
            new int[]{8883, 8884, 8885},
            new int[]{8902, 8903, 8904, 8905},
            new int[]{8941, 8942, 8943, 8944, 8945}
    );

    /**
     * An immutable list of "Make item" text widgets.
     */
    private static final ImmutableList<int[]> MAKE_ITEM_TEXTS = ImmutableList.of(
            new int[]{8893},
            new int[]{8873, 8876},
            new int[]{8889, 8893, 8897},
            new int[]{8908, 8912, 8916, 8920},
            new int[]{8948, 8952, 8956, 8960, 8964}
    );

    /**
     * An immutable list of option dialogue sword layers. The first layer frames the default title. The second is
     * hidden by default and spaced wider for longer titles.
     */
    private static final ImmutableList<int[]> OPTION_SWORDS = ImmutableList.of(
            new int[]{14449, 14450},
            new int[]{2476, 2479},
            new int[]{8215, 8216},
            new int[]{8228, 8229}
    );

    /**
     * Fix for the make item 5-options interface dialogue. Reduces the name by one word and makes it white.
     *
     * @param str The item name.
     * @return The fixed item name.
     */
    public static String makeItem5OptionsNameFix(String str) {
        String[] parts = str.split(" ");
        if (parts.length > 1) {
            StringBuilder strBuilder = new StringBuilder("@whi@");
            for (int index = 1; index < parts.length; index++) {
                String nextPart = parts[index];
                strBuilder.append(StringUtils.capitalize(nextPart)).append(' ');
            }
            return strBuilder.toString().trim();
        }
        return "@whi@" + str;
    }

    /**
     * Returns the correct NPC dialogue based on the length.
     *
     * @param length The length.
     * @return The dialogue.
     */
    public static int npcDialogue(int length) {
        switch (length) {
            case 1:
                return 4882;
            case 2:
                return 4887;
            case 3:
                return 4893;
            case 4:
                return 4900;
            default:
                throw new IllegalArgumentException("Length must be between 1 and 4.");
        }
    }

    /**
     * Returns the correct Player dialogue based on the length.
     *
     * @param length The length.
     * @return The dialogue.
     */
    public static int playerDialogue(int length) {
        switch (length) {
            case 1:
                return 968;
            case 2:
                return 973;
            case 3:
                return 979;
            case 4:
                return 986;
            default:
                throw new IllegalArgumentException("Length must be between 1 and 4.");
        }
    }

    /**
     * Returns the correct option dialogue based on the length.
     *
     * @param length The length.
     * @return The dialogue.
     */
    public static int optionDialogue(int length) {
        switch (length) {
            case 2:
                return 14443;
            case 3:
                return 2469;
            case 4:
                return 8207;
            case 5:
                return 8219;
            default:
                throw new IllegalArgumentException("Length must be between 2 and 5.");
        }
    }

    /**
     * Returns the correct option dialogue sword layers based on the length.
     *
     * @param length The length.
     * @return The default layer, then the wide layer.
     */
    public static int[] optionSwordLayers(int length) {
        checkArgument(length >= 2 && length <= 5, "Length must be between 2 and 5.");
        return OPTION_SWORDS.get(length - 2);
    }

    /**
     * Returns the correct text dialogue based on the length.
     *
     * @param length The length.
     * @return The dialogue.
     */
    public static int textDialogue(int length) {
        switch (length) {
            case 1:
                return 356;
            case 2:
                return 359;
            case 3:
                return 363;
            case 4:
                return 368;
            case 5:
                return 374;
            default:
                throw new IllegalArgumentException("Length must be between 1 and 5.");
        }
    }

    /**
     * Returns the correct make item dialogue based on the length.
     *
     * @param length The length.
     * @return The dialogue.
     */
    public static int makeItemDialogue(int length) {
        switch (length) {
            case 1:
            case 3:
                return 8880;
            case 2:
                return 8866;
            case 4:
                return 8899;
            case 5:
                return 8938;
            default:
                throw new IllegalArgumentException("Length must be between 1 and 5.");
        }
    }

    /**
     * Returns the correct make item model widgets based on the length.
     *
     * @param length The length.
     * @return The model widgets.
     */
    public static int[] makeItemModelWidgets(int length) {
        checkArgument(length >= 1 && length <= 5, "Length must be between 1 and 5.");
        return MAKE_ITEM_MODELS.get(length - 1);
    }

    /**
     * Returns the correct make item text widgets based on the length.
     *
     * @param length The length.
     * @return The text widgets.
     */
    public static int[] makeItemTextWidgets(int length) {
        checkArgument(length >= 1 && length <= 5, "Length must be between 1 and 5.");
        return MAKE_ITEM_TEXTS.get(length - 1);
    }

    /**
     * Closes the currently open dialogue. This is supposed to be used functionally within dialogues.
     * <pre>
     * {@code player.newDialogue().
     *   npcChat(1, "Hi, would you like to logout?").
     *   options("Yes.", Player::logout,
     *           "No.", DialogueUtils::close).open();
     * }
     * </pre>
     *
     * @param player The player.
     */
    public static void close(Player player) {
        player.getOverlays().closeWindows();
    }

    /**
     * Prevent instantiation.
     */
    private DialogueUtils() {
    }

    /**
     * Splits {@code text} into chat head lines the way the game does, starting a new line whenever the next word
     * wouldn't fit. Only needed where the text isn't known ahead of time, such as text that includes a name.
     *
     * @param text The text to split.
     * @return The lines.
     */
    public static String[] splitChatLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String longer = line.length() == 0 ? word : line + " " + word;
            if (line.length() == 0 || computeChatWidth(longer) <= CHAT_LINE_WIDTH) {
                line = new StringBuilder(longer);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        lines.add(line.toString());
        return lines.toArray(String[]::new);
    }

    /**
     * Computes how wide {@code text} is in the chat head font, in pixels.
     *
     * @param text The text.
     * @return The width.
     */
    private static int computeChatWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            int character = text.charAt(index) - 32;
            width += character >= 0 && character < CHAT_CHARACTER_WIDTHS.length ? CHAT_CHARACTER_WIDTHS[character] : 3;
        }
        return width;
    }
}
