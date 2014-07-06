package com.biomatters.plugins.biocode.labbench.rest.client;

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
import com.biomatters.plugins.biocode.server.XMLSerializableMessageReader;
import com.biomatters.plugins.biocode.server.XMLSerializableMessageWriter;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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


    @Override
    public void _connect(Options options) throws ConnectionException {
        if (!(options instanceof RESTConnectionOptions)) {
            throw new IllegalArgumentException("Expected instance of " + RESTConnectionOptions.class.getSimpleName() + " but was " + options.getClass().getName());
        }
        RESTConnectionOptions connetionOptions = (RESTConnectionOptions) options;
        String host = connetionOptions.getHost();
        if (!host.matches("https?://.*")) {
            host = "http://" + host;
        }

        HttpAuthenticationFeature authFeature = HttpAuthenticationFeature.universal(
                connetionOptions.getUsername(),
                connetionOptions.getPassword());
        WebTarget server = ClientBuilder.newClient().
                register(authFeature).
                register(XMLSerializableMessageReader.class).
                register(XMLSerializableMessageWriter.class).
                target(host).path("biocode");
        try {
            String serverVersion = server.path("info").path("version").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            String expectedVersion = "0.2";
            if (!serverVersion.equals(expectedVersion)) {
                throw new ConnectionException("Incompatible server version.  Expected " + expectedVersion + " (alpha), was " + serverVersion);
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
    public List<DocumentField> getCollectionAttributes() {
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
    public List<DocumentField> getTaxonomyAttributes() {
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
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        if (tissueIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Invocation.Builder request = target.path("samples").queryParam("ids",
                    StringUtilities.join(",", tissueIds)).request(MediaType.APPLICATION_XML_TYPE);
            return request.get(new GenericType<XMLSerializableList<FimsSample>>() {
            }).getList();
        } catch (WebApplicationException e) {
            throw new ConnectionException(e.getMessage(), e);
        } catch (ProcessingException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
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
    public List<String> getProjectsForSamples(Collection<FimsSample> samples) {
        throw new UnsupportedOperationException("This method should only be used in the Biocode Server");
    }
}
