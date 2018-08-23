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


import java.util.Collection;
import java.util.List;
import java.util.Map;

public class geomeFIMSConnection extends FIMSConnection {
    private static final String HOST = "api.develop.geome-db.org";
    static final String GEOME_URL = "https://" + HOST;
    private geomeFIMSClient client;

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
            List<Project> projects = client.getProjects();
            System.out.println("Finding projects...");
            for (Project project : projects) {
                System.out.println(project.title);
                // todo handle gzip encoding
//                Invocation.Builder configRequest = client.getQueryTarget().path("projects").path(String.valueOf(project.id)).path("config").request();
//
//                Response response = configRequest.get();
//                ProjectConfig config = geomeFIMSClient.getRestServiceResult(ProjectConfig.class, response);
//                for (ProjectConfig.Entity entity : config.entities) {
//                    System.out.println("Entity " + entity);
//                    for (Project.Field field : entity.attributes) {
//                        System.out.println(field.column + " (" + field.uri + ")");
//                    }
//                }
            }
        } catch (Exception e) {
            throw new ConnectionException("Can not log in to host : " + fimsOptions.getHost() + " with your credential.");
        }
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
