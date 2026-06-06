package io.luna.util.markov;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Creates Gson adapters for {@link MarkovChain} instances.
 * <p>
 * Gson cannot safely serialize this class's internal transition map directly because the transition map uses
 * {@code List<T>} as a key. JSON object keys are strings, so complex map keys are awkward and unreliable.
 * <p>
 * The factory reads the generic state type from {@code MarkovChain<T>}, so it can correctly reload chains like
 * {@code MarkovChain<Character>}, {@code MarkovChain<String>}, or {@code MarkovChain<Integer>}.
 *
 * @author lare96
 */
public  final class MarkovChainTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (typeToken.getRawType() != MarkovChain.class) {
            return null;
        }

        Type chainType = typeToken.getType();
        Type stateType = Object.class;

        if (chainType instanceof ParameterizedType) {
            stateType = ((ParameterizedType) chainType).getActualTypeArguments()[0];
        }

        TypeAdapter<?> stateAdapter = gson.getAdapter(TypeToken.get(stateType));
        return (TypeAdapter<T>) new MarkovChainTypeAdapter<>(stateAdapter);
    }
}