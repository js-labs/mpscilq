/*
 * Copyright (C) 2015 Sergey Zubarev, info@js-labs.org
 * https://github.com/js-labs/mpscilq.git
 * The code is distributed under CPOL license.
 */

package org.jsl.jct;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class MpscIntrusiveLinkedQueue<T>
{
    private final AtomicReferenceFieldUpdater<T, T> m_itemUpdater;
    private T m_head;
    private final AtomicReference<T> m_tail;

    public MpscIntrusiveLinkedQueue( AtomicReferenceFieldUpdater<T, T> itemUpdater )
    {
        m_itemUpdater = itemUpdater;
        m_tail = new AtomicReference<T>();
    }

    public final boolean put( T item )
    {
        assert( m_itemUpdater.get(item) == null );
        for (;;)
        {
            final T tail = m_tail.get();
            if (m_tail.compareAndSet(tail, item))
            {
                if (tail == null)
                {
                    m_head = item;
                    return true;
                }
                else
                {
                    m_itemUpdater.set( tail, item );
                    return false;
                }
            }
        }
    }

    public final T get()
    {
        assert( m_head != null );
        return m_head;
    }

    public final T get_next()
    {
        assert( m_head != null );
        final T head = m_head;
        T next = m_itemUpdater.get( head );
        if (next == null)
        {
            m_head = null;
            if (m_tail.compareAndSet(head, null))
                return null;
            while ((next = m_itemUpdater.get(head)) == null);
        }
        m_itemUpdater.lazySet( head, null );
        m_head = next;
        return m_head;
    }
}
