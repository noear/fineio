package org.noear.fineio.tcp;

import org.noear.fineio.FineException;
import org.noear.fineio.core.NetConnector;
import org.noear.fineio.core.IoConfig;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class NioTcpConnector<T> extends NetConnector<T> {

    private CompletableFuture<Integer> connectionFuture;

    private Selector selector;
    private SocketChannel channel;
    private NioWriteBuffer<T> writeBuffer;
    private final NioTcpAcceptor<T> acceptor;
    private Object lock = "";

    public NioTcpConnector(IoConfig<T> config){
        super(config);
        this.connectionFuture = new CompletableFuture<>();
        this.acceptor = new NioTcpAcceptor<>(config, false);
    }

    public NetConnector<T> connection() throws IOException {
        synchronized (lock) {
            if (selector != null) {
                return this;
            }
        }

        selector = Selector.open();

        channel = SocketChannel.open();
        channel.configureBlocking(false);

        writeBuffer = new NioWriteBuffer<T>(config, channel);

        //尝试连接
        if(channel.connect(config.getAddress())){
            channel.register(selector, SelectionKey.OP_READ);
            connectionFuture.complete(null);
        }else {
            channel.register(selector, SelectionKey.OP_CONNECT);
        }

        new Thread(this::startDo).start();

        return this;
    }

    private void startDo(){
        while (!colsed){
            try{
                if(selector.select(1000) < 1){
                    continue;
                }

                Iterator<SelectionKey> keyS = selector.selectedKeys().iterator();
                while (keyS.hasNext()) {
                    SelectionKey key = keyS.next();
                    keyS.remove();

                    try {
                        selectDo(key);
                    }catch (Throwable ex) {
                        if (key != null && key.channel() != null) {
                            key.channel().close();
                        }
                    }
                }

            }catch (Throwable ex){
                ex.printStackTrace();
            }
        }

        if(selector != null){
            try {
                selector.close();
            }catch (Throwable ex){
                ex.printStackTrace();
            }
        }
    }

    private void selectDo(SelectionKey key) throws IOException{
        if(key == null || key.isValid() == false){
            return;
        }

        if(key.isConnectable()){
            SocketChannel sc = (SocketChannel) key.channel();

            if (sc.finishConnect()) {
                sc.register(selector, SelectionKey.OP_READ);
                connectionFuture.complete(null);
            }else{
                this.colse();
            }
        }

        if(key.isReadable()){
            acceptor.read(key);
        }
    }

    @Override
    public void send(T message) {
        if (message == null) {
            return;
        }

        wait0();

        try {
            writeBuffer.write(message);
        } catch (IOException ex) {
            throw new FineException(ex);
        }
    }

    private void wait0() {
        if (connectionFuture != null) {
            try {
                connectionFuture.get(config.getConnectionTimeout(), TimeUnit.SECONDS);
                connectionFuture = null;
            } catch (Exception ex) {
                throw new FineException("Connection timeout!");
            }
        }
    }

    @Override
    public boolean isValid() {
        wait0();
        return channel.isOpen();
    }

    @Override
    public void colse() {
        try {
            colsed = true;
            channel.close();
        }catch (IOException ex){
            ex.printStackTrace();
        }
    }
    private boolean colsed;
}
