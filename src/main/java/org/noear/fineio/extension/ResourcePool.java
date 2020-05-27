package org.noear.fineio.extension;

import java.util.concurrent.LinkedBlockingQueue;

public class ResourcePool<R> {
    private final LinkedBlockingQueue<R> queue = new LinkedBlockingQueue<>();
    private final ThreadLocal<R> threadLocal = new ThreadLocal<>();

    private int coreSize;
    private ResourceFactory<R> factory;

    public ResourcePool(int coreSize, ResourceFactory<R> factory) {
        this.coreSize = coreSize;
        this.factory = factory;
    }

    public R apply() {
        try {
            return apply0();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void free() {
        free0();
    }

    private R open(R res) {
        return factory.open(res);
    }

    private R close(R res) {
        return factory.close(res);
    }

    /**
     * 获取资源
     */
    private R apply0() throws InterruptedException {
        R r = threadLocal.get();

        if (r == null) {
            if (queue.isEmpty() == false) {
                r = open(queue.take());
            }

            if(r == null){
                if(null != (r = create0())) {
                    queue.offer(r);
                }
            }

            threadLocal.set(r);
        }

        return r;
    }

    private R create0(){
        try{
            return factory.create();
        }catch (Throwable ex){
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * 释放资源
     * */
    private void free0() {
        R r = threadLocal.get();

        if (r != null) {
            threadLocal.remove();

            if(null != (r = close(r))) {
                queue.offer(r);
            }
        }
    }
}
