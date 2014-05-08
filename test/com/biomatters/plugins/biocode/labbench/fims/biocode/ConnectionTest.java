package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.utilities.SharedCookieHandler;
import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 3:10 PM
 */
public class ConnectionTest extends Assert {

    @Test
    public void getGraphs() throws DatabaseServiceException {
        List<Graph> graphs = BiocodeFIMSUtils.getGraphsForProject("1");
        assertNotNull(graphs);
        assertFalse(graphs.isEmpty());
    }

    @Test
    public void getFimsData() throws DatabaseServiceException {
        BiocodeFimsData data = BiocodeFIMSUtils.getData("1", null, null, null);

        System.out.println(data.header);
        assertFalse(data.header.isEmpty());
    }

    @Test
    public void getProjects() throws DatabaseServiceException {
        List<Project> projects = BiocodeFIMSUtils.getProjects();
        assertFalse("There should be some projects", projects.isEmpty());
        for (Project project : projects) {
            assertNotNull(project.code);
            assertNotNull(project.id);
            assertNotNull(project.title);
            assertNotNull(project.xmlLocation);
        }
    }

    @Test
    public void checkLoginWorks() throws MalformedURLException, DatabaseServiceException {
        String host = "biscicol.org";
        SharedCookieHandler.registerHost(host);
        BiocodeFIMSUtils.login("http://" + host, "demo", "demo");
        for (Project project : BiocodeFIMSUtils.getProjects()) {
            System.out.println(project);
        }
    }

    @Test(expected = DatabaseServiceException.class)
    public void checkProjectRetrievalWhenNotLoggedIn() throws DatabaseServiceException {
        BiocodeFIMSUtils.getProjects();
    }
}
