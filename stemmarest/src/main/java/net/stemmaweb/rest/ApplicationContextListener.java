/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.stemmaweb.rest;

import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.neo4j.graphdb.GraphDatabaseService;

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
        this.context = event.getServletContext();
        //Output a simple message to the server's console
        GraphDatabaseService db = new GraphDatabaseServiceProvider(DB_PATH).getDatabase();
        DatabaseService.createRootNode(db);    
    }

    public void contextDestroyed(ServletContextEvent event) {
        //Output a simple message to the server's console
        try {
            GraphDatabaseService db = new GraphDatabaseServiceProvider().getDatabase();
            if (db != null) {
                db.shutdown();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.context = null;
    }
    
}
