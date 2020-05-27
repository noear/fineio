package test.stringTest.server;

import org.noear.fineio.FineIO;
import org.noear.fineio.core.MessageHandler;
import test.stringTest.StringProtocol;

public class ServerTest {
    public static void main(String[] args) {
        //定义处理器
        //
        MessageHandler<String> handler = (session, message)->{
                System.out.println("收到：" + message);
                Thread.sleep(10);
                session.write("收到：" + message);
        };

        //启动服务
        //
        FineIO.server(new StringProtocol())
                .bind("localhost", 8080)
                .handle(handler.pools())
                .start(false);



        /**
         * 处理时间短的：handler
         * 处理时间长的：10ms或以上，用 handler.pools() //线程池模式
         * */
    }
}