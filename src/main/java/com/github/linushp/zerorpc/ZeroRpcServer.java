package com.github.linushp.zerorpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.ExecutorService;


/**
 * 单线程监听端口，阻塞式监听
 * 多线程处理，异步处理
 * <p>
 * 一个线程recv，多个线程execute
 */
public class ZeroRpcServer implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ZeroRpcServer.class);

    private final String address;
    private ZMQ.Socket socket;
    private final ExecutorService workers;
    private final ZeroRpcHandler zeroRpcHandler;


    /**
     * RPC服务端：构造函数
     *
     * @param address        监听的地址
     * @param zeroRpcHandler 不能为null
     * @param workers        不能为null
     */
    public ZeroRpcServer(String address, ZeroRpcHandler zeroRpcHandler, ExecutorService workers) {
        this.address = address;
        this.zeroRpcHandler = zeroRpcHandler;
        this.workers = workers;
        new Thread(this).start();
    }


    private void createSocket(ZContext context) {
        if (socket != null) {
            LOG.info("ZeroRpcServer close " + this.address);
            socket.close();
        }
        socket = context.createSocket(SocketType.REP);
        socket.setSendTimeOut(ZeroRpcConst.SERVER_SEND_TIME_OUT);
        socket.bind(this.address);
        LOG.info("ZeroRpcServer bind " + this.address);
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext()) {

            this.createSocket(context);

            while (!Thread.currentThread().isInterrupted()) {
                try {

                    byte[] req = socket.recv(0);

                    ZeroRpcTask task = new ZeroRpcTask(this.zeroRpcHandler, req);
                    workers.execute(task);

                    socket.send(ZeroRpcConst.ACK_BYTE, 0);
                } catch (Exception e) {
                    this.createSocket(context);
                    LOG.error("", e);
                }
            }
        } catch (Exception e) {
            LOG.error("", e);
        }
    }
}
