package org.thepun.queue.spsc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MultipleThreadsTest {

    private volatile long result;

    @Test
    public void addAndGet() throws InterruptedException {
        SPSCLinkedArrayQueue<Long> queue = new SPSCLinkedArrayQueue<>();

        for (int k = 0; k < 100; k++) {
            result = 0;

            class ProducerThraed extends Thread {
                @Override
                public void run() {
                    for (long i = 0; i < 1000000000; i++) {
                        queue.addToTail(i);
                    }
                }
            }

            class ConsumerThread extends Thread {
                @Override
                public void run() {
                    long tempValue = 0;
                    for (long i = 0; i < 1000000000; i++) {
                        Long value;
                        do {
                            value = queue.removeFromHead();
                        } while (value == null);

                        /*if (i != value) {
                            Object o = null;
                        }*/

                        assertEquals(i, (long) value);

                        tempValue += value;
                    }
                    result += tempValue;
                }
            }

            ProducerThraed producerThraed = new ProducerThraed();
            producerThraed.start();

            ConsumerThread consumerThread = new ConsumerThread();
            consumerThread.start();

            producerThraed.join();
            consumerThread.join();

            assertEquals(49999995000000L, result);

            System.out.println("Iteration done!");
        }
    }

}
