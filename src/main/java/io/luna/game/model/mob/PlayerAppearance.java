package io.luna.game.model.mob;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.List;

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
     * A multimap containing the valid appearance values.
     */
    private static final ImmutableListMultimap<Integer, Range<Integer>> VALID_VALUES;

    static { /* Initialize valid appearance cache. */
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
     * Determines if {@code value} is a valid gender.
     */
    public static boolean isGenderValid(int value) {
        List<Range<Integer>> validRanges = VALID_VALUES.get(GENDER);
        return validRanges.get(0).contains(value);
    }

    /**
     * Determines if {@code value} is a valid color for body part {@code id}.
     */
    public static boolean isColorValid(int id, int value) {
        checkArgument(id >= 8 && id <= 12, "invalid color index identifie");

        List<Range<Integer>> validRanges = VALID_VALUES.get(id);
        return validRanges.get(0).contains(value);
    }

    /**
     * Determines if {@code value} is a valid model for {@code gender} and body part
     * {@code value}.
     */
    public static boolean isModelValid(int id, int gender, int value) {
        checkArgument(isGenderValid(gender), "invalid gender identifier");
        checkArgument(id >= 1 && id <= 7, "invalid model index identifier");

        List<Range<Integer>> validRanges = VALID_VALUES.get(id);
        return validRanges.get(gender).contains(value);
    }

    /**
     * Determines if {@code gender} and {@code value} are valid according to the
     * {@code isGenderValid(int)}, {@code isColorValid(int, int)}, or {@code isColorValid(int, int, int)}
     * method.
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
     * Determines if an appearance set is valid.
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
    private final int[] appearance = new int[13];

    /**
     * Creates a new {@link PlayerAppearance}.
     */
    public PlayerAppearance() {

        /* Populate the appearance array with the default values. */
        for (int index = 0; index < appearance.length; index++) {
            appearance[index] = DEFAULT_APPEARANCE.get(index);
        }
    }

    /**
     * Retrieves the value for {@code id}.
     */
    public int get(int id) {
        return appearance[id];
    }

    /**
     * Sets {@code id} to {@code value}.
     */
    public void set(int id, int value) {
        checkArgument(isAnyValid(id, get(GENDER), value), "invalid id and value pair");
        appearance[id] = value;
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
     */
    public void setValues(int[] newValues) {
        checkArgument(isAllValid(newValues), "invalid appearance array");
        System.arraycopy(newValues, 0, appearance, 0, 13);
    }

    /**
     * Returns an immutable shallow-copy of the appearance array.
     */
    public ImmutableList<Integer> toList() {
        return ImmutableList.copyOf(Ints.asList(appearance));
    }

    /**
     * Returns a mutable shallow-copy of the appearance array.
     */
    public int[] toArray() {
        return Arrays.copyOf(appearance, appearance.length);
    }
}