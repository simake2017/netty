/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.util.internal.MathUtil.safeFindNextPositivePowerOfTwo;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Light-weight object pool based on a thread-local stack.
 *
 * @param <T> the type of the pooled object
 */
/*
    基于线程局部的 轻量级 对象池


    wangyang @@@ Recycler 对象是 对象池 复用对象， 是一个抽象类，需要具体去实现


 */
public abstract class Recycler<T> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Recycler.class);

    @SuppressWarnings("rawtypes")
    /*
        表示一个空引用

     */
    private static final Handle NOOP_HANDLE = new Handle() {
        @Override
        public void recycle(Object object) {
            // NOOP
        }
    };
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(Integer.MIN_VALUE);
    /*
        这里是一个常量
     */
    private static final int OWN_THREAD_ID = ID_GENERATOR.getAndIncrement();
    private static final int DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD = 32768; // Use 32k instances as default. 2的15次幂 每个线程的最大初始分配容量
    private static final int DEFAULT_MAX_CAPACITY_PER_THREAD; //每个线程的最大分配容量 默认是 32k
    private static final int INITIAL_CAPACITY; //初始容量
    private static final int MAX_SHARED_CAPACITY_FACTOR; //最大容量因子2
    private static final int MAX_DELAYED_QUEUES_PER_THREAD;
    private static final int LINK_CAPACITY;
    private static final int RATIO;

    static {
        // In the future, we might have different maxCapacity for different object types.
        // e.g. io.netty.recycler.maxCapacity.writeTask
        //      io.netty.recycler.maxCapacity.outboundBuffer

        /*
            线程存储最大容量 32k = 32768

         */
        int maxCapacityPerThread = SystemPropertyUtil.getInt("io.netty.recycler.maxCapacityPerThread",
                SystemPropertyUtil.getInt("io.netty.recycler.maxCapacity", DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD));
        if (maxCapacityPerThread < 0) {
            maxCapacityPerThread = DEFAULT_INITIAL_MAX_CAPACITY_PER_THREAD;
        }

        DEFAULT_MAX_CAPACITY_PER_THREAD = maxCapacityPerThread; //线程最大容量

        /*
            共享容量
            这里是共享容量因子 ，共享的容量 = maxCapacity/ 该参数  = 32768/ 2 = 2的15次幂 /2 = 2的14次幂
         */
        MAX_SHARED_CAPACITY_FACTOR = max(2,
                SystemPropertyUtil.getInt("io.netty.recycler.maxSharedCapacityFactor",
                        2));  //默认为 2

        /*
            每个线程  中 最多有几个 WeakOrderQueue

         */
        MAX_DELAYED_QUEUES_PER_THREAD = max(0,
                SystemPropertyUtil.getInt("io.netty.recycler.maxDelayedQueuesPerThread",
                        // We use the same value as default EventLoop number
                        NettyRuntime.availableProcessors() * 2)); //最大延迟队列

        /*
            link的默认容量
         */
        LINK_CAPACITY = safeFindNextPositivePowerOfTwo(
                max(SystemPropertyUtil.getInt("io.netty.recycler.linkCapacity", 16), 16)); //默认返回16

        // By default we allow one push to a Recycler for each 8th try on handles that were never recycled before.
        // This should help to slowly increase the capacity of the recycler while not be too sensitive to allocation
        // bursts.
        /*
            属性为8   8的下一个幂是 16

         */
        RATIO = safeFindNextPositivePowerOfTwo(SystemPropertyUtil.getInt("io.netty.recycler.ratio", 8)); //默认返回8

        if (logger.isDebugEnabled()) {
            if (DEFAULT_MAX_CAPACITY_PER_THREAD == 0) {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: disabled");
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: disabled");
                logger.debug("-Dio.netty.recycler.linkCapacity: disabled");
                logger.debug("-Dio.netty.recycler.ratio: disabled");
            } else {
                logger.debug("-Dio.netty.recycler.maxCapacityPerThread: {}", DEFAULT_MAX_CAPACITY_PER_THREAD);
                logger.debug("-Dio.netty.recycler.maxSharedCapacityFactor: {}", MAX_SHARED_CAPACITY_FACTOR);
                logger.debug("-Dio.netty.recycler.linkCapacity: {}", LINK_CAPACITY);
                logger.debug("-Dio.netty.recycler.ratio: {}", RATIO);
            }
        }

        INITIAL_CAPACITY = min(DEFAULT_MAX_CAPACITY_PER_THREAD, 256); //初始容量 取32k和 256的最小值 为 256
    }

    private final int maxCapacityPerThread;
    private final int maxSharedCapacityFactor;
    private final int ratioMask;
    private final int maxDelayedQueuesPerThread;

    /*
        线程局部 参数化 ，相当于在这个 线程局部
        编译期的类型检查 运行期不做检查  ，在线程局部 的InternalThreadLocalMap 中 的  Objects[]中的 index位置进行 占据一个位置
     */
    /*
        wangyang Recycler 对象 中的 线程池使用 FastThreadLocal 获取的对象是 栈 Stack
     */
    private final FastThreadLocal<Stack<T>> threadLocal = new FastThreadLocal<Stack<T>>() {
        @Override
        protected Stack<T> initialValue() {
            /*
                wangyang
                这里使用Recycler.this 找到具体调用 的类，作为参数
             */
            return new Stack<T>(Recycler.this, Thread.currentThread(), maxCapacityPerThread, maxSharedCapacityFactor,
                    ratioMask, maxDelayedQueuesPerThread);
        }
    };

    protected Recycler() {
        this(DEFAULT_MAX_CAPACITY_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread) {
        this(maxCapacityPerThread, MAX_SHARED_CAPACITY_FACTOR);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor) {
        this(maxCapacityPerThread, maxSharedCapacityFactor, RATIO, MAX_DELAYED_QUEUES_PER_THREAD);
    }

    protected Recycler(int maxCapacityPerThread, int maxSharedCapacityFactor,
                       int ratio, int maxDelayedQueuesPerThread) {
        ratioMask = safeFindNextPositivePowerOfTwo(ratio) - 1; // 16（2的16次幂） 的 下一个幂 是 2的32次幂
        if (maxCapacityPerThread <= 0) {
            this.maxCapacityPerThread = 0;
            this.maxSharedCapacityFactor = 1;
            this.maxDelayedQueuesPerThread = 0;
        } else {
            this.maxCapacityPerThread = maxCapacityPerThread;
            this.maxSharedCapacityFactor = max(1, maxSharedCapacityFactor);
            this.maxDelayedQueuesPerThread = max(0, maxDelayedQueuesPerThread);
        }
    }

    @SuppressWarnings("unchecked")
    /*
        wangyang
        获取一个对象

     */
    public final T get() {
        if (maxCapacityPerThread == 0) {
            return newObject((Handle<T>) NOOP_HANDLE);
        }
        Stack<T> stack = threadLocal.get();
        DefaultHandle<T> handle = stack.pop();
        if (handle == null) {
            handle = stack.newHandle();
            handle.value = newObject(handle); //这里是对应的值
        }
        return (T) handle.value;
    }

    /**
     * @deprecated use {@link Handle#recycle(Object)}.
     * 这个方法已经不再使用了
     */
    @Deprecated
    public final boolean recycle(T o, Handle<T> handle) {
        if (handle == NOOP_HANDLE) {
            return false;
        }

        /*
            因为这里无法回收不是本对象的 handler

         */
        DefaultHandle<T> h = (DefaultHandle<T>) handle;
        if (h.stack.parent != this) {
            return false;
        }

        h.recycle(o);
        return true;
    }

    final int threadLocalCapacity() {
        return threadLocal.get().elements.length;
    }

    final int threadLocalSize() {
        return threadLocal.get().size;
    }

    protected abstract T newObject(Handle<T> handle);

    public interface Handle<T> {
        void recycle(T object);
    }

    static final class DefaultHandle<T> implements Handle<T> {
        private int lastRecycledId;
        private int recycleId;

        boolean hasBeenRecycled;

        private Stack<?> stack; //-->对应的栈
        private Object value;

        DefaultHandle(Stack<?> stack) {
            this.stack = stack;
        }

        /*
            这里是回收对象,目前使用这种回收方法
            这里的参数值一般是handler
         */
        @Override
        public void recycle(Object object) {
            if (object != value) {
                /*
                    这里判断 回收的对象是不是属于这个 handler，
                    如果不属于，抛出异常
                 */
                throw new IllegalArgumentException("object does not belong to handle");
            }
            stack.push(this); //这里的this是DefaultHandle
        }
    }

    /**
     * wangyang  使用一个 WeakHashMap 存储 相应的 stack 以及对应的 WeakOrderQueue
     */
    private static final FastThreadLocal<Map<Stack<?>, WeakOrderQueue>> DELAYED_RECYCLED =
            new FastThreadLocal<Map<Stack<?>, WeakOrderQueue>>() {
        @Override
        protected Map<Stack<?>, WeakOrderQueue> initialValue() {
            return new WeakHashMap<Stack<?>, WeakOrderQueue>();
        }
    };

    // a queue that makes only moderate guarantees about visibility: items are seen in the correct order,
    // but we aren't absolutely guaranteed to ever see anything at all, thereby keeping the queue cheap to maintain
    /*


     */
    private static final class WeakOrderQueue {

        /*
            用于表示最后一个节点
         */
        static final WeakOrderQueue DUMMY = new WeakOrderQueue();

        // Let Link extend AtomicInteger for intrinsics. The Link itself will be used as writerIndex.
        @SuppressWarnings("serial")
        private static final class Link extends AtomicInteger {
            /*
                link容量
             */
            private final DefaultHandle<?>[] elements = new DefaultHandle[LINK_CAPACITY];

            private int readIndex;
            private Link next; //--> 下一个link 指向
        }

        // chain of data items
        private Link head, tail;
        // pointer to another queue of delayed items for the same stack
        private WeakOrderQueue next; //队列的下一个节点
        private final WeakReference<Thread> owner; //拥有的线程， 弱引用
        private final int id = ID_GENERATOR.getAndIncrement(); // id
        private final AtomicInteger availableSharedCapacity; // 共享的容量

        private WeakOrderQueue() {
            owner = null;
            availableSharedCapacity = null;
        }

        private WeakOrderQueue(Stack<?> stack, Thread thread) {
            head = tail = new Link();
            owner = new WeakReference<Thread>(thread);

            // Its important that we not store the Stack itself in the WeakOrderQueue as the Stack also is used in
            // the WeakHashMap as key. So just store the enclosed AtomicInteger which should allow to have the
            // Stack itself GCed.
            availableSharedCapacity = stack.availableSharedCapacity;
        }

        /*
            通过下面这段代码 将新建立的队列放在头部，
            这里的影响主要是如果 这个 DefaultHandle放到不同的线程中，
            那么这里就会对应出 不同 线程的  同一个DefaultHandler的
            可以被不同的 线程 进行回收，所以handler有一个lastRecycleId，标识被上次回收的 recycleid
         */
        static WeakOrderQueue newQueue(Stack<?> stack, Thread thread) {
            WeakOrderQueue queue = new WeakOrderQueue(stack, thread);
            // Done outside of the constructor to ensure WeakOrderQueue.this does not escape the constructor and so
            // may be accessed while its still constructed.

            /*
                下面这句话的意思是将

             */
            stack.setHead(queue); //  将新建立的队列 放在栈的头部

            return queue;
        }


        private void setNext(WeakOrderQueue next) {
            assert next != this;
            this.next = next;
        }

        /**
         * Allocate a new {@link WeakOrderQueue} or return {@code null} if not possible.
         */

        /*
            分配一个 link_capacity 作为 WeakOrderQueue的容量

         */
        static WeakOrderQueue allocate(Stack<?> stack, Thread thread) {
            // We allocated a Link so reserve the space
            return reserveSpace(stack.availableSharedCapacity, LINK_CAPACITY) // link_capacity 默认16
                    ? WeakOrderQueue.newQueue(stack, thread) : null;
        }

        private static boolean reserveSpace(AtomicInteger availableSharedCapacity, int space) {
            assert space >= 0;
            for (;;) {
                int available = availableSharedCapacity.get();
                if (available < space) {
                    return false;
                }
                /*
                    多线程环境下，如果

                 */
                if (availableSharedCapacity.compareAndSet(available, available - space)) {
                    return true;
                }
            }
        }

        /*
            回收空间
         */
        private void reclaimSpace(int space) {
            assert space >= 0;
            availableSharedCapacity.addAndGet(space);
        }

        /*
            添加一个handle
            下面添加的时候要注意，一个link如果满了，就会新建一个link
         */
        void add(DefaultHandle<?> handle) {
            /*
                不在一个线程里面，则
                 上次回收的id = 当前WeakOrderQueue的id
             */
            handle.lastRecycledId = id; //上次更新的RecyclerId等于 当前 的 id

            Link tail = this.tail;
            int writeIndex;
            if ((writeIndex = tail.get()) == LINK_CAPACITY) {
                if (!reserveSpace(availableSharedCapacity, LINK_CAPACITY)) {
                    // Drop it.
                    return;
                }
                // We allocate a Link so reserve the space
                this.tail = tail = tail.next = new Link();

                writeIndex = tail.get();
            }
            tail.elements[writeIndex] = handle; //比如第一个就是 elements[0] = handle
            handle.stack = null; // handle原来的栈设为null
            // we lazy set to ensure that setting stack to null appears before we unnull it in the owning thread;
            // this also means we guarantee visibility of an element in the queue if we see the index updated
            tail.lazySet(writeIndex + 1); //设置value
        }

        boolean hasFinalData() {
            return tail.readIndex != tail.get();
        }

        // transfer as many items as we can from this queue to the stack, returning true if any were transferred
        @SuppressWarnings("rawtypes")
        boolean transfer(Stack<?> dst) {
            Link head = this.head;
            if (head == null) {
                return false;
            }

            if (head.readIndex == LINK_CAPACITY) {
                if (head.next == null) {
                    return false;
                }
                this.head = head = head.next; //head 节点
            }

            final int srcStart = head.readIndex;
            int srcEnd = head.get();
            final int srcSize = srcEnd - srcStart;
            if (srcSize == 0) {
                return false;
            }

            final int dstSize = dst.size; //这里应该是0，如果不是0，那么应该pop出来
            final int expectedCapacity = dstSize + srcSize;

            if (expectedCapacity > dst.elements.length) { //这里应该也不会大于，因为数组的最大容量就是 dst.elements.length
                final int actualCapacity = dst.increaseCapacity(expectedCapacity);
                srcEnd = min(srcStart + actualCapacity - dstSize, srcEnd);
            }

            if (srcStart != srcEnd) {
                final DefaultHandle[] srcElems = head.elements;
                final DefaultHandle[] dstElems = dst.elements;
                int newDstSize = dstSize;
                for (int i = srcStart; i < srcEnd; i++) {
                    DefaultHandle element = srcElems[i];
                    if (element.recycleId == 0) { // 在 WeakOrderQueue中存储的 handler的recycleId = 0 只有 lastRecycleId，表示，最后一次存储的RecycleId
                        element.recycleId = element.lastRecycledId;
                    } else if (element.recycleId != element.lastRecycledId) {
                        throw new IllegalStateException("recycled already");
                    }
                    srcElems[i] = null; //src 的对应数组元素这是为null

                    if (dst.dropHandle(element)) {
                        // Drop the object.
                        continue;
                    }
                    element.stack = dst; //元素的 栈设置为 当前栈
                    dstElems[newDstSize ++] = element; //这里进行复制
                }

                if (srcEnd == LINK_CAPACITY && head.next != null) {  //如果已经回收到了最后，那么 需要 回收这块区域
                    // Add capacity back as the Link is GCed.
                    reclaimSpace(LINK_CAPACITY);

                    this.head = head.next;
                }

                head.readIndex = srcEnd;  //标识当前的回收 位置
                if (dst.size == newDstSize) {
                    return false;
                }
                dst.size = newDstSize;
                return true;
            } else {
                // The destination stack is full already.
                return false;
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                super.finalize();
            } finally {
                // We need to reclaim all space that was reserved by this WeakOrderQueue so we not run out of space in
                // the stack. This is needed as we not have a good life-time control over the queue as it is used in a
                // WeakHashMap which will drop it at any time.
                Link link = head;
                while (link != null) {
                    reclaimSpace(LINK_CAPACITY);
                    link = link.next;
                }
            }
        }
    }

    /*
        wangyang   自己定义的 Stack class
     */
    static final class Stack<T> {

        // we keep a queue of per-thread queues, which is appended to once only, each time a new thread other
        // than the stack owner recycles: when we run out of items in our stack we iterate this collection
        // to scavenge those that can be reused. this permits us to incur minimal thread synchronisation whilst
        // still recycling all items.
        final Recycler<T> parent; // --> 所属Recycler对象
        final Thread thread; // --> stack所属线程
        final AtomicInteger availableSharedCapacity; //共享容量 =
        final int maxDelayedQueues; //最大队列护目（即 WeakOrderQueue护目）

        private final int maxCapacity; //最大容量 这里是 栈 存储 handler的最大容量
        private final int ratioMask; //用于掩码运算
        private DefaultHandle<?>[] elements; //elements 用于表示数组的长度
        private int size; //用于指定 当前 已有的 handler 数量
        private int handleRecycleCount = -1; // Start with -1 so the first one will be recycled.
        private WeakOrderQueue cursor, prev;
        private volatile WeakOrderQueue head;

        Stack(Recycler<T> parent, Thread thread, int maxCapacity, int maxSharedCapacityFactor,
              int ratioMask, int maxDelayedQueues) {
            this.parent = parent;
            this.thread = thread;
            this.maxCapacity = maxCapacity;
            availableSharedCapacity = new AtomicInteger(max(maxCapacity / maxSharedCapacityFactor, LINK_CAPACITY));
            elements = new DefaultHandle[min(INITIAL_CAPACITY, maxCapacity)]; //256
            this.ratioMask = ratioMask;
            this.maxDelayedQueues = maxDelayedQueues;
        }

        // Marked as synchronized to ensure this is serialized.
        /*
            *** 一个 stack 中存放的对象 可以放在不同的线程里面，如果在不同的线程里面，那么就要放在
            WeakQueue中，stack 中的对象不能放在其他stack 中

            wangyang 这里主要承载 stack 这个对象中 存放的 DefaultHandler ,放在
            放在不同的 线程的 Queue 中
         */
        synchronized void setHead(WeakOrderQueue queue) {
            queue.setNext(head);
            head = queue;
        }

        /*
            这里属于一个扩展，实际不会调用到这里

         */
        int increaseCapacity(int expectedCapacity) {
            int newCapacity = elements.length;
            int maxCapacity = this.maxCapacity;
            do {
                newCapacity <<= 1;
            } while (newCapacity < expectedCapacity && newCapacity < maxCapacity);

            newCapacity = min(newCapacity, maxCapacity);
            if (newCapacity != elements.length) {
                elements = Arrays.copyOf(elements, newCapacity);
            }

            return newCapacity;
        }

        /*
            pop

         */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        DefaultHandle<T> pop() {
            int size = this.size;
            /*
                如果 stack中的size = 0，都已经pop出来了，则需要在回收一些
             */
            if (size == 0) { //在没有的时候 从其他线程搜索一批
                if (!scavenge()) {
                    return null;
                }
                size = this.size;
            }
            /*
                返回当前对象   然后这个 size--
             */
            size --;
            DefaultHandle ret = elements[size];
            elements[size] = null;
            /*
                用于判断 recycleId

             */
            if (ret.lastRecycledId != ret.recycleId) {
                throw new IllegalStateException("recycled multiple times");
            }
            ret.recycleId = 0;
            ret.lastRecycledId = 0;
            this.size = size;
            return ret;
        }

        /*
            从weakOrderQueue
         */
        boolean scavenge() {
            // continue an existing scavenge, if any
            if (scavengeSome()) {
                return true;
            }

            // reset our scavenge cursor
            prev = null;
            cursor = head;
            return false;
        }

        boolean scavengeSome() {
            WeakOrderQueue prev;
            WeakOrderQueue cursor = this.cursor;
            if (cursor == null) {
                prev = null; //表示前一个节点
                cursor = head; //cursor表示游标节点，表示当前 操作到某一个节点
                if (cursor == null) {
                    return false;
                }
            } else {
                prev = this.prev;
            }

            boolean success = false;
            do {
                if (cursor.transfer(this)) { // --> 这里的this指的是 stack
                    success = true;
                    break;
                }
                WeakOrderQueue next = cursor.next;
                if (cursor.owner.get() == null) {
                    // If the thread associated with the queue is gone, unlink it, after
                    // performing a volatile read to confirm there is no data left to collect.
                    // We never unlink the first queue, as we don't want to synchronize on updating the head.
                    if (cursor.hasFinalData()) {
                        for (;;) {
                            if (cursor.transfer(this)) {
                                success = true;
                            } else {
                                break;
                            }
                        }
                    }

                    if (prev != null) {
                        prev.setNext(next);
                    }
                } else {
                    prev = cursor;
                }

                cursor = next;

            } while (cursor != null && !success);

            this.prev = prev;
            this.cursor = cursor;
            return success;
        }

        void push(DefaultHandle<?> item) {
            Thread currentThread = Thread.currentThread();
            if (thread == currentThread) {
                // The current Thread is the thread that belongs to the Stack, we can try to push the object now.
                /*
                    如果该stack就是本线程的stack，那么直接把DefaultHandle放到该stack的数组里
                 */
                pushNow(item);
            } else {
                // The current Thread is not the one that belongs to the Stack, we need to signal that the push
                // happens later.
                /*
                    如果该stack不是本线程的stack，那么把该DefaultHandle放到该stack的WeakOrderQueue中
                 */
                pushLater(item, currentThread);
            }
        }

        private void pushNow(DefaultHandle<?> item) {
            if ((item.recycleId | item.lastRecycledId) != 0) {
                throw new IllegalStateException("recycled already");
            }
            /*
                回收对象时，如果回收的对象属于 当前stack当中
                那么回收对象的recycleId 便置为一个值
             */
            item.recycleId = item.lastRecycledId = OWN_THREAD_ID;

            int size = this.size;
            if (size >= maxCapacity || dropHandle(item)) {
                // Hit the maximum capacity or should drop - drop the possibly youngest object.
                return;
            }
            /*
                在相等的情况下 扩展 数组的长度
                要么扩展左移1位，扩展2的幂指数，要么扩展到最大容量
             */
            if (size == elements.length) {
                elements = Arrays.copyOf(elements, min(size << 1, maxCapacity));
            }

            /*
                这里进行赋值

             */
            elements[size] = item;
            this.size = size + 1;
        }

        /*
            回收非本线程 的 handler
            把handler 和 线程 都传递进来
         */
        private void pushLater(DefaultHandle<?> item, Thread thread) {
            // we don't want to have a ref to the queue as the value in our weak map
            // so we null it out; to ensure there are no races with restoring it later
            // we impose a memory ordering here (no-op on x86)
            Map<Stack<?>, WeakOrderQueue> delayedRecycled = DELAYED_RECYCLED.get();
            /**
             *   wangyang  这里是当前线程里面 获取一个 Map<stack, Queue>对象
             */
            WeakOrderQueue queue = delayedRecycled.get(this); //这里的 this指的是 stack
            if (queue == null) {
                if (delayedRecycled.size() >= maxDelayedQueues) { //这里进行判断，如果 Map中key的数目（即weakOrderQueue的数目） 超过设置的最大数目
                    //房屋一个空的队列作为结尾，然后返回
                    // Add a dummy queue so we know we should drop the object
                    delayedRecycled.put(this, WeakOrderQueue.DUMMY);
                    return;
                }
                /*
                    stack 对象 可以在各个不同的线程里面 传递使用
                    weakQueue 的创建 都要在指定一个线程
                 */
                // Check if we already reached the maximum number of delayed queues and if we can allocate at all.
                if ((queue = WeakOrderQueue.allocate(this, thread)) == null) {
                    //如果等于null，说明没有分配，直接返回
                    // drop object
                    return;
                }
                delayedRecycled.put(this, queue); //分配成功，将栈和队列 的 映射关系，存储到 fastLocal中
            } else if (queue == WeakOrderQueue.DUMMY) {
                // drop object
                return;
            }

            queue.add(item);
        }

        boolean dropHandle(DefaultHandle<?> handle) {
            if (!handle.hasBeenRecycled) {
                if ((++handleRecycleCount & ratioMask) != 0) {
                    // Drop the object.
                    return true;
                }
                handle.hasBeenRecycled = true;
            }
            return false;
        }

        /*
            根据stack获取一个 handle 对象

         */
        DefaultHandle<T> newHandle() {
            return new DefaultHandle<T>(this);
        }
    }

    public static void main(String[] args) {


        /*
            获取左边0的数量

         */
        System.out.println(Integer.numberOfLeadingZeros(15)); // 28   15-->1111 == 32 - 4
        int i = Integer.numberOfLeadingZeros(16 - 1);
        System.out.println(Integer.toBinaryString(i));
        System.out.println(Integer.toBinaryString(1 << i));
        System.out.println(1<<4);

    }
}
