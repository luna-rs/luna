package io.luna.game.model.mob.bot.schedule;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import io.luna.game.model.mob.PlayerCredentials;
import io.luna.util.RandomUtils;
import io.luna.util.markov.MarkovChain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates bot usernames from the username {@link MarkovChain}, then cleans and filters them so they read like names
 * a player would pick.
 * <p>
 * The username model has no end-of-name state, and its digit transitions only repeat a few patterns. Raw output
 * starts and stops mid-word and often ends in a long run of the same digit. This generator starts every name at a
 * word boundary, keeps the words before the first digit, cuts long names at a break between words, and adds its own
 * short number suffix.
 *
 * @author TheLining
 */
public final class BotUsernameGenerator {

    /**
     * The maximum length of a username.
     */
    private static final int MAX_LENGTH = 12;

    /**
     * The minimum length of a username, not counting its number suffix.
     */
    private static final int MIN_LENGTH = 4;

    /**
     * The number of states generated per attempt, including the leading space. This is longer than
     * {@link #MAX_LENGTH} so long names can be cut back to a break between words.
     */
    private static final int GENERATED_LENGTH = 18;

    /**
     * The number of attempts made before giving up.
     */
    private static final int MAX_ATTEMPTS = 20;

    /**
     * The chance of a username ending in a number.
     */
    private static final double SUFFIX_CHANCE = 0.35;

    /**
     * The minimum next-state entropy, in bits, for a position to count as a break between two words.
     * <p>
     * Inside a word only a few letters tend to follow, but after a complete word almost any letter can.
     */
    private static final double WORD_BREAK_ENTROPY = 3.0;

    /**
     * Matches the letters and spaces before the first digit.
     */
    private static final Pattern LEADING_WORDS = Pattern.compile("^[a-z ]+");

    /**
     * Matches a character repeated three or more times in a row.
     */
    private static final Pattern REPEATED_CHARACTER = Pattern.compile("(.)\\1{2,}");

    /**
     * Matches two or more spaces in a row.
     */
    private static final Pattern REPEATED_SPACES = Pattern.compile(" {2,}");

    /**
     * Matches a vowel.
     */
    private static final Pattern VOWEL = Pattern.compile("[aeiouy]");

    /**
     * Matches a run of four or more consonants.
     */
    private static final Pattern CONSONANT_RUN = Pattern.compile("[^aeiouy ]{4,}");

    /**
     * Matches a word that starts by repeating itself, or is repeated by the next word.
     */
    private static final Pattern REPEATED_WORD = Pattern.compile("(?:^| )([a-z]{3,}) ?\\1");

    /**
     * The username Markov chain.
     */
    private final MarkovChain<Character> chain;

    /**
     * The longest context in the chain's transition table.
     */
    private final int contextSize;

    /**
     * Creates a new {@link BotUsernameGenerator}.
     *
     * @param chain The username Markov chain.
     */
    public BotUsernameGenerator(MarkovChain<Character> chain) {
        this.chain = chain;
        contextSize = chain.getTransitions().keySet().stream().mapToInt(List::size).max().orElse(0);
    }

    /**
     * Generates a cleaned username.
     *
     * @param available Tests whether a username can still be used.
     * @return The username, or an empty optional if no usable name was generated.
     */
    public Optional<String> generate(Predicate<String> available) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String username = nextUsername();
            if (username != null && available.test(username)) {
                return Optional.of(username);
            }
        }
        return Optional.empty();
    }

    /**
     * Generates and cleans one username.
     *
     * @return The username, or {@code null} if the generated name was rejected.
     */
    private String nextUsername() {
        // Starting from a space puts the first letters in the same contexts as the start of a word.
        List<Character> generated = chain.generate(' ', GENERATED_LENGTH, ImmutableSet.of());
        String raw = new String(Chars.toArray(generated)).toLowerCase(Locale.CANADA);
        Matcher leadingWords = LEADING_WORDS.matcher(raw);
        if (!leadingWords.find()) {
            return null;
        }
        String words = REPEATED_CHARACTER.matcher(leadingWords.group()).replaceAll("$1$1");
        words = REPEATED_SPACES.matcher(words).replaceAll(" ").trim();

        String suffix = RandomUtils.roll(SUFFIX_CHANCE) ? Integer.toString(RandomUtils.inclusive(1, 99)) : "";
        int maxWordsLength = MAX_LENGTH - suffix.length();
        if (words.length() > maxWordsLength) {
            int wordBreak = findWordBreak(words, maxWordsLength);
            if (wordBreak == -1) {
                return null;
            }
            words = words.substring(0, wordBreak).trim();
        }
        if (!isReadable(words)) {
            return null;
        }
        String username = words.replace(' ', '_') + suffix;
        return PlayerCredentials.isValidUsername(username) ? username : null;
    }

    /**
     * Finds the last break between words that keeps {@code words} within {@code maxLength}.
     *
     * @param words The words to cut.
     * @param maxLength The maximum length after cutting.
     * @return The index to cut at, or {@code -1} if there is no break.
     */
    private int findWordBreak(String words, int maxLength) {
        for (int index = maxLength; index >= MIN_LENGTH; index--) {
            if (words.charAt(index) == ' ' || nextStateEntropy(words.substring(0, index)) >= WORD_BREAK_ENTROPY) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Computes the entropy of the states that can follow {@code prefix}, using the chain's longest context.
     *
     * @param prefix The generated text so far.
     * @return The entropy in bits, or {@code 0} if the chain has no transitions for the context.
     */
    private double nextStateEntropy(String prefix) {
        if (prefix.length() < contextSize) {
            return 0;
        }
        List<Character> context = Chars.asList(prefix.substring(prefix.length() - contextSize).toCharArray());
        Map<Character, Long> nextStates = chain.getTransitions().get(context);
        if (nextStates == null || nextStates.isEmpty()) {
            return 0;
        }
        double total = nextStates.values().stream().mapToLong(Long::longValue).sum();
        double entropy = 0;
        for (long weight : nextStates.values()) {
            double probability = weight / total;
            entropy -= probability * Math.log(probability) / Math.log(2);
        }
        return entropy;
    }

    /**
     * Checks whether {@code words} read like a name: long enough, at most two words, every word has a vowel, and no
     * long consonant runs or repeated words.
     *
     * @param words The words to check.
     * @return {@code true} if the words can be used in a username.
     */
    private boolean isReadable(String words) {
        if (words.length() < MIN_LENGTH || CONSONANT_RUN.matcher(words).find() ||
                REPEATED_WORD.matcher(words).find()) {
            return false;
        }
        String[] split = words.split(" ");
        if (split.length > 2) {
            return false;
        }
        for (String word : split) {
            if (!VOWEL.matcher(word).find()) {
                return false;
            }
        }
        return true;
    }
}
