package io.luna.util;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * A static-utility class that provides extra functionality for all {@link Future} related classes.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class FutureUtils {

    /**
     * A duplicate of {@link Futures#addCallback(ListenableFuture, FutureCallback)} that uses Java 8 functions for
     * the {@code onSuccess(T)} and {@code onFailure(Throwable)} methods.
     */
    public static <T> void addCallback(ListenableFuture<T> it, Consumer<T> onSuccess,
        Consumer<? super Throwable> onFail) {
        Futures.addCallback(it, new FutureCallback<T>() {
            @Override
            public void onSuccess(T result) {
                onSuccess.accept(result);
            }

            @Override
            public void onFailure(Throwable t) {
                onFail.accept(t);
            }
        });
    }

    /**
     * A duplicate of {@link Futures#addCallback(ListenableFuture, FutureCallback)} that uses a Java 8 function for
     * the {@code onSuccess(T)} method and propagates the exception on failure.
     */
    public static <T> void addCallback(ListenableFuture<T> it, Consumer<T> onSuccess) {
        addCallback(it, onSuccess, Throwables::propagate);
    }
}
