package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 30/06/14 12:05 AM
 */
public class ProjectsTest extends Assert {

    private File tempDir;
    private DataSource dataSource;

    @Before
    public void setupDatabase() throws ConnectionException, IOException {
        tempDir = FileUtilities.createTempDir(true);
        String path = tempDir.getAbsolutePath() + File.separator + "database.db";
        System.out.println("Database Path: " + path);
        String connectionString = "jdbc:hsqldb:file:" + path + ";shutdown=true";
        dataSource = SqlLimsConnection.createBasicDataSource(connectionString, BiocodeService.getInstance().getLocalDriver(), null, null);
        SecurityConfig.createUserTablesIfNecessary(dataSource);
    }

    @After
    public void closeDatabase() {
        dataSource = null;
        tempDir = null;
    }

    @Test
    public void canAddProject() {
        System.out.println("Test");
        Project p = new Project();
        p.name = "Test";

        Project p2 = new Project();
        p2.name = "Test2";

        Project p3 = new Project();
        p3.name = "Test3";

        Projects.addProject(dataSource, p);
        List<Project> inDatabase = Projects.getProjectsForId(dataSource);
        assertEquals(1, inDatabase.size());
        assertEquals(p.name, inDatabase.get(0).name);
        assertTrue(p.userRoles.isEmpty());

        Projects.addProject(dataSource, p2);
        Projects.addProject(dataSource, p3);
        inDatabase = Projects.getProjectsForId(dataSource);
        assertEquals(3, inDatabase.size());
        assertEquals(p.name, inDatabase.get(0).name);
        assertEquals(p2.name, inDatabase.get(1).name);
        assertEquals(p3.name, inDatabase.get(2).name);
    }

    @Test
    public void canDeleteProject() {
        Project p = new Project();
        p.name = "Test";
        Project added = Projects.addProject(dataSource, p);

        Projects.deleteProject(dataSource, added.id);
        List<Project> projectList = Projects.getProjectsForId(dataSource);
        assertTrue(projectList.isEmpty());
    }

    @Test
    public void canUpdateProject() {
        String oldName = "Test";
        String newName = "Test2";

        Project p = new Project();
        p.name = oldName;
        Project added = Projects.addProject(dataSource, p);

        added.name = newName;
        Projects.updateProject(dataSource, added);
        List<Project> inDatabase = Projects.getProjectsForId(dataSource);
        assertEquals(1, inDatabase.size());
        assertEquals(newName, inDatabase.get(0).name);
    }
}
