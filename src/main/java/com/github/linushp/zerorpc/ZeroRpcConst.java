package com.github.linushp.zerorpc;

import org.zeromq.ZMQ;

public class ZeroRpcConst {

    public static int SERVER_SEND_TIME_OUT = 2000;

    public static int CLIENT_SEND_TIME_OUT = 2000;

    public static int CLIENT_RECEIVE_TIME_OUT = 2000;

    public static byte[] ACK_BYTE = "ack".getBytes(ZMQ.CHARSET);

    public static boolean isAck(byte[] ack) {
        if (ack != null && ack.length == ACK_BYTE.length) {
            for (int i = 0; i < ACK_BYTE.length; i++) {
                if (ack[i] != ACK_BYTE[i]) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

}
