package io.netty.example.test.current;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author:王洋
 * @Date:Created in 2018/9/11
 */
public class Test {

    private static final int COUNT_BITS = Integer.SIZE - 3;
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1; //任务容量 0001111..... ，表示 可以达到的最高 任务容量
    // runState is stored in the high-order bits
    private static final int RUNNING    = -1 << COUNT_BITS; //111 0000     左移 （32 - 3） 剩余 最后3位
    private static final int SHUTDOWN   =  0 << COUNT_BITS; //0000....
    private static final int STOP       =  1 << COUNT_BITS; // 00010000
    private static final int TIDYING    =  2 << COUNT_BITS; //0010000.....
    private static final int TERMINATED =  3 << COUNT_BITS; //001100.....

    private static int workerCountOf(int c)  { return c & CAPACITY; } //
    private static int ctlOf(int rs, int wc) { return rs | wc; }
    private static int runStateOf(int c)     { return c & ~CAPACITY; }

    private static final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0)); // 111 0000....  // -->用于控制 任务数量




    public static void main(String[] args) {

        System.out.println(Integer.toBinaryString(Test.ctl.get())); //11100000000000000000000000000000
        System.out.println(Integer.toBinaryString(RUNNING)); //11100000000000000000000000000000
        System.out.println(Integer.toBinaryString(SHUTDOWN));
        System.out.println(Integer.toBinaryString(STOP));
        System.out.println(Integer.toBinaryString(CAPACITY));// 00011111111111111111111111111111
        System.out.println(Integer.toBinaryString(workerCountOf(Test.ctl.get()))); // 0
        System.out.println(Integer.toBinaryString(runStateOf(Test.ctl.get()))); //11100000000000000000000000000000

        Test.ctl.addAndGet(1);

        System.out.println(Integer.toBinaryString(runStateOf(Test.ctl.get()))); // 还是他 11100000000000000000000000000000


    }

}
