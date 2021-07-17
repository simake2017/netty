package io.netty.example.test;

import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @Author:王洋
 * @Date:Created in 2018/3/2
 */
public class Test {


    public volatile int state = 0; //使用Integer有错误


    public static void main(String[] args) throws ClassNotFoundException, IllegalAccessException, InstantiationException, IOException {

//        test1();
//        test2();
//        AtomicIntegerFieldUpdater<Test> updater = TT.updater;
        TT t = new TT();
    }

    static class TT {

       public static  AtomicIntegerFieldUpdater<Test> updater = AtomicIntegerFieldUpdater.newUpdater(Test.class, "state");
    }



    public static void test2() throws IOException {

        System.out.println(SelectionKey.OP_READ);
        System.out.println(SelectionKey.OP_WRITE);
        System.out.println(SelectionKey.OP_CONNECT);
        System.out.println(SelectionKey.OP_ACCEPT);


        System.out.println(TimeUnit.SECONDS.toNanos(1)); //一秒钟换算成10亿纳秒
        System.out.println(TimeUnit.MICROSECONDS.toNanos(1)); //一微妙换算成1000纳秒
        System.out.println(TimeUnit.MILLISECONDS.toNanos(1)); //一毫秒换算成10万纳秒
    }


    public static void test1() throws ClassNotFoundException, IOException {
        System.out.println(TimeUnit.SECONDS.toNanos(1));
        //10 0000 0000

        Object maybeSelectorImplClass = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    return Class.forName(
                            "sun.nio.ch.SelectorImpl",
                            true,
                            PlatformDependent.getSystemClassLoader());
                } catch (Throwable cause) {
                    return cause;
                }
            }
        });

        AbstractSelector selector = SelectorProvider.provider().openSelector();
        System.out.println(selector);
        System.out.println("dddddddddddd");
        System.out.println(maybeSelectorImplClass instanceof Class);


        Class<?> clazz = (Class) maybeSelectorImplClass;
        //判定参数class派生于调用class
        System.out.println(Integer.class.isAssignableFrom(Number.class));//-->false
        System.out.println(Number.class.isAssignableFrom(Integer.class));//-->true
        boolean assignableFrom = clazz.isAssignableFrom(selector.getClass());
        System.out.println(assignableFrom);

        System.out.println("dddddddddddddd");

        //class sun.nio.ch.SelectorImpl
        System.out.println(maybeSelectorImplClass);

        Class<?> aClass = Class.forName("io.netty.example.test.T", true, Thread.currentThread().getContextClassLoader());
        System.out.println(aClass);

//        Object o = aClass.newInstance(); //--> 不能创建抽象类实例
//        System.out.println(o);true
    }


}
