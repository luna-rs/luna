package io.luna.game.model.mob.block;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import io.luna.game.model.mob.overlay.StandardInterface;
import io.luna.util.RandomUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Encapsulates the 13-slot appearance array used by the player appearance system.
 * <p>
 * The underlying {@code int[]} has a fixed layout:
 * </p>
 * <pre>
 * [0] {@link #GENDER}
 * [1] {@link #HEAD}
 * [2] {@link #BEARD}
 * [3] {@link #CHEST}
 * [4] {@link #ARMS}
 * [5] {@link #HANDS}
 * [6] {@link #LEGS}
 * [7] {@link #BOOTS}
 * [8] {@link #HAIR_COLOR}
 * [9] {@link #TORSO_COLOR}
 * [10] {@link #LEG_COLOR}
 * [11] {@link #BOOTS_COLOR}
 * [12] {@link #SKIN_COLOR}
 * </pre>
 *
 * @author lare96
 */
public final class PlayerAppearance {

    /**
     * A {@link StandardInterface} implementation representing the player appearance design interface (the character
     * creation screen).
     */
    public static final class DesignPlayerInterface extends StandardInterface {

        /**
         * Creates a new {@link DesignPlayerInterface} bound to the appearance design interface ID ({@code 3559}).
         */
        public DesignPlayerInterface() {
            super(3559);
        }
    }

    /**
     * Gender value representing male.
     */
    public static final int GENDER_MALE = 0;

    /**
     * Gender value representing female.
     */
    public static final int GENDER_FEMALE = 1;

    /**
     * Array index for the gender field.
     */
    public static final int GENDER = 0;

    /**
     * Array index for the head model field.
     */
    public static final int HEAD = 1;

    /**
     * Array index for the beard model field.
     */
    public static final int BEARD = 2;

    /**
     * Array index for the chest model field.
     */
    public static final int CHEST = 3;

    /**
     * Array index for the arms model field.
     */
    public static final int ARMS = 4;

    /**
     * Array index for the hands model field.
     */
    public static final int HANDS = 5;

    /**
     * Array index for the legs model field.
     */
    public static final int LEGS = 6;

    /**
     * Array index for the boots model field.
     */
    public static final int BOOTS = 7;

    /**
     * Array index for the hair color field.
     */
    public static final int HAIR_COLOR = 8;

    /**
     * Array index for the torso color field.
     */
    public static final int TORSO_COLOR = 9;

    /**
     * Array index for the leg color field.
     */
    public static final int LEG_COLOR = 10;

    /**
     * Array index for the boots color field.
     */
    public static final int BOOTS_COLOR = 11;

    /**
     * Array index for the skin color field.
     */
    public static final int SKIN_COLOR = 12;

    /**
     * The default appearance set (male by default).
     * <p>
     * These values correspond to the lowest valid model IDs and color indices for a standard male character. The
     * constructor copies this list into the internal appearance array.
     * </p>
     */
    private static final ImmutableList<Integer> DEFAULT_APPEARANCE =
            ImmutableList.of(0, 0, 10, 18, 26, 33, 36, 42, 0, 0, 0, 0, 0);

    /**
     * Valid range of gender values ({@link #GENDER_MALE} or {@link #GENDER_FEMALE}).
     */
    private static final Range<Integer> VALID_GENDER = Range.closed(0, 1);

    /**
     * Valid model ranges for each (gender, body-part index) pair.
     * <p>
     * This table is keyed by:
     * </p>
     * <ul>
     *     <li>Row key = gender ({@link #GENDER_MALE}, {@link #GENDER_FEMALE}).</li>
     *     <li>Column key = model index ({@link #HEAD} through {@link #BOOTS}).</li>
     *     <li>Value = valid {@link Range} of model IDs for that body part.</li>
     * </ul>
     */
    private static final ImmutableTable<Integer, Integer, Range<Integer>> VALID_MODELS;

    /**
     * Valid color ranges for each color index.
     * <p>
     * Keys are the color indices ({@link #HAIR_COLOR} through {@link #SKIN_COLOR}), and values are the corresponding
     * allowed {@link Range} of color IDs.
     * </p>
     */
    private static final ImmutableMap<Integer, Range<Integer>> VALID_COLORS;

    /**
     * Returns a freshly generated, random appearance array.
     * <p>
     * The method randomly chooses:
     * </p>
     * <ol>
     *     <li>A gender.</li>
     *     <li>A model ID for each body part (head, beard, chest, etc) using {@link #VALID_MODELS}.</li>
     *     <li>A color ID for each color field using {@link #VALID_COLORS}.</li>
     * </ol>
     *
     * @return A new appearance array of length {@code 13}.
     */
    public static int[] random() {
        int[] appearance = new int[13];
        int index = 0;
        int gender = RandomUtils.randomBoolean() ? 1 : 0;
        appearance[index++] = gender;
        for (int loops = 0; loops < 7; loops++) {
            Range<Integer> nextModels = VALID_MODELS.get(gender, index);
            appearance[index++] = RandomUtils.random(Objects.requireNonNull(nextModels));
        }
        for (int loops = 0; loops < 5; loops++) {
            Range<Integer> nextColors = VALID_COLORS.get(index);
            appearance[index++] = RandomUtils.random(Objects.requireNonNull(nextColors));
        }
        return appearance;
    }

    /**
     * Determines if a gender value is valid.
     *
     * @param value The gender value.
     * @return {@code true} if {@code value} is within the allowed gender range.
     */
    public static boolean isGenderValid(int value) {
        return VALID_GENDER.contains(value);
    }

    /**
     * Determines if a color value is valid for a given color index.
     *
     * @param id The color index ({@link #HAIR_COLOR}..{@link #SKIN_COLOR}).
     * @param value The color value to test.
     * @return {@code true} if the color index has a defined range and {@code value} is within it.
     */
    public static boolean isColorValid(int id, int value) {
        Range<Integer> validColors = VALID_COLORS.get(id);
        return validColors != null && validColors.contains(value);
    }

    /**
     * Determines if a model value is valid for a given gender and body-part index.
     *
     * @param model The model index ({@link #HEAD}..{@link #BOOTS}).
     * @param gender The gender value.
     * @param value The model ID to test.
     * @return {@code true} if a range exists for the given (gender, model) pair and
     * {@code value} is within that range.
     */
    public static boolean isModelValid(int model, int gender, int value) {
        Range<Integer> validModels = VALID_MODELS.get(gender, model);
        return validModels != null && validModels.contains(value);
    }

    /**
     * Determines if the given (index, gender, value) triple is valid.
     * <p>
     * This dispatches to:
     * </p>
     * <ul>
     *     <li>{@link #isGenderValid(int)} for {@link #GENDER}.</li>
     *     <li>{@link #isModelValid(int, int, int)} for model indices {@link #HEAD}..{@link #BOOTS}.</li>
     *     <li>{@link #isColorValid(int, int)} for color indices {@link #HAIR_COLOR}..{@link #SKIN_COLOR}.</li>
     * </ul>
     *
     * @param index The appearance index being tested.
     * @param gender The gender value.
     * @param value The value at that index.
     * @return {@code true} if the triple is valid according to the mapped ranges.
     */
    public static boolean isAnyValid(int index, int gender, int value) {
        if (index == 0) {
            return isGenderValid(value);
        } else if (index >= 1 && index <= 7) {
            return isModelValid(index, gender, value);
        } else if (index >= 8 && index <= 12) {
            return isColorValid(index, value);
        }
        throw new IllegalArgumentException("Invalid id value, must be >= 0 && <= 12.");
    }

    /**
     * Determines if an entire appearance array is valid.
     *
     * @param appearance The appearance set to check.
     * @return {@code true} if all indices in {@code appearance} are valid for the array's gender.
     * @throws IllegalArgumentException If the array length is not exactly {@code 13}.
     */
    public static boolean isAllValid(int[] appearance) {
        checkArgument(appearance.length == 13, "Invalid appearance set length.");
        return IntStream.range(0, appearance.length).allMatch(index ->
                isAnyValid(index, appearance[GENDER], appearance[index]));
    }

    static {
        // Initialize and cache valid appearance values for models.
        Table<Integer, Integer, Range<Integer>> models = HashBasedTable.create();

        // Head models.
        models.put(GENDER_MALE, HEAD, Range.closed(0, 8));
        models.put(GENDER_FEMALE, HEAD, Range.closed(45, 54));

        // Beard models.
        models.put(GENDER_MALE, BEARD, Range.closed(10, 17));
        // A singleton 0 range is used to represent "no beard" for females.
        models.put(GENDER_FEMALE, BEARD, Range.singleton(0));

        // Chest models.
        models.put(GENDER_MALE, CHEST, Range.closed(18, 25));
        models.put(GENDER_FEMALE, CHEST, Range.closed(56, 60));

        // Arm models.
        models.put(GENDER_MALE, ARMS, Range.closed(26, 31));
        models.put(GENDER_FEMALE, ARMS, Range.closed(61, 65));

        // Hand models.
        models.put(GENDER_MALE, HANDS, Range.closed(33, 34));
        models.put(GENDER_FEMALE, HANDS, Range.closed(67, 68));

        // Leg models.
        models.put(GENDER_MALE, LEGS, Range.closed(36, 40));
        models.put(GENDER_FEMALE, LEGS, Range.closed(70, 77));

        // Boots models.
        models.put(GENDER_MALE, BOOTS, Range.closed(42, 43));
        models.put(GENDER_FEMALE, BOOTS, Range.closed(79, 80));

        VALID_MODELS = ImmutableTable.copyOf(models);

        // Initialize and cache valid color values.
        Map<Integer, Range<Integer>> colors = new HashMap<>();
        colors.put(HAIR_COLOR, Range.closed(0, 11));
        colors.put(TORSO_COLOR, Range.closed(0, 15));
        colors.put(LEG_COLOR, Range.closed(0, 15));
        colors.put(BOOTS_COLOR, Range.closed(0, 5));
        colors.put(SKIN_COLOR, Range.closed(0, 7));
        VALID_COLORS = ImmutableMap.copyOf(colors);
    }

    /**
     * Backing array holding the appearance values in the fixed layout documented above.
     */
    private int[] appearance;

    /**
     * Creates a new {@link PlayerAppearance} initialized to {@link #DEFAULT_APPEARANCE}.
     */
    public PlayerAppearance() {
        appearance = Ints.toArray(DEFAULT_APPEARANCE);
    }

    /**
     * Retrieves the raw appearance value at a given index.
     *
     * @param index The index to read (0..12).
     * @return The stored value at {@code index}.
     */
    public int get(int index) {
        return appearance[index];
    }

    /**
     * Sets the value at a given index, enforcing validity.
     *
     * @param index The appearance index to set.
     * @param value The new value.
     * @throws IllegalArgumentException If {@code (index, gender, value)} is invalid.
     */
    public void set(int index, int value) {
        checkArgument(isAnyValid(index, get(GENDER), value), "Invalid id and value pair.");
        appearance[index] = value;
    }

    /**
     * Returns {@code true} if the gender is {@link #GENDER_MALE}.
     *
     * @return {@code true} if this appearance is male.
     */
    public boolean isMale() {
        return get(GENDER) == GENDER_MALE;
    }

    /**
     * Returns {@code true} if the gender is {@link #GENDER_FEMALE}.
     *
     * @return {@code true} if this appearance is female.
     */
    public boolean isFemale() {
        return !isMale();
    }

    /**
     * Replaces the entire appearance array with a deep copy of the provided values, after validating that the new
     * array is well-formed.
     *
     * @param newValues The new appearance values.
     * @throws IllegalArgumentException If {@code newValues} is not a valid appearance set.
     */
    public void setValues(int[] newValues) {
        checkArgument(isAllValid(newValues), "Invalid appearance array.");
        appearance = Arrays.copyOf(newValues, newValues.length);
    }

    /**
     * Returns a deep copy of the current appearance array.
     *
     * @return A new array containing the current appearance values.
     */
    public int[] toArray() {
        return Arrays.copyOf(appearance, appearance.length);
    }
}
