# zerorpc
基于zeromq和一致性hash算法负载均衡的高性能rpc框架

### zeromq介绍

https://zeromq.org/

### 特点
1. 可靠性：内部实现基于ZeroMQ的req/rep机制，每一个消息体都会有重试和确认机制
2. Server端和Client端的启动顺序没有要求，连接建立之前会将消息暂存在发送缓冲区。不会丢失消息
3. 高性能：ZeroMQ机制配合多连接实例，连接池。
4. 内置一致性hash算法：简单的api可以非常方便的创建有状态的服务应用。
5. 对ZeroMQ的最轻量化封装，保留了ZeroMQ的所有功能。

### 服务端使用

监听端口：单线程，阻塞式

处理逻辑：多线程处理，worker线程池

```java


class ServerDemo {
    
    private static void createServer(String address) {
        
        // 创建工作线程池
        ExecutorService serverWorkers = Executors.newFixedThreadPool(10);
        
        //监听地址和端口
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

* 单线程发送消息到服务端
* 一个Service可以是一个集群，所以一个ServiceName会有多个Address
* 一个Address可以建立多个连接，所以一个Address会有多个Client
* getClient函数使用一致性hash算法获取服务的address
* 同一个address会建立多个client实例，内部使用round robin算法负载均衡


```java

class ClientDemo {
    public static void main(String[] args){

        String address5 = "tcp://localhost:5555";
        String address6 = "tcp://localhost:6666";
        
        //注意：客户端数量越多反而会导致并发能力越弱
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

### 性能测试

服务器配置：i5 双核 8G
单台服务器：支持每秒十万级并发

