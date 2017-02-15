/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

/**
 *
 * @author marijn
 */
public class ApplicationContextListener implements ServletContextListener {
    
    //private static final String DB_ENV = System.getenv("DATABASE_HOME");
    //private static final String DB_PATH = DB_ENV == null ? "/var/lib/stemmarest" : DB_ENV;
    private static final String DB_PATH = "/data/tagaid/neo4jdb";
    
    private ServletContext context = null;
        
    public void contextInitialized(ServletContextEvent event) {
        GraphDatabaseService db = new GraphDatabaseServiceProvider(DB_PATH).getDatabase();
        this.context = event.getServletContext();
        this.context.setAttribute("neo4j", db);
        //Output a simple message to the server's console
        DatabaseService.createRootNode(db);    
        
    }

    public void contextDestroyed(ServletContextEvent event) {
        //Output a simple message to the server's console
        //System.out.println("Tagaid - Stemmarest application - Step 1/4: Call to destroy context");
        this.context = event.getServletContext();
        try {
            GraphDatabaseService db = (GraphDatabaseService) this.context.getAttribute("neo4j");
            if (db != null) {
                //System.out.println("Tagaid - Stemmarest application - Step 2/4: Shutting down database");
                db.shutdown();
                // wait for shutdown to finish
                //System.out.println("Tagaid - Stemmarest application - Step 3/4: Sleeping for 5 seconds");
                //TimeUnit.SECONDS.sleep(5);
                // shutdown remaining threadLocals
                //System.out.println("Tagaid - Stemmarest application - Step 4/4: Immolating ThreadLocals");
                //JVMHelper.immolativeShutdown();
                System.out.println("Tagaid - Stemmarest application - Neo4j shutdown finished!");
                
            }
        } catch (Exception e) {
            System.out.println("Tagaid - Stemmarest application - Exception");
            e.printStackTrace();
        }
    }
    
}
