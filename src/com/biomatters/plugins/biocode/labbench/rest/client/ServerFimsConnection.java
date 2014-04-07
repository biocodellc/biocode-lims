package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.server.QueryUtils;
import com.biomatters.plugins.biocode.server.XMLSerializableList;
import com.biomatters.plugins.biocode.server.XMLSerializableMessageReader;
import com.biomatters.plugins.biocode.server.XMLSerializableMessageWriter;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
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
        if(!(options instanceof RESTConnectionOptions)) {
            throw new IllegalArgumentException("Expected instance of " + RESTConnectionOptions.class.getSimpleName() + " but was " + options.getClass().getName());
        }
        RESTConnectionOptions connetionOptions = (RESTConnectionOptions) options;
        String host = connetionOptions.getHost();
        if(!host.matches("https?://.*")) {
            host = "http://" + host;
        }

        WebTarget server = ClientBuilder.newClient().
                register(XMLSerializableMessageReader.class).
                register(XMLSerializableMessageWriter.class).
                target(host).path("biocode");
        try {
            String serverVersion = server.path("info").path("version").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            if(!serverVersion.equals("0.1")) {
                throw new ConnectionException("Incompatible server version.  Expected 0.1 (alpha), was " + serverVersion);
            }
        } catch (WebApplicationException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
        target = server.path("fims");
    }

    @Override
    public void disconnect() {
        target = null;
    }

    @Override
    public DocumentField getTissueSampleDocumentField() {
        return getDocumentField("tissue");
    }

    private DocumentField getDocumentField(String type) {
        Invocation.Builder request = target.path("fields").path(type).request(MediaType.APPLICATION_XML_TYPE);
        return request.get(DocumentField.class);
    }


    @Override
    public DocumentField getLatitudeField() {
        return getDocumentField("latitude");
    }

    @Override
    public DocumentField getLongitudeField() {
        return getDocumentField("longitude");
    }

    @Override
    public DocumentField getPlateDocumentField() {
        return getDocumentField("plate");
    }

    @Override
    public DocumentField getWellDocumentField() {
        return getDocumentField("well");
    }

    @Override
    public List<DocumentField> getCollectionAttributes() {
        Invocation.Builder request = target.path("fields").queryParam("type", "collection").request(MediaType.APPLICATION_XML_TYPE);
        return request.get(new GenericType<XMLSerializableList<DocumentField>>(){}).getList();
    }

    @Override
    public List<DocumentField> getTaxonomyAttributes() {
        Invocation.Builder request = target.path("fields").queryParam("type", "taxonomy").request(MediaType.APPLICATION_XML_TYPE);
        return request.get(new GenericType<XMLSerializableList<DocumentField>>(){}).getList();
    }

    @Override
    public List<DocumentField> _getSearchAttributes() {
        Invocation.Builder request = target.path("fields").request(MediaType.APPLICATION_XML_TYPE);
        return request.get(new GenericType<XMLSerializableList<DocumentField>>(){}).getList();
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query) throws ConnectionException {
        QueryUtils.Query restQuery = QueryUtils.createRestQuery(query);
        Invocation.Builder request = target.path("samples/search").
                queryParam("query", restQuery.getQueryString()).
                queryParam("type", restQuery.getType()).
                request(MediaType.TEXT_PLAIN_TYPE);

        return Arrays.asList(request.get(String.class).split(","));
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        Invocation.Builder request = target.path("samples").queryParam("ids", StringUtilities.join(",", tissueIds)).request(MediaType.APPLICATION_XML_TYPE);
        return request.get(new GenericType<XMLSerializableList<FimsSample>>(){}).getList();
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        Invocation.Builder request = target.path("samples/count").request(MediaType.TEXT_PLAIN_TYPE);
        return request.get(Integer.class);
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
        Invocation.Builder request = target.path("property/" + name).request(MediaType.TEXT_PLAIN_TYPE);
        return request.get(Boolean.class);
    }

    @Override
    public boolean requiresMySql() {
        return false;
    }
}
