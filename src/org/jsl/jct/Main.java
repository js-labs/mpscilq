/*
 * Copyright (C) 2015 Sergey Zubarev, info@js-labs.org
 * https://github.com/js-labs/mpscilq.git
 * The code is distributed under CPOL license.
 */

package org.jsl.jct;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class Main
{
    private static class Item
    {
        public volatile Item nextItem;
        public final int value;

        public Item( int value )
        {
            this.value = value;
        }
    }

    private class Consumer extends Thread
    {
        public void run()
        {
            try
            {
                /* Sync threads */
                while (m_state.get() != 1);
                m_state.set( 2 );

                int ops = 0;
                final long startTime = System.nanoTime();

                loop: for (;;)
                {
                    m_sema.acquire();
                    Item item = m_queue.get();
                    do
                    {
                        if (item.value == -1)
                            break loop;
                        ops++;
                        item = m_queue.get_next();
                    }
                    while (item != null);
                }

                final long delay = ((System.nanoTime() - startTime) / 1000);
                System.out.println(
                        ops + " events processed at " +
                        String.format("%d.%06d", delay/1000000, delay%1000000) + " sec." );

            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    private final MpscIntrusiveLinkedQueue<Item> m_queue;
    private final Semaphore m_sema;
    private final AtomicInteger m_state;

    public Main()
    {
        m_queue = new MpscIntrusiveLinkedQueue<Item>(
                AtomicReferenceFieldUpdater.newUpdater(Item.class, Item.class, "nextItem") );
        m_sema = new Semaphore( 0 );
        m_state = new AtomicInteger();
    }

    public void run( int ops )
    {
        System.out.println( "**** MpscIntrusiveLinkedQueue" );

        final Item [] items = new Item[ops+1];
        int idx = 0;
        for (; idx<ops; idx++)
            items[idx] = new Item( idx );

        /* The last item with value (-1) indicates end of test */
        items[idx] = new Item( -1 );

        final Consumer consumer = new Consumer();
        consumer.start();

        /* Sync threads */
        m_state.set(1);
        while (m_state.get() != 2);

        int wakeUps = 0;
        final long startTime = System.nanoTime();
        for (idx=0; idx<ops+1; idx++)
        {
            if (m_queue.put(items[idx]))
            {
                m_sema.release();
                wakeUps++;
            }
        }
        final long delay = ((System.nanoTime() - startTime) / 1000);

        System.out.println(
                ops + " events dispatched at " +
                String.format("%d.%06d", delay/1000000, delay%1000000) + " sec (" +
                wakeUps + " wake ups).");

        try { consumer.join(); }
        catch (InterruptedException ex) { ex.printStackTrace(); }
    }

    public static void main( String [] args )
    {
        new Main().run(1000); /* warm up */
        System.gc();
        new Main().run(1000000);
    }
}
