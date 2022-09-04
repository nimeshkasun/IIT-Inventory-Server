package iit.inv.server;

import iit.inv.grpc.generated.CheckInventoryItemStockResponse;
import iit.inv.grpc.generated.InventoryServiceGrpc;

import java.util.Random;

public class InventoryServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    /*
     *  Check inventory stock available
     */
    @Override
    public void checkInventoryItemStock(iit.inv.grpc.generated.CheckInventoryItemStockRequest request,
                                        io.grpc.stub.StreamObserver<iit.inv.grpc.generated.CheckInventoryItemStockResponse> responseObserver) {
        int inventoryId = request.getItemId();
        System.out.println("Request for inventory stock check received..");
        int inventoryStock = getInventoryStock(inventoryId);
        CheckInventoryItemStockResponse response = CheckInventoryItemStockResponse.newBuilder()
                .setItemStock(inventoryStock)
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
