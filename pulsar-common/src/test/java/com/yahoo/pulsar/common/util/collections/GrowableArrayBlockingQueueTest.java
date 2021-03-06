/**
 * Copyright 2016 Yahoo Inc.
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
package com.yahoo.pulsar.common.util.collections;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import com.google.common.collect.Lists;

public class GrowableArrayBlockingQueueTest {

    @Test
    public void simple() throws Exception {
        BlockingQueue<Integer> queue = new GrowableArrayBlockingQueue<>(4);

        assertEquals(queue.poll(), null);

        assertEquals(queue.remainingCapacity(), Integer.MAX_VALUE);
        assertEquals(queue.toString(), "[]");

        try {
            queue.element();
            fail("Should have thrown exception");
        } catch (NoSuchElementException e) {
            // Expected
        }

        try {
            queue.iterator();
            fail("Should have thrown exception");
        } catch (UnsupportedOperationException e) {
            // Expected
        }

        // Test index rollover
        for (int i = 0; i < 100; i++) {
            queue.add(i);

            assertEquals(queue.take().intValue(), i);
        }

        queue.offer(1);
        assertEquals(queue.toString(), "[1]");
        queue.offer(2);
        assertEquals(queue.toString(), "[1, 2]");
        queue.offer(3);
        assertEquals(queue.toString(), "[1, 2, 3]");
        queue.offer(4);
        assertEquals(queue.toString(), "[1, 2, 3, 4]");

        assertEquals(queue.size(), 4);

        List<Integer> list = new ArrayList<>();
        queue.drainTo(list, 3);

        assertEquals(queue.size(), 1);
        assertEquals(list, Lists.newArrayList(1, 2, 3));
        assertEquals(queue.toString(), "[4]");
        assertEquals(queue.peek().intValue(), 4);

        assertEquals(queue.element().intValue(), 4);
        assertEquals(queue.remove().intValue(), 4);
        try {
            queue.remove();
            fail("Should have thrown exception");
        } catch (NoSuchElementException e) {
            // Expected
        }
    }

    @Test(timeOut = 10000)
    public void blockingTake() throws Exception {
        BlockingQueue<Integer> queue = new GrowableArrayBlockingQueue<>();

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                int expected = 0;

                for (int i = 0; i < 100; i++) {
                    int n = queue.take();

                    assertEquals(n, expected++);
                }

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        int n = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                queue.put(n);
                ++n;
            }

            // Wait until all the entries are consumed
            while (!queue.isEmpty()) {
                Thread.sleep(1);
            }
        }

        latch.await();
    }

    @Test
    public void growArray() throws Exception {
        BlockingQueue<Integer> queue = new GrowableArrayBlockingQueue<>(4);

        assertEquals(queue.poll(), null);

        assertTrue(queue.offer(1));
        assertTrue(queue.offer(2));
        assertTrue(queue.offer(3));
        assertTrue(queue.offer(4));
        assertTrue(queue.offer(5));

        assertEquals(queue.size(), 5);

        queue.clear();
        assertEquals(queue.size(), 0);

        assertTrue(queue.offer(1, 1, TimeUnit.SECONDS));
        assertTrue(queue.offer(2, 1, TimeUnit.SECONDS));
        assertTrue(queue.offer(3, 1, TimeUnit.SECONDS));
        assertEquals(queue.size(), 3);

        List<Integer> list = new ArrayList<>();
        queue.drainTo(list);
        assertEquals(queue.size(), 0);

        assertEquals(list, Lists.newArrayList(1, 2, 3));
    }

    @Test(timeOut = 10000)
    public void pollTimeout() throws Exception {
        BlockingQueue<Integer> queue = new GrowableArrayBlockingQueue<>(4);

        assertEquals(queue.poll(1, TimeUnit.MILLISECONDS), null);

        queue.put(1);
        assertEquals(queue.poll(1, TimeUnit.MILLISECONDS).intValue(), 1);

        // 0 timeout should not block
        assertEquals(queue.poll(0, TimeUnit.HOURS), null);

        queue.put(2);
        queue.put(3);
        assertEquals(queue.poll(1, TimeUnit.HOURS).intValue(), 2);
        assertEquals(queue.poll(1, TimeUnit.HOURS).intValue(), 3);
    }

    @Test(timeOut = 10000)
    public void pollTimeout2() throws Exception {
        BlockingQueue<Integer> queue = new GrowableArrayBlockingQueue<>();

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                queue.poll(1, TimeUnit.HOURS);

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Make sure background thread is waiting on poll
        Thread.sleep(100);
        queue.put(1);

        latch.await();
    }
}
