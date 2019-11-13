package io.luna.util;

import com.google.common.collect.ImmutableList;
import io.luna.game.model.mob.Player;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A static-utility class that contains functions for handling dialogues.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class DialogueUtils {

    /**
     * An immutable list of "Make item" model widgets.
     */
    private static final ImmutableList<int[]> MAKE_ITEM_MODELS = ImmutableList.of(
        new int[] {8884},
        new int[] {8869, 8870},
        new int[] {8883, 8884, 8885},
        new int[] {8902, 8903, 8904, 8905},
        new int[] {8941, 8942, 8943, 8944, 8945}
    );

    /**
     * An immutable list of "Make item" text widgets.
     */
    private static final ImmutableList<int[]> MAKE_ITEM_TEXTS = ImmutableList.of(
        new int[] {8893},
        new int[] {8873, 8876},
        new int[] {8889, 8893, 8897},
        new int[] {8908, 8912, 8916, 8920},
        new int[] {8948, 8952, 8956, 8960, 8964}
    );

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
                return 8880;
            case 2:
                return 8866;
            case 3:
                return 8880;
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
        player.getInterfaces().close();
    }

    /**
     * Prevent instantiation.
     */
    private DialogueUtils() {
    }
}