package com.github.linushp.zerorpc.test;

import com.github.linushp.zerorpc.ZeroRpcClient;
import com.github.linushp.zerorpc.ZeroRpcClientPool;
import com.github.linushp.zerorpc.ZeroRpcHandler;
import com.github.linushp.zerorpc.ZeroRpcServer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ZeroRpcTest {


    private static void createServer(String address) {
        ExecutorService serverWorkers = Executors.newFixedThreadPool(10);
        new ZeroRpcServer(address, new ZeroRpcHandler() {
            @Override
            public void handle(byte[] data) {
                System.out.print(Thread.currentThread().getId());
                System.out.println(" " + address + " Server Receive:" + new String(data));
            }
        }, serverWorkers);
    }


    public ZeroRpcTest() {
    }

    public static void main(String[] args) throws InterruptedException {

        String address5 = "tcp://localhost:5555";
        String address6 = "tcp://localhost:6666";

        createServer(address5);
        createServer(address6);


        ZeroRpcClientPool.createClient("TestService", address5, 10);
        ZeroRpcClientPool.createClient("TestService", address6, 10);


        for (int x = 0; x < 10; x++) {
            for (int uid = 0; uid < 10; uid++) {
                ZeroRpcClient client = ZeroRpcClientPool.getClient("TestService", "" + uid);
                System.out.println("uid " + uid + "  getAddress " + client.getAddress());
                client.sendMessage(("x " + x + " hello " + uid + " world ").getBytes(StandardCharsets.UTF_8));
            }
            Thread.sleep(1000 * 1);
        }


    }
}
