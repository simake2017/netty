package io.netty.example.test.socketTest;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @Author:王洋
 * @Date:Created in 2018/9/4
 */

/*

    Socks socket是一个  防火墙 转化 协议，开始创建的Socket，里面都有一个 impl，默认是 SocksSocketImpl
    但是当通过 accept成功之后，就会将 他的代理类（具体协议类，比如DualStackPlainSocketImpl）取出来，
    作为socketImpl，然后便可以通过他进行 实际的 协议通讯

    DualStackPlainSocketImpl是一个 具体协议的类（tcp）


 */
/*
    这里的代码，就是 用 socket实现 了一个 web服务器的功能，能够使用ie浏览器发送 一个 http请求，http是应用层协议，
    底层的发送也是 使用的 socket通讯，这里 就是 使用sokcet 创建的一个 简单的 web服务，能够对http请求信息
    进行响应， 在输出的时候，只要按照 http协议的格式输出，那么 浏览器就可以 进行解析，基本格式如下：
    响应部分：首先输出 协议，标准 状态码 OK，然后输出 头部，头部结束，输出一个空行，其实这就是tomcat的原理。

        out.println("HTTP/1.0 200 OK");
        //返回一个首部
        out.println("Content-Type:text/html;charset=" + request.getEncoding());
        // 根据 HTTP 协议, 空行将结束头信息
        out.println()

 */
public class SocketTest {

    public static void main( String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(6666);
        Socket socket = serverSocket. accept();
        InputStream inputStream = socket. getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader( inputStream);
        BufferedReader bufferedReader = new BufferedReader( inputStreamReader);
        String getString = "";


        System.out.println("dddddddddddddddddddd");
        while (!"".equals( getString = bufferedReader. readLine())) {
            System. out. println( getString);
        }

        OutputStream outputStream = socket.getOutputStream();

//        PrintWriter out = new PrintWriter(outputStream);
//        out.println("HTTP/1.1 200 OK");
//        out.println("<html><body> <a href=' http:// www.baidu.com'> i am baidu. com welcome you!</a> </body></html>");
//        out.println();

        /*
            下面这种方式就可以 正常解析

         */
        byte[] bytes = ("HTTP/1.1 200 OK" + System.getProperty("line.separator")  ) .getBytes();
        outputStream. write(bytes);
        outputStream.write(System.getProperty("line.separator") .getBytes());
        outputStream.write("dddddddddd".getBytes());
        outputStream. write( "<html><body> <a href=' http:// www.baidu.com'> i am baidu. com welcome you!</a> </body></html>". getBytes());
        outputStream. flush();
        inputStream. close();
        outputStream. close();
        socket. close();
        serverSocket. close();
    }


}
