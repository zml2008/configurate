/*
 * Configurate
 * Copyright (C) zml and Configurate contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.spongepowered.configurate.reactive;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.reactive.Disposable;
import org.spongepowered.configurate.reactive.Processor;
import org.spongepowered.configurate.reactive.Publisher;
import org.spongepowered.configurate.reactive.Subscriber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessorBaseTest {

    @Test
    public void testSubmission() {
        final String[] result = new String[1];
        Processor.Iso<String> proc = Processor.create();
        proc.subscribe(item -> {
            result[0] = item;
        });
        proc.submit("Hello");
        assertEquals("Hello", result[0]);
    }

    @Test
    public void testUnsubscribe() {
        final String[] result = new String[1];
        final boolean[] closed = new boolean[1];
        Processor.Iso<String> proc = Processor.create();
        Disposable disp = proc.subscribe(new Subscriber<String>() {
            @Override
            public void submit(String item) {
                result[0] = item;
            }

            @Override
            public void onClose() {
                closed[0] = true;
            }
        });
        proc.submit("Hello");
        disp.dispose();
        proc.submit("World");
        assertEquals("Hello", result[0]);
        assertFalse(closed[0]);
    }

    @Test
    public void testClose() {
        final boolean[] result = new boolean[1];
        Processor.Iso<String> proc = Processor.create();
        proc.subscribe(new Subscriber<String>() {
            @Override
            public void submit(String item) {
                // no-op -- we are only testing closing
            }

            @Override
            public void onClose() {
                result[0] = true;
            }
        });

        proc.onClose();
        assertTrue(result[0]);
        assertFalse(proc.hasSubscribers());
    }

    @Test
    public void testCloseIfUnsubscribed() {
        Processor.Iso<Boolean> proc = Processor.create();

        Disposable disp = proc.subscribe(x -> {});
        assertFalse(proc.closeIfUnsubscribed());
        disp.dispose();
        assertTrue(proc.closeIfUnsubscribed());

        // Test repeated calls
        assertTrue(proc.closeIfUnsubscribed());
    }

    @Test
    public void testMap() {
        final String[] items = new String[2];
        Processor.Iso<String> orig = Processor.create();
        Publisher<String> mapped = orig.map(x -> "2" + x);
        orig.subscribe(item -> items[0] = item);
        mapped.subscribe(item -> items[1] = item);
        orig.submit("Fast");

        assertEquals("Fast", items[0]);
        assertEquals("2Fast", items[1]);
    }

    @Test
    public void testErrorCloses() {
        final int[] callCount = new int[1];
        Processor.Iso<String> subject = Processor.create();
        final RuntimeException testExc = new RuntimeException();

        subject.subscribe(new Subscriber<String>() {
            @Override
            public void submit(final String item) {
                callCount[0]++;
                throw testExc;
            }

            @Override
            public void onError(final Throwable e) {
                assertEquals(testExc, e);
            }
        });

        assertEquals(0, callCount[0]);
        subject.submit("i don't matter");

        assertEquals(1, callCount[0]);
        assertFalse(subject.hasSubscribers());

        subject.submit("I shouldn't be received");
        assertEquals(1, callCount[0]);
    }

    @Test
    public void testMappedUnsubscribedOnEmpty() {
        final String[] items = new String[2];
        Processor.Iso<String> orig = Processor.create();
        Publisher<String> mapped = orig.map(x -> "2" + x);

        // initial state of unsubscribed
        assertFalse(orig.hasSubscribers());

        // subscribe on listener
        Disposable disp = mapped.subscribe(it -> items[0] = it);
        assertTrue(orig.hasSubscribers());

        // and unsubsubscribe again
        disp.dispose();
        assertFalse(orig.hasSubscribers());

        // repeatibility
        disp = mapped.subscribe(it -> items[0] = it);
        assertTrue(orig.hasSubscribers());

        // only last subscriber should trigger mapping to unsubscribe
        Disposable disp2 = mapped.subscribe(it -> items[1] = it);
        disp.dispose();
        assertTrue(orig.hasSubscribers());

        disp2.dispose();
        assertFalse(orig.hasSubscribers());
    }

    @Test
    public void testFallbackHandler() {
        Processor.Iso<String> handler = Processor.create();
        final String[] values = new String[2];

        handler.setFallbackHandler(val -> values[0] = val);
        handler.submit("Hello");
        assertEquals("Hello", values[0]);

        Disposable disp = handler.subscribe(val -> values[1] = val);
        handler.submit("World");
        assertEquals("Hello", values[0]);
        assertEquals("World", values[1]);

        disp.dispose();
        handler.submit("Goodbye");
        assertEquals("Goodbye", values[0]);
        assertEquals("World", values[1]);
    }
}
