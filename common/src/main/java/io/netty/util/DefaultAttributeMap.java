/*
 * Copyright 2012 The Netty Project
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Default {@link AttributeMap} implementation which use simple synchronization per bucket to keep the memory overhead
 * as low as possible.
 */

/*
 *  一个自定义的 AttributeMap 数据结构， 根据一个AttributeKey 获取一个Attribute对象
 *
 */
public class DefaultAttributeMap implements AttributeMap {

    @SuppressWarnings("rawtypes")
    /**
     * 对某个类的字段进行反射的原子更新
     */
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> updater =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, AtomicReferenceArray.class, "attributes");

    private static final int BUCKET_SIZE = 4;
    private static final int MASK = BUCKET_SIZE  - 1;

    // Initialize lazily to reduce memory consumption; updated by AtomicReferenceFieldUpdater above.
    @SuppressWarnings("UnusedDeclaration")

    /**
     * 对volatile字段 进行原子更新
     *
     */
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;//原子数组大小

    @SuppressWarnings("unchecked")
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // Not using ConcurrentHashMap due to high memory consumption.
            attributes = new AtomicReferenceArray<DefaultAttribute<?>>(BUCKET_SIZE);

            //使用反射，对当前对象的attributes字段进行更新
            if (!updater.compareAndSet(this, null, attributes)) {
                attributes = this.attributes;
            }
        }

        /**
         * 这里 相当于是一个取模 所以 比如 mask = 10 ,那么 1 跟11
         * 获得的index 都是1
         * 所以使用链表循环的方式
         */
        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // No head exists yet which means we may be able to add the attribute without synchronization and just
            // use compare and set. At worst we need to fallback to synchronization and waste two allocations.
            head = new DefaultAttribute();
            DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
            head.next = attr;
            attr.prev = head;
            if (attributes.compareAndSet(i, null, head)) {
                // we were able to add it so return the attr right away
                return attr;
            } else {
                head = attributes.get(i);
            }
        }

        synchronized (head) {
            DefaultAttribute<?> curr = head;
            for (;;) {
                /**
                 * 这里的意思就是 从头开始往后找 直到找到 key相等的元素
                 * wangyang
                 * 重要
                 */
                DefaultAttribute<?> next = curr.next;
                /**
                 * 通过循环，形成一个链式结构，直到找到next = null为止
                 */
                if (next == null) {
                    DefaultAttribute<T> attr = new DefaultAttribute<T>(head, key);
                    curr.next = attr;
                    attr.prev = curr;
                    return attr;
                }

                if (next.key == key && !next.removed) { //判断是否移除掉
                    return (Attribute<T>) next;
                }
                curr = next;
            }
        }
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (attributes == null) {
            // no attribute exists
            return false;
        }

        /**
         * 每一个key都会根据他的id与 -1 做与运算，
         */
        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (head == null) {
            // No attribute exists which point to the bucket in which the head should be located
            return false;
        }

        /**
         * 感觉这种方式没必要
         * 因为key的id不会发生重复，哪怕是多线程情况下，因为是使用AtomicInteger方式
         */
        // We need to synchronize on the head.
        synchronized (head) {
            // Start with head.next as the head itself does not store an attribute.
            DefaultAttribute<?> curr = head.next;
            while (curr != null) {
                if (curr.key == key && !curr.removed) {
                    return true;
                }
                curr = curr.next;
            }
            return false;
        }
    }

    private static int index(AttributeKey<?> key) {
        return key.id() & MASK;
    }

    @SuppressWarnings("serial")
    /*
     * DefaultAttribute是一个 有链式结构的 对象结构
     */
    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        // The head of the linked-list this attribute belongs to
        private final DefaultAttribute<?> head;//头结点
        private final AttributeKey<T> key; //当前对象的key

        // Double-linked list to prev and next node to allow fast removal
        private DefaultAttribute<?> prev; //当前对象的前一个节点
        private DefaultAttribute<?> next; //当前对象的后一个节点

        // Will be set to true one the attribute is removed via getAndRemove() or remove()
        private volatile boolean removed;

        DefaultAttribute(DefaultAttribute<?> head, AttributeKey<T> key) {
            this.head = head;
            this.key = key;
        }

        // Special constructor for the head of the linked-list.
        DefaultAttribute() {
            head = this;
            key = null;
        }

        /**
         * 返回该Attribute对应的key
         * @return
         */
        @Override
        public AttributeKey<T> key() {
            return key;
        }

        /**
         * 使用AtomicReference提供的cas方法进行设置，如果是空的，设置
         * 如果原来存在值，则返回原来的值
         * @param value
         * @return
         */
        @Override
        public T setIfAbsent(T value) {
            while (!compareAndSet(null, value)) {
                T old = get();
                if (old != null) {
                    return old;
                }
            }
            return null;
        }

        /**
         *
         * @return
         */
        @Override
        public T getAndRemove() {
            removed = true;
            //返回的是旧值，将值设置为null
            T oldValue = getAndSet(null);//原子性设置，返回旧的值，设置新的值
            remove0();
            return oldValue;
        }

        @Override
        public void remove() {
            removed = true;
            set(null);
            remove0();
        }

        /**
         * 从数据结构中，移除当前节点
         */
        private void remove0() {
            synchronized (head) {
                if (prev == null) { //不存在前驱节点
                    // Removed before.
                    return;
                }

                prev.next = next;//前驱节点的next指向当前节点的next节点

                if (next != null) {
                    next.prev = prev; //设置next的前驱节点
                }

                // Null out prev and next - this will guard against multiple remove0() calls which may corrupt
                // the linked list for the bucket.
                //设置当前节点的前驱与猴急节点。
                prev = null;
                next = null;
            }
        }
    }

    public static void main(String[] args) {

        int i = 101;
        int j = 1;
        System.out.println(i & j);


    }
}
