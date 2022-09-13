package iit.inv.server;

import iit.inv.grpc.generated.CheckInventoryItemStockResponse;
import iit.inv.grpc.generated.InventoryServiceGrpc;
import iit.inv.grpc.generated.SetInventoryItemStockRequest;
import iit.inv.grpc.generated.SetInventoryItemStockResponse;
import iit.inv.sync.lock.client.DistributedLock;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    /*
     *  Check inventory stock available
     */

    private InventoryServer server;
    public static final String ZOOKEEPER_URL = "127.0.0.1:2181";
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private ManagedChannel channel = null;
    InventoryServiceGrpc.InventoryServiceBlockingStub clientStub = null;

    public InventoryServiceImpl(InventoryServer server) {
        this.server = server;
    }

    @Override
    public void checkInventoryItemStock(iit.inv.grpc.generated.CheckInventoryItemStockRequest request, io.grpc.stub.StreamObserver<iit.inv.grpc.generated.CheckInventoryItemStockResponse> responseObserver) {
        DistributedLock.setZooKeeperURL(ZOOKEEPER_URL);

        String lockName = request.getLockName();
        System.out.println("Contesting to acquire lock " + lockName);
        try {
            DistributedLock lock = new DistributedLock(lockName, "dummyData");
            System.out.println("lock: " + lock);
            lock.acquireLock();
            System.out.println("I Got the lock at " + getCurrentTimeStamp());


            int inventoryId = request.getItemId();
            System.out.println("Request for inventory stock check received..");
            int inventoryStock = getInventoryStock(inventoryId);
            CheckInventoryItemStockResponse response = CheckInventoryItemStockResponse.newBuilder()
                    .setItemStock(inventoryStock)
                    .build();
            System.out.println("Responding, stock for inventory " + inventoryId + " is " + inventoryStock);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            accessSharedResource();


            lock.releaseLock();
            System.out.println("Releasing the lock " + getCurrentTimeStamp());
        } catch (IOException | KeeperException | InterruptedException e) {
            System.out.println("Error while creating Distributed Lock myLock :" + e.getMessage());
            e.printStackTrace();
        }


    }

    private static void accessSharedResource() throws InterruptedException {
        Random r = new Random();
        long sleepDuration = Math.abs(r.nextInt() % 20);
        System.out.println("Accessing critical section. Time remaining : " + sleepDuration + " seconds.");
        Thread.sleep(sleepDuration * 1000);
    }

    private static String getCurrentTimeStamp() {
        return timeFormat.format(new Date(System.currentTimeMillis()));
    }

    private int getInventoryStock(int inventoryId) {
        System.out.println("Checking stock for inventory " + inventoryId);
        //return new Random().nextInt() * 10000;
        return server.getInventoryStock(inventoryId);
    }

    /*
     * ~~~
     */

    @Override
    public void setInventoryItemStock(iit.inv.grpc.generated.SetInventoryItemStockRequest request, io.grpc.stub.StreamObserver<iit.inv.grpc.generated.SetInventoryItemStockResponse> responseObserver) {
        Integer itemId = request.getItemId();
        Integer value = request.getValue();
        boolean status = false;
        if (server.isLeader()) {
            // Act as primary
            try {
                System.out.println("Updating account balance as Primary");
                updateBalance(itemId, value);
                updateSecondaryServers(itemId, value);
                status = true;
            } catch (Exception e) {
                System.out.println("Error while updating the account balance" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Act As Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Updating account balance on secondary, on Primary's command");
                updateBalance(itemId, value);
            } else {
                SetInventoryItemStockResponse response = callPrimary(itemId, value);
                if (response.getStatus()) {
                    status = true;
                }
            }
        }
        SetInventoryItemStockResponse response = SetInventoryItemStockResponse
                .newBuilder()
                .setStatus(status)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private void updateBalance(Integer itemId, Integer value) {
        server.setInventoryStock(itemId, value);
        System.out.println("Inventory " + itemId + " updated to value " + value);
    }

    private SetInventoryItemStockResponse callServer(Integer itemId, Integer value, boolean isSentByPrimary, String IPAddress, int port) {
        System.out.println("Call Server " + IPAddress + ":" + port);
        channel = ManagedChannelBuilder.forAddress(IPAddress, port)
                .usePlaintext()
                .build();
        clientStub = InventoryServiceGrpc.newBlockingStub(channel);
        SetInventoryItemStockRequest request = SetInventoryItemStockRequest
                .newBuilder()
                .setItemId(itemId)
                .setValue(value)
                .setIsSentByPrimary(isSentByPrimary)
                .build();
        SetInventoryItemStockResponse response = clientStub.setInventoryItemStock(request);
        return response;
    }

    private SetInventoryItemStockResponse callPrimary(Integer itemId, Integer value) {
        System.out.println("Calling Primary server");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);
        return callServer(itemId, value, false, IPAddress, port);
    }

    private void updateSecondaryServers(Integer itemId, Integer value) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers");
        List<String[]> othersData = server.getOthersData();
        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);
            callServer(itemId, value, true, IPAddress, port);
        }
    }


}
