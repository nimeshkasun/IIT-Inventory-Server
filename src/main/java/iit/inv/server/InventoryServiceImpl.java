package iit.inv.server;

import iit.inv.grpc.generated.CheckInventoryItemStockResponse;
import iit.inv.grpc.generated.InventoryServiceGrpc;
import iit.inv.sync.lock.client.DistributedLock;
import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    /*
     *  Check inventory stock available
     */

    public static final String ZOOKEEPER_URL = "127.0.0.1:2181";
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    @Override
    public void checkInventoryItemStock(iit.inv.grpc.generated.CheckInventoryItemStockRequest request, io.grpc.stub.StreamObserver<iit.inv.grpc.generated.CheckInventoryItemStockResponse> responseObserver) {
        DistributedLock.setZooKeeperURL(ZOOKEEPER_URL);

        String lockName = request.getLockName();
        System.out.println("Contesting to acquire lock " + lockName);
        try {
            DistributedLock lock = new DistributedLock(lockName);
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
        return new Random().nextInt() * 10000;
    }

    /*
     * ~~~
     */


}
