import ds.tutorial.communication.grpc.generated.CheckInventoryStockResponse;
import ds.tutorial.communication.grpc.generated.InventoryServiceGrpc;

import java.util.Random;

public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    /*
    *  Check inventory stock available
    */
    @Override
    public void checkInventoryStock(ds.tutorial.communication.grpc.generated.CheckInventoryStockRequest request,
                             io.grpc.stub.StreamObserver<ds.tutorial.communication.grpc.generated.CheckInventoryStockResponse> responseObserver) {
        int inventoryId = request.getInventoryId();
        System.out.println("Request received..");
        int inventoryStock = getInventoryStock(inventoryId);
        CheckInventoryStockResponse response = CheckInventoryStockResponse.newBuilder()
                .setInventory(inventoryStock)
                .build();
        System.out.println("Responding, stock for inventory " + inventoryId + " is " +
                inventoryStock);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private int getInventoryStock(int inventoryId) {
        System.out.println("Checking stock for inventory " + inventoryId);
        return new Random().nextInt() * 10000;
    }

    /*
     * ~~~
     */



}
