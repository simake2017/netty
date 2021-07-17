package io.netty.example.test.t1;

import org.openjdk.jol.info.ClassLayout;
import sun.misc.VMSupport;

import java.lang.instrument.Instrumentation;

/**
 * @Author:王洋
 * @Date:Created in 2018/8/7
 */
public class TestJol extends T1{



    private int i = 3;

    public static void main(String[] args) {





//        System.out.println(VMSupport.);

        System.out.println(ClassLayout.parseClass( TestJol.class).toPrintable());

        while (true);

    }

}
