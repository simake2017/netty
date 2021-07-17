package io.netty.example.test.MockTest;

import org.mockito.Mockito;

import java.util.List;

/**
 * @Author:王洋
 * @Date:Created in 2018/7/11
 */
public class MockTest {

    public static void main(String[] args) {

        List list = Mockito.mock(List.class);
        System.out.println(list.getClass());

    }





}
