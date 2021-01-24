# zerorpc
基于zeromq和一致性hash算法负载均衡的高性能rpc框架


### 服务端使用

```java


class ServerDemo {
    
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
    
    public static void main(String[] args){

        String address5 = "tcp://localhost:5555";
        String address6 = "tcp://localhost:6666";

        createServer(address5);
        createServer(address6);
    }

}

```


### 客户端使用

```java

class ClientDemo {
    public static void main(String[] args){

        String address5 = "tcp://localhost:5555";
        String address6 = "tcp://localhost:6666";
        
        ZeroRpcClientPool.createClient("TestService", address5, 10);
        ZeroRpcClientPool.createClient("TestService", address6, 10);


        for (int x = 0; x < 10; x++) {
            for (int uid = 0; uid < 10; uid++) {
                
                //对uid使用一致性hash算法获取TestService的客户端实例
                ZeroRpcClient client = ZeroRpcClientPool.getClient("TestService", "" + uid);
                System.out.println("uid " + uid + "  getAddress " + client.getAddress());
                client.sendMessage(("x " + x + " hello " + uid + " world ").getBytes(StandardCharsets.UTF_8));
            }
            
            Thread.sleep(1000 * 1);
        }  
    }
}


```