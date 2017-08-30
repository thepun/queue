package org.thepun.concurrency.queue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ArrayQueue<T> implements QueueHead<T>, QueueTail<T> {

    private final int size;
    private final Object[] data;
    private final AlignedLong readCounter;
    private final AlignedLong writeCounter;
    private final AlignedLong readCounterForWriter;
    private final AlignedLong writeCounterForReader;

    public ArrayQueue(int queueSize) {
        if (queueSize < 1) {
            throw new IllegalArgumentException("Size should be greater then zero");
        }

        size = queueSize;
        data = new Object[queueSize];
        readCounter = new AlignedLong();
        writeCounter = new AlignedLong();
        readCounterForWriter = new AlignedLong();
        writeCounterForReader = new AlignedLong();
    }

    @Override
    public T removeFromHead() {
        long writeIndex = writeCounterForReader.get();
        long readIndex = readCounter.getAndIncrement(writeIndex);
        if (readIndex == -1) {
            return null;
        }

        int index = (int) readIndex % size;
        Object element;
        do {
            element = data[index];
        } while (element == null);

        readCounterForWriter.increment();

        return (T) element;
    }

    @Override
    public T removeFromHead(long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
        // TODO: implement busy wait
        return null;
    }

    @Override
    public boolean addToTail(T element) {
        long readIndex = readCounterForWriter.get();
        long writeIndex = writeCounter.getAndIncrement(readIndex + size);
        if (writeIndex == -1) {
            return false;
        }

        int index = (int) writeIndex % size;
        data[index] = element;

        writeCounterForReader.increment();

        return true;
    }
}
