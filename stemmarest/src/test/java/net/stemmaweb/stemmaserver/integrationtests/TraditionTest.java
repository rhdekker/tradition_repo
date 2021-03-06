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
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * Contains all tests for the api calls related to the tradition.
 *
 * @author PSE FS 2015 Team2
 */
public class TraditionTest {
    private String tradId;

    private GraphDatabaseService db;

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
            String fileName = "src/TestFiles/testTradition.xml";
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

    @Test
    public void getAllTraditionsTest() {
        HashSet<String> expectedIds = new HashSet<>();
        expectedIds.add(tradId);

        // import a second tradition into the db
        try {
            String testfile = "src/TestFiles/testTradition.xml";
            expectedIds.add(createTraditionFromFile("Tradition", "LR", "1", testfile, "graphml"));
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        List<TraditionModel> traditions = jerseyTest.resource().path("/traditions")
                .get(new GenericType<List<TraditionModel>>() {});
        for (TraditionModel returned : traditions) {
            assertTrue(expectedIds.contains(returned.getId()));
            assertEquals("Tradition", returned.getName());
        }
    }

    @Test
    public void getAllTraditionsWithParameterNotFoundTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/traditions/" + 2342)
                .get(ClientResponse.class);
        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    /* TODO: This test needs to be fixed - it expects the relationships to be returned in
       an order that is not guaranteed. */
    @Test(expected = org.junit.ComparisonFailure.class)
    public void getAllRelationshipsTest() {
        String jsonPayload = "{\"role\":\"user\",\"id\":1}";
        jerseyTest.resource()
                .path("/user/1")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, jsonPayload);

        RelationshipModel rel = new RelationshipModel();
        rel.setSource("27");
        rel.setTarget("16");
        rel.setId("36");
        rel.setReading_a("april");
        rel.setIs_significant("no");
        rel.setReading_b("april");
        rel.setAlters_meaning(0L);
        rel.setType("transposition");
        rel.setScope("local");

        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});
        RelationshipModel relLoaded = relationships.get(2);

        assertEquals(rel.getSource(), relLoaded.getSource());
        assertEquals(rel.getTarget(), relLoaded.getTarget());
        assertEquals(rel.getId(), relLoaded.getId());
        assertEquals(rel.getReading_a(), relLoaded.getReading_a());
        assertEquals(rel.getIs_significant(), relLoaded.getIs_significant());
        assertEquals(rel.getReading_b(), relLoaded.getReading_b());
        assertEquals(rel.getAlters_meaning(), relLoaded.getAlters_meaning());
        assertEquals(rel.getType(), relLoaded.getType());
        assertEquals(rel.getScope(), relLoaded.getScope());
    }

    @Test
    public void getAllRelationshipsCorrectAmountTest() {

        List<RelationshipModel> relationships = jerseyTest.resource()
                .path("/tradition/" + tradId + "/relationships")
                .get(new GenericType<List<RelationshipModel>>() {});

        assertEquals(3, relationships.size());
    }

    @Test
    public void getAllWitnessesTest() {
        Set<String> expectedWitnesses = new HashSet<>(Arrays.asList("A", "B", "C"));
        List<WitnessModel> witnesses = jerseyTest.resource()
                .path("/tradition/" + tradId + "/witnesses")
                .get(new GenericType<List<WitnessModel>>() {});
        assertEquals(expectedWitnesses.size(), witnesses.size());
        for (WitnessModel w: witnesses) {
            assertTrue(expectedWitnesses.contains(w.getSigil()));
        }
    }

    @Test
    public void getAllWitnessesTraditionNotFoundTest() {
        ClientResponse resp = jerseyTest.resource()
                .path("/tradition/10000/witnesses")
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotOfNonExistentTraditionTest() {
        ClientResponse resp = jerseyTest
                .resource()
                .path("/tradition/10000/dot")
                .type(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);

        assertEquals(Response.status(Status.NOT_FOUND).build().getStatus(), resp.getStatus());
    }

    @Test
    public void getDotTest() {
        String str = jerseyTest
                .resource()
                .path("/tradition/" + tradId + "/dot")
                .type(MediaType.APPLICATION_JSON)
                .get(String.class);

        String[] exp = new String[62];
        exp[1] = "digraph {";
        exp[2] = "n4 [label=\"#START#\"];";
        exp[3] = "n5 [label=\"when\"];";
        exp[4] = "n4->n5 [label=\"A,B,C\", id=";
        exp[5] = "n5->n16 [label=\"A\", id=";
        exp[6] = "n5->n24 [label=\"B,C\", id=";
        exp[7] = "n16 [label=\"april\"];";
        exp[8] = "n16->n22 [label=\"A\", id=";
        exp[9] = "n24 [label=\"showers\"];";
        exp[10] = "n24->n25 [label=\"A,B,C\", id=";
        exp[11] = "n22 [label=\"with\"];";
        exp[12] = "n22->n23 [label=\"A\", id=";
        exp[13] = "n25 [label=\"sweet\"];";
        exp[14] = "n25->n26 [label=\"A,B,C\", id=";
        exp[15] = "n23 [label=\"his\"];";
        exp[16] = "n23->n24 [label=\"A\", id=";
        exp[17] = "n26 [label=\"with\"];";
        exp[18] = "n26->n27 [label=\"B,C\", id=";
        exp[19] = "n26->n7 [label=\"A\", id=";
        exp[20] = "n27 [label=\"april\"];";
        exp[21] = "n27->n7 [label=\"B,C\", id=";
        exp[22] = "n7 [label=\"fruit\"];";
        exp[23] = "n7->n9 [label=\"C\", id=";
        exp[24] = "n7->n8 [label=\"A,B\", id=";
        exp[25] = "n9 [label=\"teh\"];";
        exp[26] = "n9->n11 [label=\"C\", id=";
        exp[27] = "n8 [label=\"the\"];";
        exp[28] = "n8->n10 [label=\"B\", id=";
        exp[29] = "n8->n11 [label=\"A\", id=";
        exp[30] = "n11 [label=\"drought\"];";
        exp[31] = "n11->n12 [label=\"A,C\", id=";
        exp[32] = "n10 [label=\"march\"];";
        exp[33] = "n10->n12 [label=\"B\", id=";
        exp[34] = "n12 [label=\"of\"];";
        exp[35] = "n12->n14 [label=\"B\", id=";
        exp[36] = "n12->n13 [label=\"A,C\", id=";
        exp[37] = "n14 [label=\"drought\"];";
        exp[38] = "n14->n15 [label=\"B\", id=";
        exp[39] = "n13 [label=\"march\"];";
        exp[40] = "n13->n15 [label=\"A,C\", id=";
        exp[41] = "n15 [label=\"has\"];";
        exp[42] = "n15->n17 [label=\"A,B,C\", id=";
        exp[43] = "n17 [label=\"pierced\"];";
        exp[44] = "n17->n18 [label=\"A\", id=";
        exp[45] = "n17->n19 [label=\"B\", id=";
        exp[46] = "n17->n20 [label=\"C\", id=";
        exp[47] = "n18 [label=\"unto\"];";
        exp[48] = "n18->n21 [label=\"A\", id=";
        exp[49] = "n19 [label=\"to\"];";
        exp[50] = "n19->n21 [label=\"B\", id=";
        exp[51] = "n20 [label=\"teh\"];";
        exp[52] = "n20->n28 [label=\"C\", id=";
        exp[53] = "n21 [label=\"the\"];";
        exp[54] = "n21->n6 [label=\"A,B\", id=";
        exp[55] = "n28 [label=\"rood\"];";
        exp[56] = "n28->n3 [label=\"C\", id=";
        exp[57] = "n6 [label=\"root\"];";
        exp[58] = "n6->n3 [label=\"A,B\", id=";
        exp[59] = "n3 [label=\"#END#\"];";
        exp[60] = "n16->n27 [style=dotted, label=\"transposition\", id=";
        exp[61] = "n11->n14 [style=dotted, label=\"transposition\", id=";
        exp[0] = "n10->n13 [style=dotted, label=\"transposition\", id=";

        for (String anExp : exp) {
            assertTrue(str.contains(anExp));
        }
    }

    /**
     * Test if it is possible to change the user of a Tradition
     */
    @Test
    public void changeMetadataOfATraditionTest() {

        Result result;
        Node newUser;
        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Node rootNode = db.findNode(Nodes.ROOT, "name", "Root node");
            newUser = db.createNode(Nodes.USER);
            newUser.setProperty("id", "42");
            newUser.setProperty("role", "admin");

            rootNode.createRelationshipTo(newUser, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * The user with id 42 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            Iterable<Relationship> ownedTraditions = newUser.getRelationships(ERelations.OWNS_TRADITION);
            tx.success();
            assertFalse(ownedTraditions.iterator().hasNext());
        }

        /*
         * Verify that user 1 has tradition
         */
        try (Transaction tx = db.beginTx()) {
            Node origUser = db.findNode(Nodes.USER, "id", "1");
            Iterable<Relationship> ownership = origUser.getRelationships(ERelations.OWNS_TRADITION);
            assertTrue(ownership.iterator().hasNext());
            Node tradNode = ownership.iterator().next().getEndNode();
            TraditionModel tradition = new TraditionModel(tradNode);

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        } catch (Exception e) {
            int a = 0;
        }

        /*
         * Change the owner of the tradition, and another of its properties
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setDirection("RL");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        ClientResponse ownerChangeResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, textInfo);
        assertEquals(Status.OK.getStatusCode(), ownerChangeResponse.getStatus());

        /*
         * Test if user with id 42 has now the tradition
         */
        try (Transaction tx = db.beginTx()) {
            Node tradNode = db.findNode(Nodes.TRADITION, "id", tradId);
            TraditionModel tradition = new TraditionModel(tradNode);

            assertEquals("42", tradition.getOwner());
            assertEquals(tradId, tradition.getId());
            assertEquals("RenamedTraditionName", tradition.getName());
            assertEquals("RL", tradition.getDirection());
            tx.success();

        }

        /*
         * The user with id 1 has no tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();
        }
    }


    /**
     * Test if there is the correct error when trying to change a tradition with an invalid userid
     */
	@Test
    public void changeMetadataOfATraditionTestWithWrongUser() {
        /* Preconditon
         * The user with id 1 has tradition
         */
        Result result;
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        }

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("1337");

        ClientResponse removalResponse = jerseyTest.resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals(removalResponse.getEntity(String.class), "Error: A user with this id does not exist");

        /* PostCondition
         * The user with id 1 has still tradition
         */
        TraditionModel tradition = new TraditionModel();
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();

            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            tx.success();
        }

        assertTrue(tradition.getId().equals(tradId));
        assertTrue(tradition.getName().equals("Tradition"));

    }

    /**
     * Test if it is posibible to change the user of a Tradition with invalid traditionid
     */
    @Test
    public void changeMetadataOfATraditionTestWithInvalidTradid() {

        /*
         * Create a second user with id 42
         */
        try (Transaction tx = db.beginTx()) {
            Result result = db.execute("match (n:ROOT) return n");
            Iterator<Node> nodes = result.columnAs("n");
            Node rootNode = nodes.next();

            Node node = db.createNode(Nodes.USER);
            node.setProperty("id", "42");
            node.setProperty("role", "admin");

            rootNode.createRelationshipTo(node, ERelations.SYSTEMUSER);
            tx.success();
        }

        /*
         * The user with id 42 has no tradition
         */
        Result result;
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'42'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();

        }

        /*
         * The user with id 1 has tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        }

        /*
         * Change the owner of the tradition
         */
        TraditionModel textInfo = new TraditionModel();
        textInfo.setName("RenamedTraditionName");
        textInfo.setLanguage("nital");
        textInfo.setIs_public(false);
        textInfo.setOwner("42");

        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/1337")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, textInfo);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());
        assertEquals("There is no Tradition with this id", removalResponse.getEntity(String.class));

        /*
         * Post condition nothing has changed
         *
         */

        /*
         * Test if user with id 1 has still the old tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'1'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            Node tradNode = tradIterator.next();
            TraditionModel tradition = new TraditionModel();
            tradition.setId(tradNode.getProperty("id").toString());
            tradition.setName(tradNode.getProperty("name").toString());

            assertTrue(tradition.getId().equals(tradId));
            assertTrue(tradition.getName().equals("Tradition"));

            tx.success();
        }

        /*
         * The user with id 42 has still no tradition
         */
        try (Transaction tx = db.beginTx()) {
            result = db.execute("match (n)<-[:OWNS_TRADITION]-(userId:USER {id:'42'}) return n");
            Iterator<Node> tradIterator = result.columnAs("n");
            assertTrue(!tradIterator.hasNext());

            tx.success();
        }
    }

    /**
     * Remove a complete Tradition
     */
    @Test
    public void deleteTraditionByIdTest() {
        ClientResponse removalResponse = jerseyTest
                .resource()
                .path("/tradition/" + tradId)
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), removalResponse.getStatus());


        Node startNode = DatabaseService.getStartNode(tradId, db);

        assertTrue(startNode == null);
    }

    /**
     * Test that all the nodes of a tradition have been removed
     */
    @Test
    public void deleteTraditionCompletelyTest() {
        // count the total number of nodes
        AtomicInteger numNodes = new AtomicInteger(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        int originalNodeCount = numNodes.get();

        // upload the florilegium
        String florId = null;
        try {
            String testfile = "src/TestFiles/florilegium_graphml.xml";
            florId = createTraditionFromFile("Tradition", "LR", "1", testfile, "graphml");
        } catch (FileNotFoundException e) {
            assertTrue(false);
        }

        // give it a stemma
        String newStemma = null;
        try {
            byte[] encStemma = Files.readAllBytes(Paths.get("src/TestFiles/florilegium.dot"));
            newStemma = new String(encStemma, Charset.forName("utf-8"));
        } catch (IOException e) {
            assertTrue(false);
        }
        ClientResponse jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId + "/stemma")
                .type(MediaType.APPLICATION_JSON)
                .put(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // re-root the stemma
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId + "/stemma/Stemma/reorient/2")
                .type(MediaType.APPLICATION_JSON)
                .post(ClientResponse.class, newStemma);
        assertEquals(ClientResponse.Status.OK.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());

        // give it some relationships - rank 37, rank 13, ranks 217/219
        try (Transaction tx = db.beginTx()) {
            int[] alignRanks = {77, 110};
            for (int r : alignRanks) {
                ResourceIterator<Node> atRank = db.findNodes(Nodes.READING, "rank", r);
                assertTrue(atRank.hasNext());
                ReadingModel rdg1 = new ReadingModel(atRank.next());
                ReadingModel rdg2 = new ReadingModel(atRank.next());
                RelationshipModel rel = new RelationshipModel();
                rel.setReading_a(rdg1.getText());
                rel.setReading_b(rdg2.getText());
                rel.setType("grammatical");
                rel.setScope("local");
                rel.setSource(rdg1.getId());
                rel.setTarget(rdg2.getId());
                jerseyResponse = jerseyTest.resource()
                        .path("/tradition/" + tradId + "/relation")
                        .type(MediaType.APPLICATION_JSON)
                        .put(ClientResponse.class, rel);
                assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            }

            // and a transposition, for kicks
            Node tx1 = db.findNode(Nodes.READING, "rank", 217);
            Node tx2 = db.findNode(Nodes.READING, "rank", 219);
            RelationshipModel txrel = new RelationshipModel();
            txrel.setType("transposition");
            txrel.setScope("local");
            txrel.setSource(String.valueOf(tx1.getId()));
            txrel.setTarget(String.valueOf(tx2.getId()));
            jerseyResponse = jerseyTest.resource()
                    .path("/tradition/" + tradId + "/relation")
                    .type(MediaType.APPLICATION_JSON)
                    .put(ClientResponse.class, txrel);
            assertEquals(ClientResponse.Status.CREATED.getStatusCode(), jerseyResponse.getStatusInfo().getStatusCode());
            tx.success();
        }

        // now count the nodes
        numNodes.set(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        assertTrue(numNodes.get() > originalNodeCount);

        // delete the florilegium
        jerseyResponse = jerseyTest
                .resource()
                .path("/tradition/" + florId)
                .type(MediaType.APPLICATION_JSON)
                .delete(ClientResponse.class);
        assertEquals(Response.Status.OK.getStatusCode(), jerseyResponse.getStatus());

        // nodes should be back to original number
        numNodes.set(0);
        try (Transaction tx = db.beginTx()) {
            db.execute("match (n) return n").forEachRemaining(x -> numNodes.getAndIncrement());
            tx.success();
        }
        assertEquals(originalNodeCount, numNodes.get());
    }

    /**
     * Test do delete a Tradition with an invalid id deletTraditionById
     */
    @Test
    public void deleteATraditionWithInvalidIdTest() {
        try (Transaction tx = db.beginTx()) {
            /*
             * Try to remove a tradition with invalid id
             */
            ClientResponse removalResponse = jerseyTest
                    .resource()
                    .path("/tradition/1337")
                    .delete(ClientResponse.class);
            assertEquals(Response.Status.NOT_FOUND.getStatusCode(), removalResponse.getStatus());

            /*
             * Test if user 1 still exists
             */
            Result result = db.execute("match (userId:USER {id:'1'}) return userId");
            Iterator<Node> nodes = result.columnAs("userId");
            assertTrue(nodes.hasNext());

            /*
             * Check if tradition {tradId} still exists
             */
            result = db.execute("match (t:TRADITION {id:'" + tradId + "'}) return t");
            nodes = result.columnAs("t");
            assertTrue(nodes.hasNext());
            tx.success();
        }
    }

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
