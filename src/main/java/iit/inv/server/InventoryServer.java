package iit.inv.server;

import iit.inv.ns.NameServiceClient;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.util.Random;

import static io.grpc.ServerBuilder.*;

public class InventoryServer {
    public static final String NAME_SERVICE_ADDRESS = "http://localhost:2379";

    public static void main(String[] args) throws Exception {
        //int serverPort = 11436;
        Random random = new Random();
        int serverPort = Integer.parseInt(String.format("%06d", random.nextInt(999999)));

        Server server = ServerBuilder
                .forPort(serverPort)
                .addService(new InventoryServiceImpl())
                .build();
        try {
            server.start();

            NameServiceClient client = new NameServiceClient(NAME_SERVICE_ADDRESS);
            client.registerService("InventoryService", "127.0.0.1", serverPort, "tcp");

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