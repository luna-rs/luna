package io.luna.game.model.mobile;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * The container class that contains functions to handle the appearance of a {@link Player}.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerAppearance {

    /**
     * The male gender identifier.
     */
    public static final int GENDER_MALE = 0;

    /**
     * The female gender identifier.
     */
    public static final int GENDER_FEMALE = 1;

    /**
     * The gender appearance index.
     */
    public static final int GENDER = 0;

    /**
     * The chest appearance index.
     */
    public static final int CHEST = 1;

    /**
     * The arms appearance index.
     */
    public static final int ARMS = 2;
    /**
     * The legs appearance index.
     */
    public static final int LEGS = 3;
    /**
     * The head appearance index.
     */
    public static final int HEAD = 4;
    /**
     * The hands appearance index.
     */
    public static final int HANDS = 5;
    /**
     * The feet appearance index.
     */
    public static final int FEET = 6;
    /**
     * The beard appearance index.
     */
    public static final int BEARD = 7;
    /**
     * The hair color appearance index.
     */
    public static final int HAIR_COLOR = 8;
    /**
     * The torso color appearance index.
     */
    public static final int TORSO_COLOR = 9;
    /**
     * The leg color appearance index.
     */
    public static final int LEG_COLOR = 10;
    /**
     * The feet color appearance index.
     */
    public static final int FEET_COLOR = 11;
    /**
     * The skin color appearance index.
     */
    public static final int SKIN_COLOR = 12;

    /**
     * The default appearance values.
     */
    public static final ImmutableList<Integer> DEFAULT_APPEARANCE = ImmutableList
        .of(0, 18, 26, 36, 0, 33, 42, 10, 0, 0, 0, 0, 0);

    /**
     * An {@link ImmutableListMultimap} containing all of the valid appearance model and color values.
     */
    private static final ImmutableListMultimap<Integer, Range<Integer>> VALID_VALUES;

    static {
        ArrayListMultimap<Integer, Range<Integer>> values = ArrayListMultimap.create();

        values.put(GENDER, Range.closed(0, 1));
        values.put(HEAD, Range.closed(0, 8));
        values.put(HEAD, Range.closed(45, 54));
        values.put(BEARD, Range.closed(10, 17));
        values.put(BEARD, Range.closed(-1, -1));
        values.put(CHEST, Range.closed(18, 25));
        values.put(CHEST, Range.closed(56, 60));
        values.put(ARMS, Range.closed(26, 31));
        values.put(ARMS, Range.closed(61, 65));
        values.put(HANDS, Range.closed(33, 34));
        values.put(HANDS, Range.closed(67, 68));
        values.put(LEGS, Range.closed(36, 40));
        values.put(LEGS, Range.closed(70, 77));
        values.put(FEET, Range.closed(42, 43));
        values.put(FEET, Range.closed(79, 80));
        values.put(HAIR_COLOR, Range.closed(0, 11));
        values.put(TORSO_COLOR, Range.closed(0, 15));
        values.put(LEG_COLOR, Range.closed(0, 15));
        values.put(FEET_COLOR, Range.closed(0, 5));
        values.put(SKIN_COLOR, Range.closed(0, 7));

        VALID_VALUES = ImmutableListMultimap.copyOf(values);
    }

    /**
     * Determines the validity of gender values.
     *
     * @return {@code true} if {@code value} is a valid gender value, {@code false} otherwise.
     */
    public static boolean isGenderValid(int value) {
        List<Range<Integer>> validRanges = VALID_VALUES.get(GENDER);
        return validRanges.get(0).contains(value);
    }

    /**
     * Determines the validity of color values.
     *
     * @return {@code true} if {@code value} is a valid color value in the {@code id} index, {@code false} otherwise.
     */
    public static boolean isColorValid(int id, int value) {
        checkArgument(id >= 8 && id <= 12, "invalid index identifier value");

        List<Range<Integer>> validRanges = VALID_VALUES.get(id);
        return validRanges.get(0).contains(value);
    }

    /**
     * Determines the validity of model values on men and women.
     *
     * @return {@code true} if {@code value} is a valid model value for {@code gender} in the {@code id} index, {@code false}
     * otherwise.
     */
    public static boolean isModelValid(int id, int gender, int value) {
        checkArgument(isGenderValid(gender), "invalid gender value");
        checkArgument(id >= 1 && id <= 7, "invalid index identifier value");

        List<Range<Integer>> validRanges = VALID_VALUES.get(id);
        return validRanges.get(gender).contains(value);
    }

    /**
     * Determines if the {@code gender}, and {@code value} values are valid according to one of the {@code isGenderValid},
     * {@code isModelValid}, or {@code isColorValid} methods. The method chosen is determined by the value of the {@code
     * id}.
     *
     * @param id The index to validate these values for.
     * @param gender The gender value that may be validated.
     * @param value The actual value that may be validated.
     * @return {@code true} if valid, {@code false} otherwise.
     */
    public static boolean isAnyValid(int id, int gender, int value) {
        if (id == 0) {
            return isGenderValid(value);
        } else if (id >= 1 && id <= 7) {
            return isModelValid(id, gender, value);
        } else if (id >= 8 && id <= 12) {
            return isColorValid(id, value);
        }
        throw new IllegalArgumentException("invalid id value, must be >= 0 || <= 12");
    }

    /**
     * Determines if an array of appearance values is completely valid. The length of the passed array must be {@code 13} or
     * an {@link IllegalArgumentException} will be thrown.
     *
     * @param appearance The array of appearance values to validate.
     * @return {@code true} if completely valid, {@code false} otherwise.
     */
    public static boolean isAllValid(int[] appearance) {
        checkArgument(appearance.length == 13);

        int index = 0;
        int gender = appearance[GENDER];

        for (int val : appearance) {
            if (!isAnyValid(index, gender, val)) {
                return false;
            }
            index++;
        }
        return true;
    }

    /**
     * An array of appearance values used for updating.
     */
    private final int[] appearance = new int[13];

    /**
     * Creates a new {@link PlayerAppearance} with the default appearance values.
     */
    public PlayerAppearance() {
        int index = 0;
        for (int val : DEFAULT_APPEARANCE) {
            appearance[index] = val;
            index++;
        }
    }

    /**
     * Retrieves a single value from the backing appearance array.
     */
    public int get(int id) {
        return appearance[id];
    }

    /**
     * Sets a single value in the backing appearance array.
     */
    public void set(int id, int value) {
        checkArgument(isAnyValid(id, get(GENDER), value), "invalid id and value pair");
        appearance[id] = value;
    }

    /**
     * @return {@code true} if the gender value is equal to {@code GENDER_MALE}, {@code false} otherwise.
     */
    public boolean isMale() {
        return get(GENDER) == GENDER_MALE;
    }

    /**
     * @return {@code true} if the gender value is equal to {@code GENDER_FEMALE}, {@code false} otherwise.
     */
    public boolean isFemale() {
        return !isMale();
    }

    /**
     * Sets the array of appearance values used for updating.
     */
    public void setValues(int[] newValues) {
        checkArgument(isAllValid(newValues), "invalid appearance array");
        System.arraycopy(newValues, 0, appearance, 0, 13);
    }

    /**
     * <strong>Please note that this function does not give direct access to the backing array but instead creates a shallow
     * copy.</strong>
     *
     * @return The array of appearance values used for updating, as an {@link ImmutableList}.
     */
    public ImmutableList<Integer> toList() {
        return ImmutableList.copyOf(Ints.asList(appearance));
    }

    /**
     * <strong>Please note that this function does not give direct access to the backing array but instead creates a shallow
     * copy.</strong>
     *
     * @return The array of appearance values used for updating, as a mutable array.
     */
    public int[] toArray() {
        return Arrays.copyOf(appearance, appearance.length);
    }
}