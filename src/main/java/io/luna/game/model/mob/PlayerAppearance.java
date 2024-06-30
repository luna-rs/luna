package io.luna.game.model.mob;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import io.luna.game.model.mob.inter.StandardInterface;
import io.luna.util.RandomUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

/**
 * A model containing player appearance data.
 *
 * @author lare96 
 */
public final class PlayerAppearance {

    /**
     * A {@link StandardInterface} implementation representing the Player appearance design interface.
     */
    public static final class DesignPlayerInterface extends StandardInterface {

        /**
         * Creates a new {@link DesignPlayerInterface}.
         */
        public DesignPlayerInterface() {
            super(3559);
        }
    }

    /**
     * The male identifier.
     */
    public static final int GENDER_MALE = 0;

    /**
     * The female identifier.
     */
    public static final int GENDER_FEMALE = 1;

    /**
     * The gender index.
     */
    public static final int GENDER = 0;

    /**
     * The head index.
     */
    public static final int HEAD = 1;

    /**
     * The beard index.
     */
    public static final int BEARD = 2;

    /**
     * The chest index.
     */
    public static final int CHEST = 3;

    /**
     * The arms index.
     */
    public static final int ARMS = 4;

    /**
     * The hands index.
     */
    public static final int HANDS = 5;

    /**
     * The legs index.
     */
    public static final int LEGS = 6;

    /**
     * The feet index.
     */
    public static final int FEET = 7;

    /**
     * The hair color index.
     */
    public static final int HAIR_COLOR = 8;

    /**
     * The torso color index.
     */
    public static final int TORSO_COLOR = 9;

    /**
     * The leg color index.
     */
    public static final int LEG_COLOR = 10;

    /**
     * The feet color index.
     */
    public static final int FEET_COLOR = 11;

    /**
     * The skin color index.
     */
    public static final int SKIN_COLOR = 12;

    /**
     * The default appearance set.
     */
    private static final int[] DEFAULT_APPEARANCE = {0, 0, 10, 18, 26, 33, 36, 42, 0, 0, 0, 0, 0};

    /**
     * The valid gender values.
     */
    private static final Range<Integer> VALID_GENDER = Range.closed(0, 1);

    /**
     * The valid male and female model values.
     */
    private static final ImmutableTable<Integer, Integer, Range<Integer>> VALID_MODELS;

    /**
     * An immutable map containing the valid color values.
     */
    private static final ImmutableMap<Integer, Range<Integer>> VALID_COLORS;

    public static int[] randomValues() {
        int[] appearance = new int[13];
        int index = 0;
        int gender = ThreadLocalRandom.current().nextInt(3) == 1 ? 1 : 0;
        appearance[index++] = gender;
        for (int loops = 0; loops < 7; loops++) {
            appearance[index] = RandomUtils.random(VALID_MODELS.get(gender, index++));
        }
        for (int loops = 0; loops < 5; loops++) {
            appearance[index] = RandomUtils.random(VALID_COLORS.get(index++));
        }
        return appearance;
    }

    static {
        // Initialize and cache valid appearance values.
        Table<Integer, Integer, Range<Integer>> models = HashBasedTable.create();
        models.put(GENDER_MALE, HEAD, Range.closed(0, 8));
        models.put(GENDER_FEMALE, HEAD, Range.closed(45, 54));

        models.put(GENDER_MALE, BEARD, Range.closed(10, 17));
        models.put(GENDER_FEMALE, BEARD, Range.singleton(-1));

        models.put(GENDER_MALE, CHEST, Range.closed(18, 25));
        models.put(GENDER_FEMALE, CHEST, Range.closed(56, 60));

        models.put(GENDER_MALE, ARMS, Range.closed(26, 31));
        models.put(GENDER_FEMALE, ARMS, Range.closed(61, 65));

        models.put(GENDER_MALE, HANDS, Range.closed(33, 34));
        models.put(GENDER_FEMALE, HANDS, Range.closed(67, 68));

        models.put(GENDER_MALE, LEGS, Range.closed(36, 40));
        models.put(GENDER_FEMALE, LEGS, Range.closed(70, 77));

        models.put(GENDER_MALE, FEET, Range.closed(42, 43));
        models.put(GENDER_FEMALE, FEET, Range.closed(79, 80));
        VALID_MODELS = ImmutableTable.copyOf(models);

        Map<Integer, Range<Integer>> colors = new HashMap<>();
        colors.put(HAIR_COLOR, Range.closed(0, 11));
        colors.put(TORSO_COLOR, Range.closed(0, 15));
        colors.put(LEG_COLOR, Range.closed(0, 15));
        colors.put(FEET_COLOR, Range.closed(0, 5));
        colors.put(SKIN_COLOR, Range.closed(0, 7));
        VALID_COLORS = ImmutableMap.copyOf(colors);
    }

    /**
     * An array of appearance values.
     */
    private int[] appearance;

    /**
     * Creates a new {@link PlayerAppearance}.
     */
    public PlayerAppearance() {
        // Populate the appearance array with the default values.
        this.appearance = Arrays.copyOf(DEFAULT_APPEARANCE, DEFAULT_APPEARANCE.length);
    }

    /**
     * Determines if {@code value} is a valid gender.
     *
     * @param value The gender value.
     * @return {@code true} if {@code value} is a valid gender.
     */
    public static boolean isGenderValid(int value) {
        return VALID_GENDER.contains(value);
    }

    /**
     * Determines if {@code value} is a valid color for body part {@code id}.
     *
     * @param id The body part identifier.
     * @param value The color value.
     * @return {@code true} if {@code value} is a valid color for body part {@code id}.
     */
    public static boolean isColorValid(int id, int value) {
        Range<Integer> validColors = VALID_COLORS.get(id);
        return validColors != null && validColors.contains(value);
    }

    /**
     * Determines if {@code value} is a valid model for {@code gender} and body part
     * {@code model}.
     *
     * @param model The body part identifier.
     * @param gender The gender value.
     * @param value The model value.
     * @return {@code true} if {@code value} is a valid model for {@code gender} and body part
     * {@code model}.
     */
    public static boolean isModelValid(int model, int gender, int value) {
        Range<Integer> validModels = VALID_MODELS.get(gender, model);
        return validModels != null && validModels.contains(value);
    }

    /**
     * Determines if {@code id}, {@code gender} and {@code value} are valid according to the
     * {@link #isGenderValid(int)}, {@link #isColorValid(int, int)}, or {@link #isColorValid(int, int)}
     * methods.
     *
     * @param index The index.
     * @param gender The gender value.
     * @param value The value.
     * @return {@code true} if the values are valid.
     */
    public static boolean isAnyValid(int index, int gender, int value) {
        if (index == 0) {
            return isGenderValid(value);
        } else if (index >= 1 && index <= 7) {
            return isModelValid(index, gender, value);
        } else if (index >= 8 && index <= 12) {
            return isColorValid(index, value);
        }
        throw new IllegalArgumentException("Invalid id value, must be >= 0 || <= 12.");
    }

    /**
     * Determines if an appearance set is valid.
     *
     * @param appearance The appearance set to check.
     * @return {@code true} if the set is valid.
     */
    public static boolean isAllValid(int[] appearance) {
        checkArgument(appearance.length == 13, "Invalid appearance set length.");
        return IntStream.range(0, appearance.length).allMatch(index ->
                isAnyValid(index, appearance[GENDER], appearance[index]));
    }

    /**
     * Retrieves the value for {@code index}.
     *
     * @param index The identifier.
     * @return The value.
     */
    public int get(int index) {
        return appearance[index];
    }

    /**
     * Sets {@code index} to {@code value}.
     *
     * @param index The index to set.
     * @param value The value to set.
     */
    public void set(int index, int value) {
        checkArgument(isAnyValid(index, get(GENDER), value), "Invalid id and value pair.");
        appearance[index] = value;
    }

    /**
     * @return {@code true} if the gender value equals {@code GENDER_MALE}.
     */
    public boolean isMale() {
        return get(GENDER) == GENDER_MALE;
    }

    /**
     * @return {@code true} if the gender value equals {@code GENDER_FEMALE}.
     */
    public boolean isFemale() {
        return !isMale();
    }

    /**
     * Sets the array of appearance values through a memory-copy.
     *
     * @param newValues The new values to set.
     */
    public void setValues(int[] newValues) {
        checkArgument(isAllValid(newValues), "Invalid appearance array.");
        appearance = Arrays.copyOf(newValues, newValues.length);
    }

    /**
     * @return A deep-copy of the appearance array.
     */
    public int[] toArray() {
        return Arrays.copyOf(appearance, appearance.length);
    }
}