package com.github.linushp.zerorpc;

public class ZeroRpcTask implements Runnable {
    private final ZeroRpcHandler zeroRpcHandler;
    private final byte[] data;

    public ZeroRpcTask(ZeroRpcHandler zeroRpcHandler, byte[] data) {
        this.zeroRpcHandler = zeroRpcHandler;
        this.data = data;
    }

    @Override
    public void run() {
        this.zeroRpcHandler.handle(this.data);
    }
}
