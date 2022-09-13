package iit.inv.server;

import iit.inv.ns.NameServiceClient;
import iit.inv.sync.lock.client.DistributedLock;
import iit.inv.sync.lock.client.DistributedTx;
import iit.inv.sync.lock.client.DistributedTxCoordinator;
import iit.inv.sync.lock.client.DistributedTxParticipant;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.grpc.ServerBuilder.*;

public class InventoryServer {
    private static int serverPort;
    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";
    private static DistributedLock leaderLock;
    private static AtomicBoolean isLeader = new AtomicBoolean(false);
    private static byte[] leaderData; // = Leader's IP and Port
    private Map<Integer, Integer> inventory = new HashMap();
    private DistributedTx transaction;
    private InventoryServiceImpl inventoryService;

    public InventoryServer(String host, int port) throws InterruptedException, IOException, KeeperException {
        System.out.println("Server:Port " + host + ":" + port);
        this.serverPort = port;
        leaderLock = new DistributedLock("InventoryServerRWLock", buildServerData(host, port));

        inventoryService = new InventoryServiceImpl(this);
        transaction = new DistributedTxParticipant(inventoryService);
    }

    public DistributedTx getTransaction() {
        return transaction;
    }

    public int getInventoryStock(Integer itemId) {
        Integer value = inventory.get(itemId);
        return (value != null) ? value : 0;
    }

    public void setInventoryStock(Integer itemId, Integer itemStock) {
        inventory.put(itemId, itemStock);
        System.out.println("itemId itemStock: " + itemId + " " + itemStock);
    }

    public void orderInventoryStock(Integer itemIdForOrder, Integer itemStockForOrder) {
        inventory.replace(itemIdForOrder, itemStockForOrder);
        System.out.println("itemIdForOrder itemStockForOrder: " + itemIdForOrder + " " + itemStockForOrder);
    }

    public boolean isLeader() {
        return isLeader.get();
    }

    private static synchronized void setCurrentLeaderData(byte[] leaderData) {
        InventoryServer.leaderData = leaderData;
    }

    public static void main(String[] args) throws Exception {
        DistributedTx.setZooKeeperURL("localhost:2181");

        //serverPort = 11436;

        Random random = new Random();
        serverPort = Integer.parseInt(String.format("%03d", random.nextInt(10000)));

        DistributedLock.setZooKeeperURL("localhost:2181");

        InventoryServer inventoryServer = new InventoryServer("localhost", serverPort);
        inventoryServer.startServer();

    }

    public void startServer() {
        Server server = ServerBuilder
                .forPort(serverPort)
                .addService(inventoryService)
                /*.addService(new InventoryServiceImpl(this))*/
                .build();
        try {
            server.start();
            tryToBeLeader();

            NameServiceClient client = new NameServiceClient(NAME_SERVICE_ADDRESS);
            client.registerService("InventoryService", "127.0.0.1", serverPort, "tcp");

            System.out.println("InventoryServer Started and ready to accept requests on port " + serverPort);
            server.awaitTermination();
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    public static String buildServerData(String IP, int port) {
        StringBuilder builder = new StringBuilder();
        builder.append(IP).append(":").append(port);
        return builder.toString();
    }

    public synchronized String[] getCurrentLeaderData() {
        return new String(leaderData).split(":");
    }

    public List<String[]> getOthersData() throws KeeperException, InterruptedException {
        List<String[]> result = new ArrayList<>();
        List<byte[]> othersData = leaderLock.getOthersData();
        for (byte[] data : othersData) {
            String[] dataStrings = new String(data).split(":");
            result.add(dataStrings);
        }
        return result;
    }

    private  void tryToBeLeader() throws KeeperException, InterruptedException {
        Thread leaderCampaignThread = new Thread(new LeaderCampaignThread());
        leaderCampaignThread.start();
    }

    private void beTheLeader() {
        System.out.println("I got the leader lock. Now acting as primary");
        isLeader.set(true);
        transaction = new DistributedTxCoordinator(inventoryService);
    }


    class LeaderCampaignThread implements Runnable {
        private byte[] currentLeaderData = null;

        @Override
        public void run() {
            System.out.println("Starting the leader Campaign");
            try {
                boolean leader = leaderLock.tryAcquireLock();
                while (!leader) {
                    byte[] leaderData = leaderLock.getLockHolderData();
                    if (currentLeaderData != leaderData) {
                        currentLeaderData = leaderData;
                        setCurrentLeaderData(currentLeaderData);
                    }
                    Thread.sleep(10000);
                    leader = leaderLock.tryAcquireLock();
                }
                System.out.println("I got the leader lock. Now acting as primary");
                /* //Removed from Step 5
                isLeader.set(true);
                 */
                currentLeaderData = null;
                beTheLeader();
            } catch (Exception e) {
            }
        }
    }


}



/*
 * Commands:
 *
 * Clean Install: mvn clean install
 *
 * Open CMD/ PowerShell > Run:
 *
 * Run ZooKeeper Server:
 * > cd IdeaProjects\IIT-Inventory-Server\apache-zookeeper-3.6.2-bin
 * IF Windows > ./bin/zkServer.cmd
 * IF MAC > ./bin/zkServer.cmd start conf/zoo_sample.sh
 *
 * Run Inventory Server:
 * > cd IdeaProjects\IIT-Inventory-Server
 * Run Server: java -jar target/IIT-Inventory-Server-1.0-SNAPSHOT-jar-with-dependencies.jar
 *
 *
 *
 *
 * */