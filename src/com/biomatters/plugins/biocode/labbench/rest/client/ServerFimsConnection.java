package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.server.RestQueryUtils;
import com.biomatters.plugins.biocode.server.XMLSerializableList;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 1/04/14 11:55 AM
 */
public class ServerFimsConnection extends FIMSConnection {

    WebTarget target;

    @Override
    public String getLabel() {
        return "Server FIMS";
    }

    @Override
    public String getName() {
        return "REST Server FIMS";
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return null;
    }

    /**
     * For pre release versions of the server we will require exact version matching until we have finalized the API.
     * At that point we can use a more flexible system.  ie Major version, minor version.
     */
    private static final String EXPECTED_VERSION = "0.6";  // Update this when updating Info.version()

    @Override
    public void _connect(Options options) throws ConnectionException {
        if (!(options instanceof RESTConnectionOptions)) {
            throw new IllegalArgumentException("Expected instance of " + RESTConnectionOptions.class.getSimpleName() + " but was " + options.getClass().getName());
        }
        RESTConnectionOptions connetionOptions = (RESTConnectionOptions) options;
        String host = connetionOptions.getHost();
        if (host == null || host.trim().length() == 0) {
            Dialogs.showMessageDialog("Host can not be empty");
            return;
        }

        if (!host.matches("https?://.*")) {
            host = "http://" + host;
        }

        WebTarget server = RestQueryUtils.getBiocodeWebTarget(host, connetionOptions.getUsername(), connetionOptions.getPassword(), requestTimeoutInSeconds);
        try {
            String serverVersion = server.path("info").path("version").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            if (!serverVersion.equals(EXPECTED_VERSION)) {
                throw new ConnectionException("Incompatible server version.  Expected " + EXPECTED_VERSION + " (alpha), was " + serverVersion);
            }
            target = server.path("fims");
            tissueField = getDocumentField("tissue");
            latitudeField = getDocumentField("latitude");
            longitudeField = getDocumentField("longitude");
            plateField = getDocumentField("plate");
            wellField = getDocumentField("well");

            collectionFields = getDocumentFieldListForType("collection");
            taxonFields = getDocumentFieldListForType("taxonomy");
            searchFields = getDocumentFieldListForType(null);
        } catch (WebApplicationException e) {
            throw new ConnectionException(e.getMessage(), e);
        } catch (ProcessingException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void disconnect() {
        target = null;
    }

    private DocumentField tissueField;
    private DocumentField latitudeField;
    private DocumentField longitudeField;
    private DocumentField plateField;
    private DocumentField wellField;

    private List<DocumentField> collectionFields;
    private List<DocumentField> taxonFields;
    private List<DocumentField> searchFields;


    @Override
    public DocumentField getTissueSampleDocumentField() {
        return tissueField;
    }

    private DocumentField getDocumentField(String type) {
        try {
            Invocation.Builder request = target.path("fields").path(type).request(MediaType.APPLICATION_XML_TYPE);
            return request.get(DocumentField.class);
        } catch (NotFoundException e) {
            return null;
        }
    }


    @Override
    public DocumentField getLatitudeField() {
        return latitudeField;
    }

    @Override
    public DocumentField getLongitudeField() {
        return longitudeField;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        return plateField;
    }

    @Override
    public DocumentField getWellDocumentField() {
        return wellField;
    }

    @Override
    public List<DocumentField> _getCollectionAttributes() {
        return collectionFields;
    }

    private List<DocumentField> getDocumentFieldListForType(String type) {
        WebTarget fieldsTarget = target.path("fields");
        if (type != null) {
            fieldsTarget = fieldsTarget.queryParam("type", type);
        }
        Invocation.Builder request = fieldsTarget.request(MediaType.APPLICATION_XML_TYPE);
        return request.get(new GenericType<XMLSerializableList<DocumentField>>() {
        }).getList();
    }

    @Override
    public List<DocumentField> _getTaxonomyAttributes() {
        return taxonFields;
    }

    @Override
    public List<DocumentField> _getSearchAttributes() {
        return searchFields;
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        try {
            RestQueryUtils.Query restQuery = RestQueryUtils.createRestQuery(query);
            Invocation.Builder request = target.path("samples/search").
                    queryParam("query", restQuery.getQueryString()).
                    queryParam("type", restQuery.getType()).
                    request(MediaType.TEXT_PLAIN_TYPE);

            String result = request.get(String.class);
            if (result == null || result.trim().isEmpty() || result.contains("<html>")) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(result.split(","));
            }
        } catch (WebApplicationException e) {
            throw new ConnectionException(e.getMessage(), e);
        } catch (ProcessingException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmptyQuery) throws ConnectionException {
        return getTissueIdsMatchingQuery(query, projectsToMatch);
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        if (tissueIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<FimsSample> result = new ArrayList<FimsSample>();
        int index = 0;
        int batchSize = 200;
        for(;index<tissueIds.size(); index+=batchSize) {
            int last = index + batchSize;
            List<String> toFetch = tissueIds.subList(index, Math.min(last, tissueIds.size()));
            try {
                Invocation.Builder request = target.path("samples").queryParam("ids",
                        StringUtilities.join(",", toFetch)).request(MediaType.APPLICATION_XML_TYPE);
                result.addAll(
                        request.get(new GenericType<XMLSerializableList<FimsSample>>() {
                        }).getList()
                );
            } catch (WebApplicationException e) {
                throw new ConnectionException(e.getMessage(), e);
            } catch (ProcessingException e) {
                throw new ConnectionException(e.getMessage(), e);
            }
        }
        return result;
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        try {
            Invocation.Builder request = target.path("samples/count").request(MediaType.TEXT_PLAIN_TYPE);
            return request.get(Integer.class);
        } catch (WebApplicationException e) {
            throw new ConnectionException(e.getMessage(), e);
        } catch (ProcessingException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public static final String HAS_PLATE_INFO = "hasPlateAndWell";
    public static final String HAS_PHOTOS = "hasPhotos";

    @Override
    public boolean storesPlateAndWellInformation() {
        return getProperty(HAS_PLATE_INFO);
    }

    @Override
    public boolean hasPhotos() {
        return getProperty(HAS_PHOTOS);
    }

    private boolean getProperty(String name) {
        try {
            Invocation.Builder request = target.path("property/" + name).request(MediaType.TEXT_PLAIN_TYPE);
            return request.get(Boolean.class);
        } catch (WebApplicationException e) {
            // todo
            return false;
        } catch (ProcessingException e) {
            // todo
            return false;  // Can't do anything since we don't support throwing exception from parent method
        }
    }

    @Override
    public List<FimsProject> getProjects() throws DatabaseServiceException {
        throw new UnsupportedOperationException("This method should only be used in the Biocode Server");
    }

    @Override
    public Map<String, Collection<FimsSample>> getProjectsForSamples(Collection<FimsSample> samples) {
        throw new UnsupportedOperationException("This method should only be used in the Biocode Server");
    }
}
