package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 3:10 PM
 */
public class ConnectionTest extends BiocodeFimsTestCase {

    @Test
    public void getGraphs() throws DatabaseServiceException {
        List<Graph> graphs = client.getGraphsForProject("1");
        assertNotNull(graphs);
        assertFalse(graphs.isEmpty());
    }

    @Test
    public void getFimsData() throws DatabaseServiceException {
        BiocodeFimsData data = client.getData("24", null, null, null);

        System.out.println(data.header);
        assertFalse(data.header.isEmpty());
    }

    @Test
    public void checkLoginWorks() throws MalformedURLException, DatabaseServiceException, ConnectionException {
        client.login("demo", "demo");
        for (Project project : client.getProjects()) {
            System.out.println(project);
        }
    }

    @Test(expected = Exception.class)
    public void checkLoginCanFail() throws MalformedURLException, DatabaseServiceException {
        client.login("a", "bc");
        fail("login should have thrown a ConnectionException because we used incorrect credentials");
    }

    @Test
    public void getProjects() throws DatabaseServiceException, MalformedURLException {
        client.login("demo", "demo");
        List<Project> projects = client.getProjects();
        assertFalse("There should be some projects", projects.isEmpty());
        for (Project project : projects) {
            assertNotNull(project.code);
            assertNotNull(project.id);
            assertNotNull(project.title);
            assertNotNull(project.xmlLocation);
        }
    }

    @Test(expected = DatabaseServiceException.class)
    public void checkProjectRetrievalWhenNotLoggedIn() throws DatabaseServiceException {
        client.getProjects();
    }
}
