package net.stemmaweb.parser;

import net.stemmaweb.rest.ERelations;
import net.stemmaweb.rest.Nodes;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import org.neo4j.graphdb.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Parser for CollateX-collated traditions.
 *
 * @author tla
 */
public class CollateXParser {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();

    public Response parseCollateX(String filename, String tradId)
            throws FileNotFoundException {
        File file = new File(filename);
        InputStream in = new FileInputStream(file);
        return parseCollateX(in, tradId);
    }

    public Response parseCollateX(InputStream filestream, String tradId)
    {
        // Try this the DOM parsing way
        Document doc;
        try {
            DocumentBuilder dbuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doc = dbuilder.parse(filestream);
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        Element rootEl = doc.getDocumentElement();
        rootEl.normalize();
        // Get the data keys
        HashMap<String,String> dataKeys = new HashMap<>();
        NodeList keyNodes = rootEl.getElementsByTagName("key");
        for (int i = 0; i < keyNodes.getLength(); i++) {
            NamedNodeMap keyAttrs = keyNodes.item(i).getAttributes();
            dataKeys.put(keyAttrs.getNamedItem("id").getNodeValue(), keyAttrs.getNamedItem("attr.name").getNodeValue());
        }
//        String tradId = UUID.randomUUID().toString();
        try (Transaction tx = db.beginTx()) {
            // Get the tradition node
            Node traditionNode = db.findNode(Nodes.TRADITION, "id", tradId);
            // Create the tradition node
/*            Node traditionNode = db.createNode(Nodes.TRADITION); // create the root node of tradition
            traditionNode.setProperty("id", tradId);
            traditionNode.setProperty("name", tradName);
            traditionNode.setProperty("direction", direction);
*/

            // Create all the nodes from the graphml nodes
            NodeList readingNodes = rootEl.getElementsByTagName("node");
            HashMap<String,Node> createdReadings = new HashMap<>();
            Long highestRank = 0L;
            for (int i = 0; i < readingNodes.getLength(); i++) {
                NamedNodeMap rdgAttrs = readingNodes.item(i).getAttributes();
                String cxId = rdgAttrs.getNamedItem("id").getNodeValue();
                Node reading = db.createNode(Nodes.READING);

                NodeList dataNodes = ((Element) readingNodes.item(i)).getElementsByTagName("data");
                for (int j = 0; j < dataNodes.getLength(); j++) {
                    NamedNodeMap dataAttrs = dataNodes.item(j).getAttributes();
                    String keyId = dataAttrs.getNamedItem("key").getNodeValue();
                    String keyVal = dataNodes.item(j).getTextContent();
                    if (dataKeys.get(keyId).equals("rank")) {
                        Long rankVal = Long.valueOf(keyVal);
                        reading.setProperty("rank", rankVal);
                        highestRank = rankVal > highestRank ? rankVal : highestRank;
                        // Detect start node
                        if (rankVal == 0) {
                            traditionNode.createRelationshipTo(reading, ERelations.COLLATION);
                            reading.setProperty("is_start", true);
                        }
                    } else if (dataKeys.get(keyId).equals("tokens"))
                        reading.setProperty("text", keyVal);
                }
                createdReadings.put(cxId, reading);
            }
            // Identify the end node. Assuming that there is only one.
            final Long hr = highestRank;
            Node endNode = createdReadings.values().stream()
                    .filter(x -> Long.valueOf(x.getProperty("rank").toString()).equals(hr))
                    .findFirst().get();
            endNode.setProperty("is_end", true);
            traditionNode.createRelationshipTo(endNode, ERelations.HAS_END);

            // Create all the sequences and keep track of the witnesses we see
            HashSet<String> seenWitnesses = new HashSet<>();
            NodeList edgeNodes = rootEl.getElementsByTagName("edge");
            for (int i = 0; i < edgeNodes.getLength(); i++) {
                NamedNodeMap edgeAttrs = edgeNodes.item(i).getAttributes();
                String sourceId = edgeAttrs.getNamedItem("source").getNodeValue();
                String targetId = edgeAttrs.getNamedItem("target").getNodeValue();
                Node source = createdReadings.get(sourceId);
                Node target = createdReadings.get(targetId);
                RelationshipType rtype = null;
                String[] witnessList = null;
                NodeList dataNodes = ((Element) edgeNodes.item(i)).getElementsByTagName("data");
                for (int j = 0; j < dataNodes.getLength(); j++) {
                    NamedNodeMap dataAttrs = dataNodes.item(j).getAttributes();
                    String keyId = dataAttrs.getNamedItem("key").getNodeValue();
                    String keyVal = dataNodes.item(j).getTextContent();
                    if (dataKeys.get(keyId).equals("type")) {
                        if (keyVal.equals("path"))
                            rtype = ERelations.SEQUENCE;
                        else
                            rtype = ERelations.RELATED;
                    } else if (dataKeys.get(keyId).equals("witnesses")) {
                        witnessList = keyVal.split(",\\s+");
                        Collections.addAll(seenWitnesses, witnessList);
                    }
                }
                Relationship relation = source.createRelationshipTo(target, rtype);
                if (rtype != null && rtype.equals(ERelations.RELATED)) {
                    relation.setProperty("source", source.getId());
                    relation.setProperty("target", target.getId());
                    relation.setProperty("type", "transposition");
                    relation.setProperty("reading_a", source.getProperty("text"));
                    relation.setProperty("reading_b", target.getProperty("text"));
                } else {
                    relation.setProperty("witnesses", witnessList);
                }


            }
            // Create all the witnesses
            seenWitnesses.forEach(x -> {
                Node witness = db.createNode(Nodes.WITNESS);
                witness.setProperty("sigil", x);
                witness.setProperty("hypothetical", false);
                witness.setProperty("quotesigil", isDotId(x));
                traditionNode.createRelationshipTo(witness, ERelations.HAS_WITNESS);
            });

/*
            // Set the user if it exists in the system; auto-create the user if it doesn't exist
            Node userNode = db.findNode(Nodes.USER, "id", userId);
            if (userNode == null) {
                userNode = db.createNode(Nodes.USER);
                userNode.setProperty("id", userId);
                db.findNode(Nodes.ROOT, "name", "Root node").createRelationshipTo(userNode, ERelations.SYSTEMUSER);
            }
            userNode.createRelationshipTo(traditionNode, ERelations.OWNS_TRADITION);
*/
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().build();
        }

        String response = String.format("{\"tradId\":\"%s\"}", tradId);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    private Boolean isDotId (String nodeid) {
        return nodeid.matches("^[A-Za-z][A-Za-z0-9_.]*$")
                || nodeid.matches("^-?(\\.\\d+|\\d+\\.\\d+)$");
    }

}
