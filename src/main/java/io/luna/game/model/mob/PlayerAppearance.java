package io.luna.game.model.mob;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Range;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model containing player appearance data.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerAppearance {

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
     * The chest index.
     */
    public static final int CHEST = 1;

    /**
     * The arms index.
     */
    public static final int ARMS = 2;

    /**
     * The legs index.
     */
    public static final int LEGS = 3;

    /**
     * The head index.
     */
    public static final int HEAD = 4;

    /**
     * The hands index.
     */
    public static final int HANDS = 5;

    /**
     * The feet index.
     */
    public static final int FEET = 6;

    /**
     * The beard index.
     */
    public static final int BEARD = 7;

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
    public static final ImmutableList<Integer> DEFAULT_APPEARANCE = ImmutableList
            .of(0, 18, 26, 36, 0, 33, 42, 10, 0, 0, 0, 0, 0);

    /**
     * The valid gender values.
     */
    private static final Range<Integer> VALID_GENDER = Range.closed(0, 1);

    /**
     * The valid male and female model values.
     */
    private static final ImmutableTable<Integer, Integer, Range<Integer>> VALID_MODELS;

    /**
     * The valid color values.
     */
    private static final ImmutableMap<Integer, Range<Integer>> VALID_COLORS;

    static {
        // Initialize and cache valid appearance values.
        Table<Integer, Integer, Range<Integer>> models = HashBasedTable.create();
        models.put(GENDER_MALE, HEAD, Range.closed(0, 8));
        models.put(GENDER_FEMALE, HEAD, Range.closed(45, 54));

        models.put(GENDER_MALE, BEARD, Range.closed(10, 17));
        models.put(GENDER_FEMALE, BEARD, Range.closed(-1, -1));

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
        throw new IllegalArgumentException("invalid id value, must be >= 0 || <= 12");
    }

    /**
     * Determines if an appearance set is valid.
     *
     * @param appearance The appearance set to check.
     * @return {@code true} if the set is valid.
     */
    public static boolean isAllValid(int[] appearance) {
        checkArgument(appearance.length == 13, "invalid appearance set length");

        for (int index = 0; index < appearance.length; index++) {
            if (!isAnyValid(index, appearance[GENDER], appearance[index])) {
                return false;
            }
        }
        return true;
    }

    /**
     * An array of appearance values.
     */
    private final int[] appearance = new int[DEFAULT_APPEARANCE.size()];

    /**
     * Creates a new {@link PlayerAppearance}.
     */
    public PlayerAppearance() {

        // Populate the appearance array with the default values.
        for (int index = 0; index < appearance.length; index++) {
            appearance[index] = DEFAULT_APPEARANCE.get(index);
        }
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
        checkArgument(isAnyValid(index, get(GENDER), value), "invalid id and value pair");
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
        checkArgument(isAllValid(newValues), "invalid appearance array");
        System.arraycopy(newValues, 0, appearance, 0, 13);
    }

    /**
     * Returns an immutable shallow-copy of the appearance array.
     *
     * @return An immutable list copy of the backing array.
     */
    public ImmutableList<Integer> toList() {
        return ImmutableList.copyOf(Ints.asList(appearance));
    }

    /**
     * Returns a mutable shallow-copy of the appearance array.
     *
     * @return A shallow-copy of the appearance array.
     */
    public int[] toArray() {
        return Arrays.copyOf(appearance, appearance.length);
    }
}