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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

// TODO: move to junit5
@RunWith(Parameterized.class)
public class OrderCorrectnessTest {

    @Parameter(0)
    public QueueTail<Long> tail;

    @Parameter(1)
    public QueueHead<Long> head;

    @Test
    public void emptyQueue() {
        for (int i = 0; i < 100; i++) {
            Long element = head.removeFromHead();
            assertNull(element);
        }
    }

    @Test
    public void addAndGet() {
        tail.addToTail(1L);

        Long element = head.removeFromHead();
        assertNotNull(element);
        assertEquals(1L, (long) element);
    }

    @Test
    public void addAndGetAndAddAndGet() {
        Long element;

        tail.addToTail(1L);

        element = head.removeFromHead();
        assertNotNull(element);
        assertEquals(1L, (long) element);

        tail.addToTail(3L);

        element = head.removeFromHead();
        assertNotNull(element);
        assertEquals(3L, (long) element);
    }

    @Test
    public void noMoreElements() {
        for (int i = 0; i < 1000; i++) {
            tail.addToTail(1L);
        }

        for (int i = 0; i < 1000; i++) {
            head.removeFromHead();
        }

        Long element = head.removeFromHead();
        assertNull(element);
    }

    @Test
    public void addManyAndGetMany() {
        for (long i = 0; i < 10000000; i++) {
            boolean b = tail.addToTail(i);
            assertTrue(b);
        }

        for (long i = 0; i < 10000000; i++) {
            Long element = head.removeFromHead();
            assertNotNull(element);
            assertEquals(i, (long) element);
        }
    }

    @Test
    public void addBunchAndGetBunchMultipleTimes() {
        for (int l = 0; l < 10000; l++) {
            Object o1 = null;

            for (long i = 0; i < 10000; i++) {
                boolean result = tail.addToTail(i * l);
                assertTrue(result);
            }

            for (long i = 0; i < 10000; i++) {
                if (i == 0 && l == 1999) {
                    Object o = null;
                }

                Long element = head.removeFromHead();
                assertNotNull(element);
                assertEquals(i * l, (long) element);
            }
        }
    }

   /* @Test
    public void test1() {
        AtomicBufferRouter<Long> arrayQueue = new AtomicBufferRouter<>(4);
        QueueHead<Long> consumer = arrayQueue.createConsumer();
        QueueTail<Long> producer = arrayQueue.createProducer();

        producer.addToTail(1L);
        producer.addToTail(2L);
        producer.addToTail(3L);
        producer.addToTail(4L);

        consumer.removeFromHead();
        consumer.removeFromHead();
        consumer.removeFromHead();
        consumer.removeFromHead();

        producer.addToTail(5L);

    }*/

    @Parameters
    public static Collection<Object[]> data() {
        List<Object[]> list = new ArrayList<>();

        LinkedChunkBridge<Long> longSPSCSplittedLinkedQueue = new LinkedChunkBridge<>();
        list.add(new Object[] {longSPSCSplittedLinkedQueue, longSPSCSplittedLinkedQueue});

        RingBufferBridge<Long> arrayBridge = new RingBufferBridge<>(10000000);
        list.add(new Object[] {arrayBridge, arrayBridge});

        RingBufferRouter<Long> arrayQueue = new RingBufferRouter<>(10000000);
        list.add(new Object[] {arrayQueue.createProducer(), arrayQueue.createConsumer()});

        RingBufferDemultiplexer<Long> arrayDemultiplexer = new RingBufferDemultiplexer<>(10000000);
        list.add(new Object[] {arrayDemultiplexer, arrayDemultiplexer.createConsumer()});

        StealingLinkedChunkDemultiplexer<Long> stealingDemultiplexer = new StealingLinkedChunkDemultiplexer<>();
        list.add(new Object[] {stealingDemultiplexer, stealingDemultiplexer.createConsumer()});

        GreedyRingBufferRouter<Long> greedyArrayQueue = new GreedyRingBufferRouter<>(10000000);
        list.add(new Object[] {greedyArrayQueue.createProducer(), greedyArrayQueue.createConsumer()});

        GreedyRingBufferMultiplexer<Long> longLinkedArrayMultiplexer = new GreedyRingBufferMultiplexer<>(10000000);
        list.add(new Object[] {longLinkedArrayMultiplexer.createProducer(), longLinkedArrayMultiplexer});

        AtomicPoolRouter<Long> arrayPool = new AtomicPoolRouter<>(10000000);
        list.add(new Object[] {arrayPool.createProducer(), arrayPool.createConsumer()});

        return list;
    }
}
