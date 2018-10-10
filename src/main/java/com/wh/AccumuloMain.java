package com.wh;

import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.admin.TableOperations;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.accumulo.minicluster.MiniAccumuloCluster;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.io.Text;

import java.io.File;
import java.util.Map;

public class AccumuloMain {
    private final static String accumulotemp = "/home/hwang/accumulotemp";
    private final static String accumulouser = "root";
    private final static String accumulopwd  = "root";
    private final static String accumulotable = "test";

    public static void main(String[] args) throws Exception {
        File accumuloFile = new File(accumulotemp);
        // the directory must be empty in order to initial accumulo
        if(accumuloFile.exists()){
            FileUtils.deleteDirectory(accumuloFile);
        }

        MiniAccumuloCluster accumulo =
                new MiniAccumuloCluster(accumuloFile, accumulopwd);
        accumulo.start();

        Instance instance =
                new ZooKeeperInstance(accumulo.getInstanceName(),
                        accumulo.getZooKeepers());
        // Connecting
        System.out.println("connecting... instance name: "
                + accumulo.getInstanceName()
                + ", zookeepers: "
                + accumulo.getZooKeepers());
        Connector conn =
                instance.getConnector(accumulouser,
                        new PasswordToken(accumulopwd));
        conn.securityOperations().changeUserAuthorizations("root",new Authorizations("public"));

        // Creating Table
        System.out.println("creating table...");
        TableOperations tableOperations = conn.tableOperations();

        if(tableOperations.exists(accumulotable)){
            tableOperations.delete(accumulotable);
        }

        tableOperations.create(accumulotable);

        // Writing Data
        // 1. creating mutaion objectr
        System.out.println("writing data...");
        Text    rowID   = new Text("john");
        Text    family  = new Text("info");
        Text    column  = new Text("age");
        Value   value   = new Value("20".getBytes());

        ColumnVisibility    colvis      = new ColumnVisibility();
        long                timestamp   = System.currentTimeMillis();

        Mutation            john        = new Mutation(rowID);
        john.put(family, column, colvis, timestamp, value);

        // 2. creating batchwriter object to write mutation to accumulo
        BatchWriterConfig config = new BatchWriterConfig();
        config.setMaxMemory(1000);
        BatchWriter writer = conn.createBatchWriter(accumulotable, config);
        writer.addMutation(john);
        writer.close();

        // scan data
        System.out.println("scan data...");
        Authorizations authorizations = new Authorizations("public");
        Scanner scanner = conn.createScanner(accumulotable, authorizations);
        scanner.setRange(new Range("john","john"));
        scanner.fetchColumnFamily(new Text("info"));

        for(Map.Entry<Key, Value> e : scanner){
            System.out.println(e.getKey() + ": " + e.getValue());
        }

        accumulo.stop();
    }
}
