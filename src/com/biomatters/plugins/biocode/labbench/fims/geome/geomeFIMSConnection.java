package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;

import com.biomatters.plugins.biocode.labbench.fims.geome.geomeFIMSClient;
import com.biomatters.plugins.biocode.labbench.fims.geome.geomeFIMSOptions;
import com.biomatters.plugins.biocode.labbench.fims.geome.Project;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class geomeFIMSConnection extends FIMSConnection {
    private static final String HOST = "api.develop.geome-db.org";
        private static final int PORT = 80;
    static final String GEOME_URL = "https://" + HOST + ":" + PORT;
    private geomeFIMSClient client;
          private Project project;

    @Override
    public String getLabel() {
        return "GeOMe FIMS";
    }

    @Override
    public String getName() {
        return "GeOMe FIMS";
    }

    @Override
    public String getDescription() {
        return "Connection to GeOMe (https://geome-db.org/)";
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new geomeFIMSOptions();
    }



    @Override
    public void _connect(Options options) throws ConnectionException {
        if (!(options instanceof geomeFIMSOptions)) {
                   throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptions()");
               }
               geomeFIMSOptions fimsOptions = (geomeFIMSOptions) options;
               client = new geomeFIMSClient(fimsOptions.getHost(), requestTimeoutInSeconds);
               try {
                   client.login(fimsOptions.getUserName(), fimsOptions.getPassword());
               } catch (Exception e) {
                   throw new ConnectionException("Can not log in to host : " + fimsOptions.getHost() + " with your credential.");
               }

               project = fimsOptions.getProject();
               if (project == null) {
                   throw new ConnectionException("You must select a project");
               }
               // JBD: i don't think we need graphs
               /*
               graphs = new HashMap<String, Graph>();
               try {
                   List<Graph> graphsForExpedition = client.getGraphsForProject("" + project.id);
                   if (graphsForExpedition.isEmpty()) {
                       throw new ConnectionException("Project has no expeditions");
                   }
                   for (Graph graph : graphsForExpedition) {
                       graphs.put(graph.getExpeditionTitle(), graph);
                   }
               } catch (DatabaseServiceException e) {
                   throw new ConnectionException(e.getMessage(), e);
               }
               */
    }

    @Override
    public void disconnect() {

    }

    @Override
    public DocumentField getTissueSampleDocumentField() {
        return null;
    }

    @Override
    public Map<String, Collection<FimsSample>> getProjectsForSamples(Collection<FimsSample> samples) {
        return null;
    }

    @Override
    public List<FimsProject> getProjects() throws DatabaseServiceException {
        return null;
    }

    @Override
    protected List<DocumentField> _getCollectionAttributes() {
        return null;
    }

    @Override
    protected List<DocumentField> _getTaxonomyAttributes() {
        return null;
    }

    @Override
    protected List<DocumentField> _getSearchAttributes() {
        return null;
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        return null;
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmptyQuery) throws ConnectionException {
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
