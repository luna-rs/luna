package io.luna.util.markov;

import com.google.common.collect.ImmutableSet;
import com.google.common.math.LongMath;
import com.google.gson.reflect.TypeToken;
import io.luna.game.model.mob.attr.Attribute;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.requireNonNull;

/**
 * A weighted, order-based Markov chain.
 * <p>
 * This chain learns weighted transitions from training sequences and can later generate new sequences that follow the
 * same transition patterns. Each transition maps a context of one or more previous states to a weighted set of possible next
 * states.
 * <p>
 * The chain supports variable context fallback during generation. It first attempts to use the largest available
 * context up to the configured order, then falls back to smaller contexts if no transition data exists for the larger one.
 *
 * @param <T> The state type used by this chain.
 */
public final class MarkovChain<T> {

    /**
     * Builder for creating and training a {@link MarkovChain}.
     * <p>
     * The builder stores transition data until {@link #build()} is called. It can be trained from raw sequences or by
     * loading a previously saved model file.
     *
     * @param <T> The state type used by the chain.
     */
    public static final class Builder<T> {

        /**
         * The maximum context length used when training transitions.
         */
        private int order = 2;

        /**
         * The model name used when saving the chain.
         */
        private String name = "default";

        /**
         * Weighted transitions from a context to possible next states.
         */
        private final Map<List<T>, Map<T, Long>> transitions = new HashMap<>();

        /**
         * Weighted starting states observed during training.
         */
        private final Map<T, Long> startingStates = new HashMap<>();

        /**
         * Creates a {@link MarkovChain} from the current builder state.
         *
         * @return A chain containing the builder's configured name, order, starting states,
         * and transition data.
         */
        public MarkovChain<T> build() {
            MarkovChain<T> chain = new MarkovChain<>(name, order);
            chain.startingStates.putAll(startingStates);
            chain.transitions.putAll(transitions);
            return chain;
        }

        /**
         * Sets the model name.
         *<p>
         * The name is used when saving the chain to disk.
         *
         * @param name The model name.
         * @return This builder.
         */
        public Builder<T> setName(String name) {
            this.name = requireNonNull(name);
            return this;
        }

        /**
         * Sets the maximum context length used for transition training.
         * <p>
         * Values below {@code 2} are clamped to {@code 2}.
         *
         * @param order The maximum Markov context length.
         * @return This builder.
         */
        public Builder<T> setOrder(int order) {
            this.order = Math.max(2, order);
            return this;
        }

        /**
         * Loads transition data from a saved model file into this builder.
         * <p>
         * The model is read from the default Markov model directory using the file name {@code modelName + ".model.json"}.
         * If the model cannot be loaded, the error is logged and the builder remains usable.
         *
         * @param modelName The saved model name, without the {@code .model.json} suffix.
         * @param modelType The Gson type token for this chain type.
         * @return This builder.
         */
        public Builder<T> train(String modelName, TypeToken<MarkovChain<T>> modelType) {
            try {
                String modelJson = Files.readString(MODEL_DIRECTORY.resolve(modelName + ".model.json"));
                MarkovChain<T> chain = Attribute.getGsonInstance().fromJson(modelJson, modelType.getType());

                if (chain != null) {
                    transitions.putAll(chain.transitions);
                    startingStates.putAll(chain.startingStates);
                }
            } catch (Exception e) {
                logger.catching(e);
            }
            return this;
        }

        /**
         * Trains this builder from a sequence using a default weight of {@code 1}.
         *
         * @param sequence The observed sequence of states.
         */
        public void train(List<T> sequence) {
            train(sequence, 1L);
        }

        /**
         * Trains this builder from a weighted sequence.
         * <p>
         * The first state is recorded as a possible starting state. Each following state is recorded as a possible
         * next state for every context length from {@code 1} up to the configured order.
         * <p>
         * Invalid inputs are ignored.
         *
         * @param sequence The observed sequence of states.
         * @param weight The weight to add to each observed transition.
         */
        public void train(List<T> sequence, long weight) {
            if (sequence == null || sequence.size() < 2 || weight <= 0) {
                return;
            }

            startingStates.merge(sequence.get(0), weight, LongMath::saturatedAdd);

            for (int nextIndex = 1; nextIndex < sequence.size(); nextIndex++) {
                T next = sequence.get(nextIndex);
                int maxContextSize = Math.min(order, nextIndex);

                for (int contextSize = 1; contextSize <= maxContextSize; contextSize++) {
                    int contextStart = nextIndex - contextSize;
                    List<T> context = List.copyOf(sequence.subList(contextStart, nextIndex));

                    addTransition(context, next, weight);
                }
            }
        }

        /**
         * Clears all trained starting states and transitions.
         */
        public void clear() {
            transitions.clear();
            startingStates.clear();
        }

        /**
         * @return The transition table currently stored by this builder.
         */
        public Map<List<T>, Map<T, Long>> getTransitions() {
            return transitions;
        }

        /**
         * @return The starting-state table currently stored by this builder.
         */
        public Map<T, Long> getStartingStates() {
            return startingStates;
        }

        /**
         * Adds a weighted transition from {@code context} to {@code next}.
         */
        private void addTransition(List<T> context, T next, long weight) {
            Map<T, Long> nextStates = transitions.computeIfAbsent(context, ignored -> new HashMap<>());
            nextStates.merge(next, weight, LongMath::saturatedAdd);
        }
    }

    /**
     * Directory containing saved Markov model files.
     */
    private static final Path MODEL_DIRECTORY = Paths.get("data", "game", "bots", "model");

    /**
     * Logger used for model load/save failures.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The maximum context length used during generation.
     */
    private int order;

    /**
     * The model name used when saving this chain.
     */
    private final String name;

    /**
     * Weighted transitions from context states to possible next states.
     */
    private final Map<List<T>, Map<T, Long>> transitions = new HashMap<>();

    /**
     * Weighted starting states used when generation does not supply an explicit start.
     */
    private final Map<T, Long> startingStates = new HashMap<>();

    private MarkovChain(String name, int order) {
        this.name = name;
        this.order = order;
    }

    /**
     * Saves this chain to disk.
     * <p>
     * The model is written to the default Markov model directory using this chain's name and the {@code .model.json}
     * suffix. Save failures are logged.
     *
     * @param modelType The Gson type token for this chain type.
     */
    public void save(TypeToken<MarkovChain<T>> modelType) {
        try {
            String json = Attribute.getGsonInstance().toJson(this, modelType.getType());
            Files.writeString(MODEL_DIRECTORY.resolve(name + ".model.json"), json);
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    /**
     * Generates a sequence using any trained state.
     *
     * @param maxLength The maximum number of states to generate.
     * @return A generated sequence, or an empty list if generation cannot start.
     */
    public List<T> generate(int maxLength) {
        return generate(maxLength, ImmutableSet.of());
    }

    /**
     * Generates a sequence using a random trained starting state.
     * <p>
     * If {@code allowedStates} is not empty, both the starting state and all following states must be contained in
     * that set.
     *
     * @param maxLength The maximum number of states to generate.
     * @param allowedStates The optional set of states allowed in the generated sequence.
     * @return A generated sequence, or an empty list if generation cannot start.
     */
    public List<T> generate(int maxLength, Set<T> allowedStates) {
        if (maxLength <= 0 || startingStates.isEmpty()) {
            return List.of();
        }

        Map<T, Long> filteredStarts = filterOptions(startingStates, allowedStates);

        if (filteredStarts.isEmpty()) {
            return List.of();
        }

        T start = weightedRandom(filteredStarts);

        if (start == null) {
            return List.of();
        }

        return generate(start, maxLength, allowedStates);
    }

    /**
     * Generates a sequence from a specific starting state.
     * <p>
     * Generation stops when the maximum length is reached, when no transition exists for the current context, or when
     * all possible next states are filtered out by {@code allowedStates}.
     *
     * @param start The first state in the generated sequence.
     * @param maxLength The maximum number of states to generate.
     * @param allowedStates The optional set of states allowed after filtering.
     * @return A generated sequence beginning with {@code start}, or an empty list if the input is invalid.
     */
    public List<T> generate(T start, int maxLength, Set<T> allowedStates) {
        if (start == null || maxLength <= 0) {
            return List.of();
        }

        List<T> result = new ArrayList<>(maxLength);
        result.add(start);

        for (int index = 1; index < maxLength; index++) {
            Map<T, Long> nextOptions = findNextOptions(result);

            if (nextOptions == null || nextOptions.isEmpty()) {
                break;
            }

            Map<T, Long> filtered = filterOptions(nextOptions, allowedStates);

            if (filtered.isEmpty()) {
                break;
            }

            T next = weightedRandom(filtered);

            if (next == null) {
                break;
            }

            result.add(next);
        }

        return result;
    }

    /**
     * Finds the best available next-state options for the generated sequence.
     * <p>
     * The longest matching context is preferred. If no transition exists for the longest context, smaller contexts
     * are tried until a match is found.
     */
    private Map<T, Long> findNextOptions(List<T> generated) {
        int maxContextSize = Math.min(order, generated.size());

        for (int contextSize = maxContextSize; contextSize >= 1; contextSize--) {
            int contextStart = generated.size() - contextSize;
            List<T> context = List.copyOf(generated.subList(contextStart, generated.size()));
            Map<T, Long> nextOptions = transitions.get(context);

            if (nextOptions != null && !nextOptions.isEmpty()) {
                return nextOptions;
            }
        }

        return null;
    }

    /**
     * Filters transition options against the allowed-state set.
     */
    private Map<T, Long> filterOptions(Map<T, Long> options, Set<T> allowedStates) {
        if (allowedStates == null || allowedStates.isEmpty()) {
            return options;
        }

        Map<T, Long> filtered = new HashMap<>();

        for (Map.Entry<T, Long> entry : options.entrySet()) {
            if (allowedStates.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }

        return filtered;
    }

    /**
     * Selects one option using weighted random selection.
     */
    private T weightedRandom(Map<T, Long> options) {
        long total = 0L;

        for (long weight : options.values()) {
            if (weight > 0) {
                total = LongMath.saturatedAdd(total, weight);
            }
        }

        if (total <= 0L) {
            return null;
        }

        long randomValue = ThreadLocalRandom.current().nextLong(total);

        for (Map.Entry<T, Long> entry : options.entrySet()) {
            long weight = entry.getValue();

            if (weight <= 0) {
                continue;
            }

            randomValue -= weight;

            if (randomValue < 0L) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Checks whether this chain has transition data for the supplied single-state context.
     *
     * @param state The state to check.
     * @return {@code true} if this chain has transition data beginning with {@code state}.
     */
    public boolean hasState(T state) {
        return transitions.containsKey(List.of(state));
    }

    /**
     * Checks whether this chain has no transition data.
     *
     * @return {@code true} if this chain has no trained transitions.
     */
    public boolean isEmpty() {
        return transitions.isEmpty();
    }

    /**
     * @return This chain's model name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return This chain's maximum context length.
     */
    public int getOrder() {
        return order;
    }

    /**
     * Sets this chain's maximum context length.
     *
     * @param order The new Markov order.
     */
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * @return The weighted starting states used by this chain.
     */
    public Map<T, Long> getStartingStates() {
        return startingStates;
    }

    /**
     * @return The weighted transition table used by this chain.
     */
    public Map<List<T>, Map<T, Long>> getTransitions() {
        return transitions;
    }
}