package com.github.linushp.zerorpc;

import com.github.linushp.zerorpc.consistenthash.ConsistentHashNode;
import com.github.linushp.zerorpc.consistenthash.ConsistentHashRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端连接池
 * 单线程发送消息到服务端
 * 多线程发送消息到此客户端
 * 一个Service可以是一个集群，所以一个ServiceName会有多个Address
 * 一个Address可以建立多个连接，所以一个Address会有多个Client
 */
public class ZeroRpcClientPool {

    private static final Logger LOG = LoggerFactory.getLogger(ZeroRpcClientPool.class);

    //<ServiceName,<Address,List<Client>>>
    private static final Map<String, Map<String, List<ZeroRpcClient>>> allClients = new ConcurrentHashMap<>();

    //<ServiceName,<Address,Index>>
    private static final Map<String, Map<String, Integer>> lastClientIndexes = new ConcurrentHashMap<>();

    //<ServiceName,ConsistentHashRouter>
    private static final Map<String, ConsistentHashRouter<ConsistentHashNode>> consistentHashRouterMap = new ConcurrentHashMap<>();

    /**
     * 创建一个Client
     *
     * @param serviceName 服务名
     * @param address     服务地址，如：tcp://localhost:5555
     * @param clientCount 每个address创建多少个client连接
     */
    public static void createClient(String serviceName, String address, int clientCount) {
        Map<String, List<ZeroRpcClient>> serviceMap = getClientMapByServiceName(serviceName);
        List<ZeroRpcClient> clients = getClientListByAddress(serviceMap, address);
        for (int i = 0; i < clientCount; i++) {
            clients.add(new ZeroRpcClient(serviceName, address));
        }

        buildConsistentHash();
    }

    /**
     * 创建一致性hash关系
     */
    private static void buildConsistentHash() {
        Set<Map.Entry<String, Map<String, List<ZeroRpcClient>>>> allClientsEntrySets = allClients.entrySet();
        for (Map.Entry<String, Map<String, List<ZeroRpcClient>>> entry : allClientsEntrySets) {
            String serviceName = entry.getKey();
            Map<String, List<ZeroRpcClient>> addressClients = entry.getValue();
            Set<String> addressList = addressClients.keySet();
            List<ConsistentHashNode> consistentHashNodes = toConsistentHashNodes(addressList);
            ConsistentHashRouter<ConsistentHashNode> consistentHashRouter = new ConsistentHashRouter<>(consistentHashNodes, consistentHashNodes.size() * 10);//10 virtual node
            consistentHashRouterMap.put(serviceName, consistentHashRouter);
        }
    }


    private static List<ConsistentHashNode> toConsistentHashNodes(Set<String> addressList) {
        List<ConsistentHashNode> nodeList = new ArrayList<>();
        for (String address : addressList) {
            nodeList.add(new ConsistentHashNode(address));
        }
        return nodeList;
    }


    private static Map<String, List<ZeroRpcClient>> getClientMapByServiceName(String serviceName) {
        return allClients.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
    }

    private static Map<String, Integer> getIndexMapByServiceName(String serviceName) {
        return lastClientIndexes.computeIfAbsent(serviceName, k -> new ConcurrentHashMap<>());
    }


    private static List<ZeroRpcClient> getClientListByAddress(Map<String, List<ZeroRpcClient>> serviceMap, String address) {
        return serviceMap.computeIfAbsent(address, k -> new ArrayList<>());
    }

    private static Integer getIndexByAddress(Map<String, Integer> serviceMap, String address) {
        return serviceMap.computeIfAbsent(address, k -> 0);
    }


    /**
     * 随机获取一个address
     * 然后再使用round robin找到其中一个Client
     *
     * @param serviceName 服务名
     * @return 获取RPCClient对象
     */
    public static ZeroRpcClient getClient(String serviceName) {
        return getClient(serviceName, "" + Math.random());
    }

    /**
     * 通过一致性hash算法，算出address
     * 然后再使用round robin找到其中一个Client
     *
     * @param serviceName 服务名
     * @param objectKey   用来做一致性hash的对象key
     * @return ZeroRpcClient实例
     */
    public static ZeroRpcClient getClient(String serviceName, Object objectKey) {
        ConsistentHashRouter<ConsistentHashNode> consistentHashRouter = consistentHashRouterMap.get(serviceName);
        if (consistentHashRouter == null) {
            LOG.error("consistentHashRouter is null of " + serviceName);
            return null;
        }
        ConsistentHashNode routeNode = consistentHashRouter.routeNode(objectKey.toString());
        if (routeNode == null) {
            LOG.error("routeNode is null of " + serviceName + " , " + objectKey);
            return null;
        }

        String address = routeNode.getKey();
        return getClientByAddress(serviceName, address);
    }


    /**
     * 工具函数：
     * 使用一致性hash算法 将Object keys 分组
     * 方便批量处理大量数据，分组后可以批量发送
     *
     * @param serviceName   服务名
     * @param objectKeyList 用来做一致性hash的对象key
     * @return 返回的map的结构是这样的 <address,List<ObjectKey>>
     */
    public static <T> Map<String, List<T>>  groupAddressOfKeys(String serviceName, List<T> objectKeyList) {
        ConsistentHashRouter<ConsistentHashNode> consistentHashRouter = consistentHashRouterMap.get(serviceName);
        if (consistentHashRouter == null) {
            LOG.error("consistentHashRouter is null of " + serviceName);
            return new HashMap<>();
        }

        Map<String, List<T>> map = new HashMap<>();

        for (T key : objectKeyList) {
            String keyString = key.toString();
            ConsistentHashNode routeNode = consistentHashRouter.routeNode(keyString);
            if (routeNode != null) {
                String address = routeNode.getKey();
                List<T> keyArr = map.computeIfAbsent(address, k -> new ArrayList<>());
                keyArr.add(key);
            }
        }

        return map;
    }


    public static ZeroRpcClient getClientByAddress(String serviceName, String address) {
        Map<String, List<ZeroRpcClient>> serviceClients = allClients.get(serviceName);
        if (serviceClients == null) {
            LOG.error("serviceClients is null of " + serviceName);
            return null;
        }

        List<ZeroRpcClient> addressClients = serviceClients.get(address);
        if (addressClients == null) {
            LOG.error("addressClients is null of " + serviceName + " , " + address);
            return null;
        }

        Map<String, Integer> serviceClientIndexes = getIndexMapByServiceName(serviceName);
        Integer index = getIndexByAddress(serviceClientIndexes, address);
        index = (index + 1) % addressClients.size();
        ZeroRpcClient client = addressClients.get(index);
        serviceClientIndexes.put(address, index);
        return client;
    }


    /**
     * 获取某个服务所有的地址
     * @param serviceName 服务名
     * @return 地址列表
     */
    public static List<String> getAllAddressListByServiceName(String serviceName){
        Map<String, List<ZeroRpcClient>> clients = allClients.get(serviceName);
        return new ArrayList<>(clients.keySet());
    }
}
