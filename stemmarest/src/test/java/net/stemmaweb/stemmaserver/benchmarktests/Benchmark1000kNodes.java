package net.stemmaweb.stemmaserver.benchmarktests;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;

import net.stemmaweb.rest.Reading;
import net.stemmaweb.rest.Tradition;
import net.stemmaweb.rest.User;
import net.stemmaweb.services.GraphDatabaseServiceProvider;
import net.stemmaweb.services.GraphMLToNeo4JParser;
import net.stemmaweb.stemmaserver.JerseyTestServerFactory;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;

import com.carrotsearch.junitbenchmarks.annotation.AxisRange;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import org.neo4j.test.TestGraphDatabaseFactory;

/**
 * 
 * @author PSE FS 2015 Team2
 *
 */
@AxisRange(min = 0, max = 0.2)
@BenchmarkMethodChart(filePrefix = "benchmark/benchmark-1000kNodes")
public class Benchmark1000kNodes extends BenchmarkTests {

    @BeforeClass
    public static void prepareTheDatabase(){

        /*
         * Fill the Testbench with a nice graph 9 users 2 traditions 5 witnesses with degree 10
         */
        initDatabase();
    }

    public static void initDatabase() {
        RandomGraphGenerator rgg = new RandomGraphGenerator();

        GraphDatabaseService db = new GraphDatabaseServiceProvider(
                new TestGraphDatabaseFactory().newImpermanentDatabase()).getDatabase();

        userResource = new User();
        traditionResource = new Tradition();
        readingResoruce = new Reading();
        importResource = new GraphMLToNeo4JParser();

        jerseyTest = JerseyTestServerFactory.newJerseyTestServer()
                .addResource(userResource)
                .addResource(traditionResource)
                .addResource(readingResoruce).create();
        try {
            jerseyTest.setUp();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        rgg.role(db, 10, 100, 10, 100);

		testfile = new File("src/TestXMLFiles/ReadingstestTradition.xml");

        try {
            tradId = importResource.parseGraphML(testfile.getPath(), "1", "Tradition")
                    .getEntity()
                    .toString()
                    .replace("{\"tradId\":", "")
                    .replace("}", "");
        } catch (FileNotFoundException f) {
            // this error should not occur
            assertTrue(false);
        }

        Result result = db.execute("match (w:READING {text:'showers'}) return w");
        Iterator<Node> nodes = result.columnAs("w");
        duplicateReadingNodeId = nodes.next().getId();

        result = db.execute("match (w:READING {text:'the root'}) return w");
        nodes = result.columnAs("w");
        theRoot = nodes.next().getId();

        result = db.execute("match (w:READING {text:'unto me'}) return w");
        nodes = result.columnAs("w");
        untoMe = nodes.next().getId();

    }

    @AfterClass
    public static void shutdown() throws Exception{
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        dbServiceProvider.getDatabase().shutdown();
        jerseyTest.tearDown();
    }


}
