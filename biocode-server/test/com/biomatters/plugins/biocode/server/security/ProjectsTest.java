package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.*;

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
    public void deletingParentDeletesChild() {
        Project p = new Project();
        p.name = "parent";
        Project parent = Projects.addProject(dataSource, p);

        Project p2 = new Project();
        p2.name = "child";
        p2.parentProjectId = parent.id;
        Projects.addProject(dataSource, p2);

        Projects.deleteProject(dataSource, parent.id);
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

    @Test
    public void canListRoles() {
        User user1 = new User("user1", "password", "", "", "test@test.com", true, false);
        Users.addUser(dataSource, user1);

        Project p = new Project();
        p.name = "Test";
        p.userRoles.put(user1, Role.WRITER);
        Project added = Projects.addProject(dataSource, p);

        Project inDatabase = Projects.getProjectForId(dataSource, added.id);
        assertEquals(1, inDatabase.userRoles.size());
        assertEquals(Role.WRITER, inDatabase.userRoles.get(user1));
    }

    @Test
    public void canSetRole() {
        User user1 = new User("user1", "password", "", "", "test@test.com", true, false);
        Users.addUser(dataSource, user1);

        Project p = new Project();
        p.name = "Test";
        p.userRoles.put(user1, Role.WRITER);
        Project added = Projects.addProject(dataSource, p);

        Projects.setProjectRoleForUsername(dataSource, p.id, user1.username, Role.ADMIN);

        Project inDatabase = Projects.getProjectForId(dataSource, added.id);
        assertEquals(1, inDatabase.userRoles.size());
        assertEquals(Role.ADMIN, inDatabase.userRoles.get(user1));

        Projects.removeUserFromProject(dataSource, added.id, user1.username);
        inDatabase = Projects.getProjectForId(dataSource, added.id);
        assertTrue(inDatabase.userRoles.isEmpty());
    }

    @Test
    public void canAddHierarchy() throws DatabaseServiceException {
        FimsProject root = new FimsProject("1", "root", null);

        FimsProject root2 = new FimsProject("2", "root2", null);
        FimsProject root3 = new FimsProject("3", "root3", null);

        FimsProject rootChild = new FimsProject("4", "rootChild", root);
        FimsProject rootChild2 = new FimsProject("5", "rootChild2", root);

        FimsProject rootChildChild = new FimsProject("6", "rootChildChild", rootChild);

        List<FimsProject> noChildren = Arrays.asList(root, root2, root3);
        checkDatabaseMatchesList(dataSource, noChildren);
        checkDatabaseMatchesList(dataSource, noChildren);  // Check it does not create duplicates

        List<FimsProject> withChildren = Arrays.asList(root, root2, root3, rootChild, rootChild2, rootChildChild);
        checkDatabaseMatchesList(dataSource, withChildren);
    }

    @Test
    public void canUpdateProjectHierarchy() throws DatabaseServiceException {
        FimsProject root = new FimsProject("1", "root", null);
        FimsProject rootChild = new FimsProject("2", "rootChild", root);
        FimsProject rootChildChild = new FimsProject("3", "rootChildChild", rootChild);
        FimsProject rootChildChildMoved = new FimsProject("3", "rootChildChild", root);
        FimsProject rootChildChildRenamed = new FimsProject("3", "sub", root);
        FimsProject rootChildChildNotAChild = new FimsProject("3", "sub", null);

        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChild));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChildMoved));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChildRenamed));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChildNotAChild));
    }

    @Test
    public void deletesOldProjects() throws DatabaseServiceException {
        FimsProject root = new FimsProject("1", "root", null);
        FimsProject rootChild = new FimsProject("2", "rootChild", root);
        FimsProject rootChildChild = new FimsProject("3", "rootChildChild", rootChild);

        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChild));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root));
    }

    private void checkDatabaseMatchesList(DataSource dataSource, List<FimsProject> expected) throws DatabaseServiceException {
        Projects.updateProjectsFromFims(dataSource, new FimsWithProjects(expected));
        List<Project> inDatabase = Projects.getProjectsForId(dataSource);
        assertEquals(expected.size(), inDatabase.size());

        Map<String, Project> inDatabaseByKey = new HashMap<String, Project>();
        for (Project project : inDatabase) {
            inDatabaseByKey.put(project.globalId, project);
        }

        Set<Integer> idsSeen = new HashSet<Integer>();
        for (FimsProject fimsProject : expected) {
            Project toCompare = inDatabaseByKey.get(fimsProject.getId());
            assertNotNull(toCompare);
            assertFalse(idsSeen.contains(toCompare.id));
            idsSeen.add(toCompare.id);
            assertEquals(fimsProject.getId(), toCompare.globalId);
            assertEquals(fimsProject.getName(), toCompare.name);
            FimsProject parent = fimsProject.getParent();
            if(parent != null) {
                Project parentInDatabase = inDatabaseByKey.get(parent.getId());
                assertTrue(parentInDatabase.id == toCompare.parentProjectId);
                assertNotNull(parentInDatabase);
                assertEquals(parent.getName(), parentInDatabase.name);
            } else {
                assertTrue(-1 == toCompare.parentProjectId);
            }
        }
    }

    private class FimsWithProjects extends FIMSConnection {
        List<FimsProject> fimsProjects;

        private FimsWithProjects(List<FimsProject> fimsProjects) {
            this.fimsProjects = fimsProjects;
        }

        @Override
        public List<FimsProject> getProjects() throws DatabaseServiceException {
            return fimsProjects;
        }

        @Override
        public String getLabel() {
            return null;
        }

        @Override
        public String getName() {
            return null;
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public PasswordOptions getConnectionOptions() {
            return null;
        }

        @Override
        public void _connect(Options options) throws ConnectionException {

        }

        @Override
        public void disconnect() {

        }

        @Override
        public DocumentField getTissueSampleDocumentField() {
            return null;
        }

        @Override
        public List<String> getProjectsForSamples(Collection<FimsSample> samples) {
            return Collections.emptyList();
        }

        @Override
        public List<DocumentField> getCollectionAttributes() {
            return null;
        }

        @Override
        public List<DocumentField> getTaxonomyAttributes() {
            return null;
        }

        @Override
        public List<DocumentField> _getSearchAttributes() {
            return null;
        }

        @Override
        public List<String> getTissueIdsMatchingQuery(Query query) throws ConnectionException {
            return null;
        }

        @Override
        protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
            return null;
        }

        @Override
        public int getTotalNumberOfSamples() throws ConnectionException {
            return 0;
        }

        @Override
        public DocumentField getPlateDocumentField() {
            return null;
        }

        @Override
        public DocumentField getWellDocumentField() {
            return null;
        }

        @Override
        public boolean storesPlateAndWellInformation() {
            return false;
        }

        @Override
        public boolean hasPhotos() {
            return false;
        }
    }
}
