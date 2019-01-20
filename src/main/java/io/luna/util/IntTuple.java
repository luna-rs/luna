package io.luna.util;

import java.util.Objects;

/**
 * A class that acts as an {@code int}-{@code int} tuple.
 *
 * @author Jacob G. <https://github.com/jhg023>
 * @version January 19, 2019
 */
public final class IntTuple {
    
    /**
     * The key of this {@link IntTuple}.
     */
    private int key;
    
    /**
     * The value of this {@link IntTuple}.
     */
    private int value;
    
    /**
     * Creates a new {@link IntTuple} with the specified key and value.
     *
     * @param key   the key.
     * @param value the value.
     */
    public IntTuple(int key, int value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntTuple)) {
            return false;
        }
        
        var tuple = (IntTuple) o;
        
        return key == tuple.key && value == tuple.value;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
    
    /**
     * Gets this {@link IntTuple}'s key.
     *
     * @return the key as an {@code int}.
     */
    public int getKey() {
        return key;
    }
    
    /**
     * Gets this {@link IntTuple}'s value.
     *
     * @return the value as an {@code int}.
     */
    public int getValue() {
        return value;
    }
    
}