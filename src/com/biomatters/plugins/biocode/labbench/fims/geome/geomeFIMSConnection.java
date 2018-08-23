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
import org.apache.commons.collections.map.LinkedMap;


import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;
import java.util.*;

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

                Invocation.Builder configRequest = client.getQueryTarget().path("projects").path(String.valueOf(project.id)).path("config").request();

                Response response = configRequest.get();
                ProjectConfig config = geomeFIMSClient.getRestServiceResult(ProjectConfig.class, response);
                for (ProjectConfig.Entity entity : config.entities) {
                    if(!Arrays.asList("Tissue", "Event", "Sample").contains(entity.conceptAlias)) {
                        continue;
                    }

                    for (Project.Field attribute : entity.attributes) {
                        allAttributes.put(attribute.uri, attribute.asDocumentField());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectionException("Unable to connect to GeOMe: " + e.getMessage());
        }
    }

    private Map<String, DocumentField> allAttributes = new LinkedMap();

    @Override
    public void disconnect() {

    }

    @Override
    public DocumentField getTissueSampleDocumentField() {
        return allAttributes.get("urn:tissueID");
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
        return new ArrayList<>(allAttributes.values());
    }

    @Override
    protected List<DocumentField> _getTaxonomyAttributes() {
        return Arrays.asList(
                allAttributes.get("urn:family"),
                allAttributes.get("urn:scientificName")
        );
    }

    @Override
    protected List<DocumentField> _getSearchAttributes() {
        return new ArrayList<>(allAttributes.values());
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
