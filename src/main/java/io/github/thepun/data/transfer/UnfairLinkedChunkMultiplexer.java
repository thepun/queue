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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by thepun on 19.08.17.
 */
@SuppressWarnings("unchecked")
public final class UnfairLinkedChunkMultiplexer<T> implements Multiplexer<T> {

    // TODO: align producers

    // TODO: refactor constants
    private static final int BUNCH_SIZE = 1024;
    private static final int FIRST_ITEM_INDEX = 1;
    private static final int SECOND_ITEM_INDEX = 2;
    private static final int REF_TO_NEXT_INDEX = 0;
    private static final int FIRST_OFFSET_INDEX = BUNCH_SIZE;

    private static final Object[] EMPTY_ARRAY = new Object[BUNCH_SIZE];


    private int nextProducerIndex;
    private ProducerSubqueue<T>[] producers;

    public UnfairLinkedChunkMultiplexer() {
        producers = new ProducerSubqueue[0];
        nextProducerIndex = 0;
    }

    @Override
    public synchronized QueueTail<T> createProducer() {
        ProducerSubqueue<T>[] oldProducers = producers;
        ProducerSubqueue<T>[] newProducers = Arrays.copyOf(oldProducers, oldProducers.length + 1);
        ProducerSubqueue<T> producer = new ProducerSubqueue<>(this);
        newProducers[oldProducers.length] = producer;
        producers = newProducers;
        return producer;
    }

    @Override
    public synchronized void destroyProducer(QueueTail<T> producer) {
        if (!(producer instanceof ProducerSubqueue)) {
            throw new IllegalArgumentException("Wrong producer");
        }

        ProducerSubqueue producerSubqueue = (ProducerSubqueue) producer;
        if (producerSubqueue.parent != this) {
            throw new IllegalArgumentException("Producer from another multiplexer");
        }

        ProducerSubqueue[] newProducers;
        ProducerSubqueue[] oldProducers;

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

        newProducers = new ProducerSubqueue[oldProducers.length - 1];
        System.arraycopy(oldProducers, 0, newProducers, 0, index);
        System.arraycopy(oldProducers, index + 1, newProducers, index + 1 - 1, oldProducers.length - (index + 1));

        producers = newProducers;
    }

    @Override
    public T removeFromHead() {
        ProducerSubqueue<T>[] localProducers = producers;

        int producerIndex = nextProducerIndex;
        int producerCount = localProducers.length;
        int maxProducerIndex = producerIndex + producerCount;
        for (;;) {
            if (producerIndex >= maxProducerIndex) {
                nextProducerIndex = 0;
                return null;
            }

            ProducerSubqueue<T> producer = localProducers[producerIndex % producerCount];

            if (producer.consumerIndex == FIRST_OFFSET_INDEX) {
                Object[] oldConsumerBunch = producer.consumerBunch;

                Object[] newHeadBunch = (Object[]) oldConsumerBunch[REF_TO_NEXT_INDEX];
                if (newHeadBunch == null) {
                    continue;
                }

                producer.consumerIndex = FIRST_ITEM_INDEX;
                producer.consumerBunch = newHeadBunch;

                System.arraycopy(EMPTY_ARRAY, 0, oldConsumerBunch, 0, BUNCH_SIZE);

                Object[] prevEmptyChainHead = producer.emptyChain.get();
                if (prevEmptyChainHead == null) {
                    producer.emptyChain.set(oldConsumerBunch);
                } else {
                    oldConsumerBunch[REF_TO_NEXT_INDEX] = prevEmptyChainHead;
                    if (!producer.emptyChain.compareAndSet(prevEmptyChainHead, oldConsumerBunch)) {
                        oldConsumerBunch[REF_TO_NEXT_INDEX] = null;
                        producer.emptyChain.set(oldConsumerBunch);
                    }
                }
            }

            Object element = producer.consumerBunch[producer.consumerIndex];
            if (element != null) {
                nextProducerIndex = producerIndex;
                producer.consumerIndex++;
                return (T) element;
            }

            producerIndex++;
        }
    }


    private static final class ProducerSubqueue<T> implements QueueTail<T> {

        private final UnfairLinkedChunkMultiplexer<T> parent;

        // TODO: use aligned node
        private int consumerIndex;
        private Object[] consumerBunch;

        // TODO: use aligned node
        private int producerIndex;
        private Object[] producerBunch;
        private Object[] producerEmptyChain;

        private final AtomicReference<Object[]> emptyChain;

        private ProducerSubqueue(UnfairLinkedChunkMultiplexer<T> parent) {
            this.parent = parent;

            Object[] firstBunch = new Object[BUNCH_SIZE];
            consumerBunch = firstBunch;
            producerBunch = firstBunch;
            consumerIndex = FIRST_ITEM_INDEX;
            producerIndex = FIRST_ITEM_INDEX;

            emptyChain = new AtomicReference<>();
        }

        @Override
        public boolean addToTail(T element) {
            if (producerIndex == FIRST_OFFSET_INDEX) {
                Object[] newTailBunch;

                if (producerEmptyChain == null) {
                    Object[] newChain = emptyChain.getAndSet(null);
                    if (newChain == null) {
                        newChain = new Object[BUNCH_SIZE];
                    }

                    producerEmptyChain = newChain;
                }

                newTailBunch = producerEmptyChain;
                producerEmptyChain = (Object[]) producerEmptyChain[REF_TO_NEXT_INDEX];
                newTailBunch[REF_TO_NEXT_INDEX] = null;
                newTailBunch[FIRST_ITEM_INDEX] = element;
                producerBunch[REF_TO_NEXT_INDEX] = newTailBunch;
                producerBunch = newTailBunch;
                producerIndex = SECOND_ITEM_INDEX;
                return true;
            }

            producerBunch[producerIndex++] = element;
            return true;
        }
    }
}
