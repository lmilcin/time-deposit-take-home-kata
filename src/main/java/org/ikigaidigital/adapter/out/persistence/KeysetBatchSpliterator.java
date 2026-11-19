package org.ikigaidigital.adapter.out.persistence;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Lazily fetches consecutive batches using keyset pagination: each batch starts after the last id
 * of the previous one. No database resources are held between batches, so the resulting stream
 * may be consumed outside of a transaction (e.g. while an HTTP response is being written).
 * Batches are produced in ascending id order; {@code fetchAfterId} must return rows ordered by id.
 */
final class KeysetBatchSpliterator<T> extends Spliterators.AbstractSpliterator<List<T>> {

    private final int batchSize;
    private final IntFunction<List<T>> fetchAfterId;
    private final ToIntFunction<T> idOf;
    private int lastId;
    private boolean exhausted;

    /**
     * @param startAfterId the first batch starts after this id (exclusive)
     */
    KeysetBatchSpliterator(int startAfterId, int batchSize, IntFunction<List<T>> fetchAfterId, ToIntFunction<T> idOf) {
        super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
        this.lastId = startAfterId;
        this.batchSize = batchSize;
        this.fetchAfterId = fetchAfterId;
        this.idOf = idOf;
    }

    @Override
    public boolean tryAdvance(Consumer<? super List<T>> action) {
        if (exhausted) {
            return false;
        }
        List<T> batch = fetchAfterId.apply(lastId);
        if (batch.isEmpty()) {
            exhausted = true;
            return false;
        }
        lastId = idOf.applyAsInt(batch.get(batch.size() - 1));
        exhausted = batch.size() < batchSize;
        action.accept(batch);
        return true;
    }
}
