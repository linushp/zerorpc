package com.github.linushp.zerorpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.LinkedBlockingQueue;


/**
 * 单线程发送消息到服务端
 * 多线程发送消息到此客户端
 */
public class ZeroRpcClient implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ZeroRpcClient.class);

    private final String address;
    private final String serviceName;
    private byte[] nextMessage;
    private final LinkedBlockingQueue<byte[]> sending = new LinkedBlockingQueue<>();

    public ZeroRpcClient(String serviceName, String address) {
        this.serviceName = serviceName;
        this.address = address;
        new Thread(this).start();
    }

    public void sendMessage(byte[] message) {
        this.sending.add(message);

        int sendingSize = this.sending.size();
        if (sendingSize > 10000) {
            LOG.warn("sending queue is over 10000 , now size is " + sendingSize + " , serviceName = " + this.serviceName);
        }
    }

    private ZMQ.Socket connect(ZContext context) {
        LOG.info("connecting to " + this.address + " , serviceName is " + this.serviceName);
        ZMQ.Socket socket = context.createSocket(SocketType.REQ);
        socket.connect(this.address);
        socket.setReceiveTimeOut(ZeroRpcConst.CLIENT_RECEIVE_TIME_OUT);
        socket.setSendTimeOut(ZeroRpcConst.CLIENT_SEND_TIME_OUT);
        LOG.info("connected to " + this.address);
        return socket;
    }

    private byte[] getNextMessage() throws InterruptedException {
        if (this.nextMessage == null) {
            this.nextMessage = sending.take();
        }
        return this.nextMessage;
    }

    @Override
    public void run() {
        try (ZContext context = new ZContext()) {

            ZMQ.Socket socket = connect(context);

            while (!Thread.currentThread().isInterrupted()) {

                byte[] task = getNextMessage();

                socket.send(task, 0);

                byte[] ack = socket.recv(0);

                if (!ZeroRpcConst.isAck(ack)) {
                    socket.close();
                    socket = connect(context);
                } else {
                    this.nextMessage = null;
                }
            }

        } catch (Exception e) {
            LOG.error("", e);
        }
    }

    public String getAddress() {
        return address;
    }

    public String getServiceName() {
        return serviceName;
    }
}
