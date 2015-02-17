package org.jsl.dtest;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class Main
{
    private static class Item
    {
        public int value;

        public Item()
        {
            this.value = Integer.MAX_VALUE;
        }
    }

    private class ItemFactory implements EventFactory<Item>
    {
        public Item newInstance()
        {
            return new Item();
        }
    }

    private class ItemHandler implements EventHandler<Item>
    {
        private long m_startTime;

        public void onEvent(Item event, long sequence, boolean endOfBatch)
        {
            if (event.value == 0)
                m_startTime = System.nanoTime();
            else if (event.value == -1)
            {
                final long delay = ((System.nanoTime() - m_startTime) / 1000);
                System.out.println(
                        "Processed at " +
                        String.format("%d.%06d", delay/1000000, delay%1000000) + " sec." );
                m_sema.release();
            }
        }
    }

    private final Semaphore m_sema;

    public Main()
    {
        m_sema = new Semaphore(0);
    }

    public void run( int ops )
    {
        System.out.println( "**** Disruptor" );

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Disruptor<Item> disruptor = new Disruptor<Item>(
                new ItemFactory(), 1024*1024, executor, ProducerType.SINGLE, new BlockingWaitStrategy() );
        final RingBuffer<Item> ringBuffer = disruptor.getRingBuffer();
        disruptor.handleEventsWith( new ItemHandler() );
        disruptor.start();

        long sequence;
        final long startTime = System.nanoTime();
        for (int idx=0; idx<ops; idx++)
        {
            sequence = ringBuffer.next();
            try
            {
                final Item item = ringBuffer.get( sequence );
                item.value = idx;
            }
            finally
            {
                ringBuffer.publish( sequence );
            }
        }
        final long delay = ((System.nanoTime() - startTime) / 1000);

        sequence = ringBuffer.next();
        try
        {
            final Item item = ringBuffer.get( sequence );
            item.value = -1;
        }
        finally
        {
            ringBuffer.publish( sequence );
        }

        System.out.println(
                ops + " events dispatched at " +
                String.format("%d.%06d", delay/1000000, delay%1000000) + " sec." );

        try { m_sema.acquire(); }
        catch (InterruptedException ex) { ex.printStackTrace(); }

        disruptor.shutdown();
        executor.shutdownNow();
    }

    public static void main( String [] args )
    {
        new Main().run( 1000 ); /* warm up */
        System.gc();
        new Main().run( 1000000 );
    }
}
