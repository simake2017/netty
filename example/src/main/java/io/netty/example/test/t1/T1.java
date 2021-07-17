package io.netty.example.test.t1;

/**
 * @Author:王洋
 * @Date:Created in 2018/5/15
 */
public class T1 {

    private int i = 4;
    private String s = "a";

    public void t() {
        System.out.println("dddddddddddd");
    }


    public static void main(String[] args) {



    }


    /**
     * 这里是 非 static ，类里面可以 调用 外部的接口
     * 如果是static 那么 就是不可以的
     */
    class T11 {

        public void t11() {
            t();
        }

    }


}
