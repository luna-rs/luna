package io.luna.util.markov;

import com.google.common.math.LongMath;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Gson {@link TypeAdapter} for serializing and deserializing {@link MarkovChain} instances.
 * <p>
 * This adapter stores the chain as two weighted tables:
 * <ul>
 *     <li>{@code startingStates}, used to choose the first generated state.</li>
 *     <li>{@code transitions}, used to choose a weighted next state from a previous context.</li>
 * </ul>
 * <p>
 * The state type itself is delegated to {@code stateAdapter}, allowing this adapter to support chains made from strings,
 * characters, enum values, or any other Gson-supported state type.
 *
 * @param <T> The state type used by the Markov chain.
 */
public final class MarkovChainTypeAdapter<T> extends TypeAdapter<MarkovChain<T>> {

    /**
     * The adapter used to serialize and deserialize individual chain states.
     */
    private final TypeAdapter<T> stateAdapter;

    /**
     * Creates a new adapter backed by {@code stateAdapter}.
     *
     * @param stateAdapter The adapter used for individual Markov chain states.
     */
    MarkovChainTypeAdapter(TypeAdapter<T> stateAdapter) {
        this.stateAdapter = stateAdapter;
    }

    @Override
    public void write(JsonWriter out, MarkovChain<T> chain) throws IOException {
        if (chain == null) {
            out.nullValue();
            return;
        }

        out.beginObject();

        out.name("startingStates");
        writeStartingStates(out, chain.getStartingStates());

        out.name("transitions");
        writeTransitions(out, chain.getTransitions());

        out.endObject();
    }

    @Override
    public MarkovChain<T> read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }

        MarkovChain.Builder<T> builder = new MarkovChain.Builder<>();

        in.beginObject();

        while (in.hasNext()) {
            String name = in.nextName();

            switch (name) {
                case "startingStates":
                    readStartingStates(in, builder);
                    break;
                case "transitions":
                    readTransitions(in, builder);
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }

        in.endObject();
        return builder.build();
    }

    /**
     * Writes the weighted starting-state table.
     *
     * @param out The JSON writer.
     * @param startingStates The starting states mapped to their observed weights.
     * @throws IOException If writing fails.
     */
    private void writeStartingStates(JsonWriter out, Map<T, Long> startingStates) throws IOException {
        out.beginArray();

        for (Map.Entry<T, Long> entry : startingStates.entrySet()) {
            out.beginObject();

            out.name("state");
            stateAdapter.write(out, entry.getKey());

            out.name("weight").value(entry.getValue());

            out.endObject();
        }

        out.endArray();
    }

    /**
     * Reads weighted starting states into {@code builder}.
     * <p>
     * Invalid entries with a null state or non-positive weight are ignored. Duplicate states are merged using saturated
     * addition to avoid overflowing the stored weight.
     *
     * @param in The JSON reader.
     * @param builder The chain builder being populated.
     * @throws IOException If reading fails.
     */
    private void readStartingStates(JsonReader in, MarkovChain.Builder<T> builder) throws IOException {
        in.beginArray();

        while (in.hasNext()) {
            T state = null;
            long weight = 0L;

            in.beginObject();

            while (in.hasNext()) {
                String name = in.nextName();

                switch (name) {
                    case "state":
                        state = stateAdapter.read(in);
                        break;
                    case "weight":
                        weight = in.nextLong();
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }

            in.endObject();

            if (state != null && weight > 0L) {
                builder.getStartingStates().merge(state, weight, LongMath::saturatedAdd);
            }
        }

        in.endArray();
    }

    /**
     * Writes every transition context and its weighted next-state table.
     *
     * @param out The JSON writer.
     * @param transitions The transition table to write.
     * @throws IOException If writing fails.
     */
    private void writeTransitions(JsonWriter out, Map<List<T>, Map<T, Long>> transitions) throws IOException {
        out.beginArray();

        for (Map.Entry<List<T>, Map<T, Long>> entry : transitions.entrySet()) {
            out.beginObject();

            out.name("context");
            writeContext(out, entry.getKey());

            out.name("nextStates");
            writeNextStates(out, entry.getValue());

            out.endObject();
        }

        out.endArray();
    }

    /**
     * Reads transition contexts and their weighted next states into {@code builder}.
     * <p>
     * Empty contexts and empty next-state tables are ignored. Duplicate transition contexts are merged rather than
     * replaced, allowing partially duplicated JSON entries to load as one combined transition table.
     *
     * @param in The JSON reader.
     * @param builder The chain builder being populated.
     * @throws IOException If reading fails.
     */
    private void readTransitions(JsonReader in, MarkovChain.Builder<T> builder) throws IOException {
        in.beginArray();

        while (in.hasNext()) {
            List<T> context = new ArrayList<>();
            Map<T, Long> nextStates = new HashMap<>();

            in.beginObject();

            while (in.hasNext()) {
                String name = in.nextName();

                switch (name) {
                    case "context":
                        context = readContext(in);
                        break;
                    case "nextStates":
                        nextStates = readNextStates(in);
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }

            in.endObject();

            if (!context.isEmpty() && !nextStates.isEmpty()) {
                List<T> contextKey = List.copyOf(context);
                Map<T, Long> existing = builder.getTransitions().computeIfAbsent(contextKey, ignored -> new HashMap<>());

                for (Map.Entry<T, Long> entry : nextStates.entrySet()) {
                    existing.merge(entry.getKey(), entry.getValue(), LongMath::saturatedAdd);
                }
            }
        }

        in.endArray();
    }

    /**
     * Writes one transition context.
     *
     * @param out The JSON writer.
     * @param context The ordered states that form the transition lookup key.
     * @throws IOException If writing fails.
     */
    private void writeContext(JsonWriter out, List<T> context) throws IOException {
        out.beginArray();

        for (T state : context) {
            stateAdapter.write(out, state);
        }

        out.endArray();
    }

    /**
     * Reads one transition context.
     *
     * @param in The JSON reader.
     * @return The ordered states that form the transition lookup key.
     * @throws IOException If reading fails.
     */
    private List<T> readContext(JsonReader in) throws IOException {
        List<T> context = new ArrayList<>();

        in.beginArray();

        while (in.hasNext()) {
            context.add(stateAdapter.read(in));
        }

        in.endArray();
        return context;
    }

    /**
     * Writes a weighted next-state table for one transition context.
     *
     * @param out The JSON writer.
     * @param nextStates The possible next states mapped to their observed weights.
     * @throws IOException If writing fails.
     */
    private void writeNextStates(JsonWriter out, Map<T, Long> nextStates) throws IOException {
        out.beginArray();

        for (Map.Entry<T, Long> entry : nextStates.entrySet()) {
            out.beginObject();

            out.name("state");
            stateAdapter.write(out, entry.getKey());

            out.name("weight").value(entry.getValue());

            out.endObject();
        }

        out.endArray();
    }

    /**
     * Reads a weighted next-state table.
     * <p>
     * Invalid entries with a null state or non-positive weight are ignored. Duplicate states are merged using saturated
     * addition to avoid overflowing the stored weight.
     *
     * @param in The JSON reader.
     * @return The decoded next-state table.
     * @throws IOException If reading fails.
     */
    private Map<T, Long> readNextStates(JsonReader in) throws IOException {
        Map<T, Long> nextStates = new HashMap<>();

        in.beginArray();

        while (in.hasNext()) {
            T state = null;
            long weight = 0L;

            in.beginObject();

            while (in.hasNext()) {
                String name = in.nextName();

                switch (name) {
                    case "state":
                        state = stateAdapter.read(in);
                        break;
                    case "weight":
                        weight = in.nextLong();
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }

            in.endObject();

            if (state != null && weight > 0L) {
                nextStates.merge(state, weight, LongMath::saturatedAdd);
            }
        }

        in.endArray();
        return nextStates;
    }
}