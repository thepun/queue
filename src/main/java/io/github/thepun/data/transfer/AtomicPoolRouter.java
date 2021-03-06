/**
 * Copyright (C)2011 - Marat Gariev <thepun599@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.thepun.data.transfer;

import java.util.Arrays;

import io.github.thepun.unsafe.ArrayMemory;
import io.github.thepun.unsafe.MemoryFence;


public final class AtomicPoolRouter<T> implements HasProducers<T>, HasConsumers<T> {

    // TODO: extend from AbstractRouter
    // TODO: align producers/consumers
    // TODO: get rid of size


    private static final Object DATA_REF = new Object();
    private static final Object EMPTY_REF = new Object();
    private static final Object ALMOST_DATA_REF = new Object();
    private static final Object ALMOST_EMPTY_REF = new Object();



    private final int size;
    private final int mask;
    private final Object[] data;

    private AtomicPoolConsumer<T>[] consumers;
    private AtomicPoolProducer<T>[] producers;

    public AtomicPoolRouter(int bufferSize) {
        if (bufferSize < 1) {
            throw new IllegalArgumentException("Size should be greater then zero");
        }

        double log2 = Math.log10(bufferSize) / Math.log10(2);
        int pow = (int) Math.ceil(log2);

        size = (int) Math.pow(2, pow);
        mask = size - 1;
        data = new Object[size * 2];
        consumers = new AtomicPoolConsumer[0];
        producers = new AtomicPoolProducer[0];

        for (int i = 0; i < size; i++) {
            ArrayMemory.setObject(data, i << 1, EMPTY_REF);
        }
    }

    @Override
    public synchronized QueueTail<T> createProducer() {
        AtomicPoolProducer<T>[] oldProducers = producers;
        AtomicPoolProducer<T>[] newProducers = Arrays.copyOf(oldProducers, oldProducers.length + 1);
        AtomicPoolProducer<T> producer = new AtomicPoolProducer<>(this);
        newProducers[oldProducers.length] = producer;
        updateProducers(newProducers);
        return producer;
    }

    @Override
    public synchronized QueueHead<T> createConsumer() {
        AtomicPoolConsumer<T>[] oldConsumers = consumers;
        AtomicPoolConsumer<T>[] newConsumers = Arrays.copyOf(oldConsumers, oldConsumers.length + 1);
        AtomicPoolConsumer<T> consumer = new AtomicPoolConsumer<>(this);
        newConsumers[oldConsumers.length] = consumer;
        updateConsumers(newConsumers);
        return consumer;
    }



    @Override
    public synchronized void destroyProducer(QueueTail<T> producer) {
        if (!(producer instanceof AtomicPoolRouter.AtomicPoolProducer)) {
            throw new IllegalArgumentException("Wrong producer");
        }

        AtomicPoolProducer<T> producerSubqueue = (AtomicPoolProducer<T>) producer;
        if (producerSubqueue.parent != this) {
            throw new IllegalArgumentException("Producer from another router");
        }

        AtomicPoolProducer<T>[] newProducers;
        AtomicPoolProducer<T>[] oldProducers;

        oldProducers = producers;
        int index = -1;
        for (int i = 0; i < oldProducers.length; i++) {
            if (oldProducers[i] == producer) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            throw new IllegalArgumentException("Producer not found");
        }

        newProducers = new AtomicPoolProducer[oldProducers.length - 1];
        System.arraycopy(oldProducers, 0, newProducers, 0, index);
        System.arraycopy(oldProducers, index + 1, newProducers, index + 1 - 1, oldProducers.length - (index + 1));
        updateProducers(newProducers);
    }

    @Override
    public synchronized void destroyConsumer(QueueHead<T> consumer) {
        if (!(consumer instanceof AtomicPoolRouter.AtomicPoolConsumer)) {
            throw new IllegalArgumentException("Wrong consumer");
        }

        AtomicPoolConsumer<T> producerSubqueue = (AtomicPoolConsumer<T>) consumer;
        if (producerSubqueue.parent != this) {
            throw new IllegalArgumentException("Consumer from another router");
        }

        AtomicPoolConsumer<T>[] newConsumers;
        AtomicPoolConsumer<T>[] oldConsumers;

        oldConsumers = consumers;
        int index = -1;
        for (int i = 0; i < oldConsumers.length; i++) {
            if (oldConsumers[i] == consumer) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            throw new IllegalArgumentException("Consumer not found");
        }

        newConsumers = new AtomicPoolConsumer[oldConsumers.length - 1];
        System.arraycopy(oldConsumers, 0, newConsumers, 0, index);
        System.arraycopy(oldConsumers, index + 1, newConsumers, index + 1 - 1, oldConsumers.length - (index + 1));
        updateConsumers(newConsumers);
    }

    private void updateProducers(AtomicPoolProducer<T>[] newProducers) {
        producers = newProducers;

        /*int count = newProducers.length;
        int step = size / count;
        for (int i = 0; i < count; i++) {
            newProducers[i].producerWriteCounter.set(step * i);
        }*/
    }

    private void updateConsumers(AtomicPoolConsumer<T>[] newConsumers) {
        consumers = newConsumers;

        /*int count = newConsumers.length;
        int step = size / count;
        for (int i = 0; i < count; i++) {
            newConsumers[i].consumerReadCounter.set(step * i);
        }*/
    }


    private static final class AtomicPoolProducer<T> implements QueueTail<T> {

        private final AtomicPoolRouter<T> parent;

        // gap
        private int t1;

        private final int mask;
        private final Object[] data;

        private int shift;
        private AlignedLong producerWriteCounter;

        private AtomicPoolProducer(AtomicPoolRouter<T> parent) {
            this.parent = parent;

            mask = parent.mask;
            data = parent.data;

            shift = 16 + (int) (Math.random() * 10);
            producerWriteCounter = new AlignedLong();
            producerWriteCounter.set(0);
        }

        @Override
        public boolean addToTail(T element) {
            int localMask = mask;
            Object[] localData = data;
            AlignedLong localWriteCounter = producerWriteCounter;

            int index;
            Object atomic;
            int localShift = shift;
            long writeIndex = localWriteCounter.get();
            long maxWriteIndex = writeIndex + localMask;
            do {
                index = (int) (writeIndex & localMask) << 1;
                atomic = ArrayMemory.getObject(localData, index);

                if (atomic == EMPTY_REF) {
                    if (ArrayMemory.compareAndSwapObject(localData, index, EMPTY_REF, ALMOST_DATA_REF)) {
                        ArrayMemory.setObject(localData, index | 1, element);
                        MemoryFence.store();
                        ArrayMemory.setObject(localData, index, DATA_REF);
                        localWriteCounter.set(writeIndex);
                        return true;
                    }
                } else if (atomic == ALMOST_EMPTY_REF) {
                    continue;
                } else if (atomic == ALMOST_DATA_REF) {
                    writeIndex += localShift;
                    continue;
                }

                writeIndex++;
            } while (writeIndex < maxWriteIndex);

            localWriteCounter.set(writeIndex);
            return false;
        }
    }


    private static final class AtomicPoolConsumer<T> implements QueueHead<T> {

        private final AtomicPoolRouter<T> parent;

        private final int size;
        private final int mask;
        private final Object[] data;

        private AlignedLong consumerReadCounter;

        private AtomicPoolConsumer(AtomicPoolRouter<T> parent) {
            this.parent = parent;

            size = parent.size;
            mask = parent.mask;
            data = parent.data;

            consumerReadCounter = new AlignedLong();
            consumerReadCounter.set(0);
        }

        @Override
        public T removeFromHead() {
            int localMask = mask;
            Object[] localData = data;
            AlignedLong localReadCounter = consumerReadCounter;

            int index;
            Object atomic;
            long readIndex = localReadCounter.get();
            long maxReadIndex = readIndex + size;
            do {
                index = (int) (readIndex & localMask) << 1;
                atomic = ArrayMemory.getObject(localData, index);

                if (atomic == DATA_REF) {
                    if (ArrayMemory.compareAndSwapObject(localData, index, DATA_REF, ALMOST_EMPTY_REF)) {
                        Object element = ArrayMemory.getObject(localData, index | 1);
                        MemoryFence.load();
                        ArrayMemory.setObject(localData, index, EMPTY_REF);
                        localReadCounter.set(readIndex);
                        return (T) element;
                    }
                } else if (atomic == ALMOST_DATA_REF) {
                    continue;
                } else if (atomic == ALMOST_EMPTY_REF) {
                    readIndex += 16;
                    continue;
                }

                readIndex++;
            } while (readIndex < maxReadIndex);

            localReadCounter.set(readIndex);
            return null;
        }
    }
}
