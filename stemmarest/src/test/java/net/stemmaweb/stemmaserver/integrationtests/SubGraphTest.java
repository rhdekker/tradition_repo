package net.stemmaweb.stemmaserver.integrationtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import net.stemmaweb.model.ReadingModel;
import net.stemmaweb.model.RelationshipModel;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.WitnessModel;
import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.rest.Root;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.parser.GraphMLParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import net.stemmaweb.stemmaserver.Util;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.*;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.test.framework.JerseyTest;
import static java.lang.Long.parseLong;
import java.util.concurrent.ThreadLocalRandom;
import net.stemmaweb.model.GraphModel;
import org.apache.log4j.Logger;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * Contains all tests for the api calls related to the tradition.
 *
 * @author PSE FS 2015 Team2
 */
public class SubGraphTest {
    private String tradId;
    private long startRank;
    private long endRank;

    private GraphDatabaseService db;

    final static Logger logger = Logger.getLogger(SubGraphTest.class);
    
    /*
     * JerseyTest is the test environment to Test api calls it provides a
     * grizzly http service
     */
    private JerseyTest jerseyTest;
    private GraphMLParser importResource;

    @Before
    public void setUp() throws Exception {

        db = new GraphDatabaseServiceProvider(new TestGraphDatabaseFactory()
                .newImpermanentDatabase())
                .getDatabase();

        /*
         * Populate the test database with the root node and a user with id 1
         */
        DatabaseService.createRootNode(db);
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "1");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        // Create a JerseyTestServer for the necessary REST API calls
        Root webResource = new Root();
        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(webResource)
                .create();
        jerseyTest.setUp();

        /**
         * create a tradition inside the test DB
         */
        try {
            //String fileName = "src/TestFiles/testTradition.xml";
            String fileName = "src/TestFiles/Sapientia.xml";
            tradId = createTraditionFromFile("Tradition", "LR", "1", fileName, "graphml");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }
    }

    private String createTraditionFromFile(String tName, String tDir, String userId, String fName,
                                           String fType) throws FileNotFoundException {

        FormDataMultiPart form = new FormDataMultiPart();
        if (fType != null) form.field("filetype", fType);
        if (tName != null) form.field("name", tName);
        if (tDir != null) form.field("direction", tDir);
        if (userId != null) form.field("userId", userId);
        if (fName != null) {
            FormDataBodyPart fdp = new FormDataBodyPart("file",
                    new FileInputStream(fName),
                    MediaType.APPLICATION_OCTET_STREAM_TYPE);
            form.bodyPart(fdp);
        }
        ClientResponse jerseyResult = jerseyTest.resource()
                .path("/tradition")
                .type(MediaType.MULTIPART_FORM_DATA_TYPE)
                .put(ClientResponse.class, form);
        assertEquals(Response.Status.CREATED.getStatusCode(), jerseyResult.getStatus());
        String tradId = Util.getValueFromJson(jerseyResult, "tradId");
        assert(tradId.length() != 0);
        return  tradId;
    }

    /*
    ** Test if subgraph request returns edges that span the requested range
    */
    @Test
    public void getSubGraphEdgeTest() {

        startRank = 10;
        endRank = 20;
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/subgraph/" + startRank + "/" + endRank)
                .get(ClientResponse.class);

        try {
            // Make sure we get a 200 statuscode
            assertEquals(Status.OK.getStatusCode(), response.getStatus());

            // parse the returned sub graph
            Map<String, Long> hasRank = new HashMap();
            GraphModel subgraph = response.getEntity(new GenericType<GraphModel>(){});

            // map Ids of returned readings to their ranks
            subgraph.getReadings().stream().forEach((reading) -> {
                hasRank.put(reading.getId(), reading.getRank());
            });

            subgraph.getRelationships().stream().forEach((relationship) -> {
                // there is always at least one witness
                assertTrue(relationship.getWitness().size() > 0);

                String sourceId = relationship.getSource();
                String targetId = relationship.getTarget();
                Long sourceRank = hasRank.get(sourceId);
                Long targetRank = hasRank.get(targetId);
                if (sourceRank > targetRank) {
                    Long temp = sourceRank;
                    sourceRank = targetRank;
                    targetRank = temp;
                }
                
                // lower rank in relationship should be 
                // before the end of the sub graph range
                assertTrue(sourceRank <= endRank);
                // higher rank in relationship should be 
                // after the start of the sub graph range
                assertTrue(targetRank >= startRank);
            });
        } catch (Exception e){
            e.printStackTrace();
        }        
    }
    
    /*
    ** Test if subgraph request returns each reading only once
    */
    @Test
    public void getSubGraphUniqueReadingsTest() {

        startRank = 10;
        endRank = 20;
        ClientResponse response = jerseyTest.resource()
                .path("/tradition/" + tradId + "/subgraph/" + startRank + "/" + endRank)
                .get(ClientResponse.class);

        assertEquals(Status.OK.getStatusCode(), response.getStatus());
        
        List<String> ids = new ArrayList<String>();
        GraphModel subgraph = response.getEntity(new GenericType<GraphModel>(){});
        for (ReadingModel r : subgraph.getReadings()) {
            assertEquals(ids.contains(r.getId()), false);
            ids.add(r.getId());
        }
    }
    
    /**
     * Test if returned readings have ranks within range
     */
    @Test
    public void testSubgraphQueryPerformance() {
        /* Preconditon
         * The user with id 1 has tradition
         */
        int maxRank = 2117; // maximum rank in Sapientia tradition
        int maxRange = 100;
        
        int iteration = 0;
        int warmupIterations = 5;
        int testIterations = 100;
        while (iteration < warmupIterations) {
            int startRange = (int)(Math.random() * ((maxRank - maxRange) + 1));
            int endRange = (startRange + 1) + (int)(Math.random() * (maxRange + 1));
            doSubgraphQuery(tradId, startRange, endRange);
            iteration++;
        }

        iteration = 0;
        long startTime = System.currentTimeMillis();
        long prevTime = startTime;
        
        while (iteration < testIterations) {
            int startRange = (int)(Math.random() * ((maxRank - maxRange) + 1));
            int endRange = (startRange + 1) + (int)(Math.random() * (maxRange + 1));
            doSubgraphQuery(tradId, startRange, endRange);
            long subTime = System.currentTimeMillis();
            System.out.println("Iteration " + iteration + " with range " + startRange + "-" + endRange + " took: "+(subTime-prevTime)+ " ms");
            iteration++;
            prevTime = subTime;
        }
        
        long finishTime = System.currentTimeMillis();

        System.out.println("Total test took: "+(finishTime-startTime)+ " ms, average per request is " + (finishTime-startTime)/testIterations + "ms");
        assertTrue(finishTime-startTime > 0);
        
    }
    
    public void doSubgraphQuery(String tradId, int startRank, int endRank) {
        Result result;
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (m)-[r:SEQUENCE]-(n) where m.tradition_id = \"" + tradId + "\" and m.rank <= " + endRank + " and n.rank >= " + startRank + " and m.rank < n.rank return m, r, n");
            
            tx.success();
        }
    }
    /*
     * Test if returned edges are connected to nodes with a rank range
     */
    /*
    @Test
    public void testEdgeRange() {
        Node startNode = DatabaseService.getStartNode(tradId, db);
        ArrayList<Relationship> relationships = new ArrayList<>();

        Evaluator e = path -> {
            Integer rank = Integer.parseInt(path.endNode().getProperty("rank").toString());
            if (rank > endRank)
                return Evaluation.EXCLUDE_AND_PRUNE;
            if (rank < startRank)
                return Evaluation.EXCLUDE_AND_CONTINUE;
            return Evaluation.INCLUDE_AND_CONTINUE;
        };
        
        try (Transaction tx = db.beginTx()) {
            db.traversalDescription().depthFirst()
                    .relationships(ERelations.SEQUENCE, Direction.OUTGOING)
                    .evaluator(e).uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(startNode).relationships().forEach(relationships::add);
            tx.success();
        }
        
    }
    */

    // match (n)-[r:SEQUENCE]-(m) where n.tradition_id = " + tradId + " and n.rank >= " + startRank + " and m.rank <= " + endRank + " return n.rank, m.rank, r"
    // "match (n) where n.tradition_id = " + tradId + " and n.rank > " + startRank + " and n.rank < " + endRank + " return n"
    
    /**
     * Shut down the jersey server
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception {
        jerseyTest.tearDown();
        db.shutdown();
    }
}
