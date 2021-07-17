package io.netty.example.test.juctest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @Author:王洋
 * @Date:Created in 2018/9/18
 */
public class ThreadFactoryTest {

    public static void main(String[] args) {

        ExecutorService service = Executors.newFixedThreadPool(4);

    }

    class R1 implements Runnable {

        @Override
        public void run() {
            System.out.println("r1111111111111111111");
            try {
                TimeUnit.SECONDS.sleep(4l);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class R2 implements Runnable {

        @Override
        public void run() {
            System.out.println("r22222222222221");
            try {
                TimeUnit.SECONDS.sleep(2l);
                System.out.println("00000000000000000000000");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }





}
