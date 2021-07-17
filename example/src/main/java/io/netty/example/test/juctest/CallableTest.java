package io.netty.example.test.juctest;

import java.util.concurrent.*;

/**
 * @Author:王洋
 * @Date:Created in 2018/6/12
 */
public class CallableTest {

    public static void main(String[] args) {

        ExecutorService threadPool = Executors.newSingleThreadExecutor();
        //future的泛型类型必须要与callable的泛型类型相一致
        Future<String> future   =   threadPool.submit(new Callable<String>(){
            @Override
            public String call() throws Exception {
                Thread.sleep(2000);
                return "hello";
            }
        });

        System.out.println("等待结果....");
        try {
            //future.get方法会一直等待，也会阻塞线程
            System.out.println("拿到结果：future.get方法的结果是"+future.get());
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("aaaaaaaaaaaa");
        threadPool.shutdown();//必须显示关闭
    }




}
