package iit.inv.server;

import iit.inv.grpc.generated.*;
import iit.inv.sync.lock.client.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import javafx.util.Pair;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase implements DistributedTxListener {

    /*
     *  Check inventory stock available
     */

    private InventoryServer server;
    public static final String ZOOKEEPER_URL = "127.0.0.1:2181";
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private ManagedChannel channel = null;
    InventoryServiceGrpc.InventoryServiceBlockingStub clientStub = null;
    private Pair<Integer, Integer> tempDataHolder;
    private boolean transactionStatus = false;

    private String updateType = "NEW";

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

    private void startDistributedTx(Integer itemID, Integer value) {
        try {
            server.getTransaction().start(itemID.toString(), String.valueOf(UUID.randomUUID()));
            tempDataHolder = new Pair<>(itemID, value);
        } catch (IOException e) {
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

        updateType = "NEW";

        /* // Replced by Step 5
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
        responseObserver.onCompleted();*/


        Integer itemId = request.getItemId();
        Integer value = request.getValue();
        if (server.isLeader()){
            // Act as primary
            try {
                System.out.println("Updating account balance as Primary");
                startDistributedTx(itemId, value);
                updateSecondaryServers(itemId, value);
                System.out.println("going to perform");
                if (value > 0){
                    ((DistributedTxCoordinator)server.getTransaction()).perform();
                } else {
                    ((DistributedTxCoordinator)server.getTransaction()).sendGlobalAbort();
                }
            } catch (Exception e) {
                System.out.println("Error while updating the account balance" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Act As Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Updating account balance on secondary, on Primary's command");
                startDistributedTx(itemId, value);
                if (value != 0.0d) {
                    ((DistributedTxParticipant)server.getTransaction()).voteCommit();
                } else {
                    ((DistributedTxParticipant)server.getTransaction()).voteAbort();
                }
            } else {
                SetInventoryItemStockResponse response = callPrimary(itemId, value);
                if (response.getStatus() == true) {
                    transactionStatus = true;
                }else{
                    transactionStatus = false;
                }
            }
        }
        SetInventoryItemStockResponse response = SetInventoryItemStockResponse
                .newBuilder()
                .setStatus(transactionStatus)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    //private void updateBalance(Integer itemId, Integer value) {
    private void updateInventory() {
        /* // Replced by Step 5
        server.setInventoryStock(itemId, value);
        System.out.println("Inventory " + itemId + " updated to value " + value);
        */


        if (tempDataHolder != null) {
            Integer itemId = tempDataHolder.getKey();
            Integer value = tempDataHolder.getValue();
            Integer currentStock = server.getInventoryStock(itemId);

            if(updateType == "NEW") {
                Integer totalStock = currentStock + value;
                server.setInventoryStock(itemId, totalStock);
                transactionStatus = true;
            }else {
                transactionStatus = false;
            }

            if(updateType == "ORDER" && value <= currentStock && currentStock != 0) {
                Integer remainingStock = currentStock - value;
                server.orderInventoryStock(itemId, remainingStock);
                transactionStatus = true;
            }else if(updateType == "ORDER") {
                transactionStatus = false;
            }

            System.out.println("Inventory " + itemId + " updated to value " + value + " committed");
            tempDataHolder = null;
        }
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

    @Override
    public void onGlobalCommit() {
        updateInventory();
    }

    @Override
    public void onGlobalAbort() {
        tempDataHolder = null;
        System.out.println("Transaction Aborted by the Coordinator");
    }


    /*
     * ~~~
     */

    @Override
    public void orderInventoryItem(iit.inv.grpc.generated.OrderInventoryItemRequest request, io.grpc.stub.StreamObserver<iit.inv.grpc.generated.OrderInventoryItemResponse> responseObserver) {
        updateType = "ORDER";

        Integer itemIdForOrder = request.getItemIdForOrder();
        Integer valueForOrder = request.getValueForOrder();
        if (server.isLeader()){
            // Act as primary
            try {
                System.out.println("Updating account balance as Primary");
                startDistributedTx(itemIdForOrder, valueForOrder);
                updateSecondaryServersForOrder(itemIdForOrder, valueForOrder);
                System.out.println("going to perform");
                if (valueForOrder > 0){
                    ((DistributedTxCoordinator)server.getTransaction()).perform();
                } else {
                    ((DistributedTxCoordinator)server.getTransaction()).sendGlobalAbort();
                }
            } catch (Exception e) {
                System.out.println("Error while updating the account balance" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            // Act As Secondary
            if (request.getIsSentByPrimary()) {
                System.out.println("Updating account balance on secondary, on Primary's command");
                startDistributedTx(itemIdForOrder, valueForOrder);
                if (valueForOrder != 0.0d) {
                    ((DistributedTxParticipant)server.getTransaction()).voteCommit();
                } else {
                    ((DistributedTxParticipant)server.getTransaction()).voteAbort();
                }
            } else {
                OrderInventoryItemResponse responseForOrder = callPrimaryForOrder(itemIdForOrder, valueForOrder);
                if (responseForOrder.getStatus() == true) {
                    transactionStatus = true;
                }else {
                    transactionStatus = false;
                }
            }
        }
        OrderInventoryItemResponse responseForOrder = OrderInventoryItemResponse
                .newBuilder()
                .setItemIdForOrder(itemIdForOrder)
                .setValueForOrder(valueForOrder)
                .setStatus(transactionStatus)
                .build();
        responseObserver.onNext(responseForOrder);
        responseObserver.onCompleted();
    }

    private OrderInventoryItemResponse callServerForOrder(Integer itemIdForOrder, Integer valueForOrder, boolean isSentByPrimary, String IPAddress, int port) {
        System.out.println("Call Server " + IPAddress + ":" + port);
        channel = ManagedChannelBuilder.forAddress(IPAddress, port)
                .usePlaintext()
                .build();
        clientStub = InventoryServiceGrpc.newBlockingStub(channel);
        OrderInventoryItemRequest request = OrderInventoryItemRequest
                .newBuilder()
                .setItemIdForOrder(itemIdForOrder)
                .setValueForOrder(valueForOrder)
                .setIsSentByPrimary(isSentByPrimary)
                .build();
        OrderInventoryItemResponse response = clientStub.orderInventoryItem(request);
        return response;
    }

    private OrderInventoryItemResponse callPrimaryForOrder(Integer itemIdForOrder, Integer valueForOrder) {
        System.out.println("Calling Primary server");
        String[] currentLeaderData = server.getCurrentLeaderData();
        String IPAddress = currentLeaderData[0];
        int port = Integer.parseInt(currentLeaderData[1]);
        return callServerForOrder(itemIdForOrder, valueForOrder, false, IPAddress, port);
    }

    private void updateSecondaryServersForOrder(Integer itemIdForOrder, Integer valueForOrder) throws KeeperException, InterruptedException {
        System.out.println("Updating secondary servers");
        List<String[]> othersData = server.getOthersData();
        for (String[] data : othersData) {
            String IPAddress = data[0];
            int port = Integer.parseInt(data[1]);
            callServer(itemIdForOrder, valueForOrder, true, IPAddress, port);
        }
    }



}
