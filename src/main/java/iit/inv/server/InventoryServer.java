package iit.inv.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;

import static io.grpc.ServerBuilder.*;

public class InventoryServer {
    public static void main(String[] args) throws Exception {
        int serverPort = 11436;
        Server server = ServerBuilder
                .forPort(serverPort)
                .addService(new InventoryServiceImpl())
                .build();
        try {
            server.start();
            System.out.println("InventoryServer Started and ready to accept requests on port " + serverPort);
            server.awaitTermination();
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }
}



/*
 * Commands:
 *
 * Clean Install: mvn clean install
 *
 * Open CMD/ PowerShell > Run:
 * > cd IdeaProjects\IIT-Inventory-Server
 * Run Server: java -jar target/IIT-Inventory-Server-1.0-SNAPSHOT-jar-with-dependencies.jar
 *
 *
 * */