package io.netty.example.test.socketTest;

import java.util.Iterator;
import java.util.ServiceLoader;
/**
 * @Author:王洋
 * @Date:Created in 2018/9/6
 */
public class ServiceLoaderTest {

    /*
        下面这种方式就是 使用serviceLoader，从 meta-info/services目录下进行寻找，


     */
    public static void main(String[] args) {

        System.out.println("------------");
        ServiceLoader<Amimal> serviceLoader = ServiceLoader.load(Amimal.class);
        Iterator<Amimal> animalIterator = serviceLoader.iterator();
        System.out.println("=============");
        while(animalIterator.hasNext()){
            Amimal animal = animalIterator.next();
            animal.eat();
        }

    }

}


