package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.server.*;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 11:14 AM
 */
public class ServerLimsConnetion extends LIMSConnection {

    private String username;
    WebTarget target;
    @Override
    protected void _connect(PasswordOptions options) throws ConnectionException {
        if(!(options instanceof RESTConnectionOptions)) {
            throw new IllegalArgumentException("Expected instance of " + RESTConnectionOptions.class.getSimpleName() + " but was " + options.getClass().getName());
        }
        RESTConnectionOptions connetionOptions = (RESTConnectionOptions) options;
        this.username = connetionOptions.getUsername();
        String host = connetionOptions.getHost();
        if(!host.matches("https?://.*")) {
            host = "http://" + host;
        }

        target = ClientBuilder.newClient().
                register(XMLSerializableMessageReader.class).
                register(XMLSerializableMessageWriter.class).
                target(host).path("biocode");
    }

    @Override
    public LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, RetrieveCallback callback, boolean downloadTissues) throws DatabaseServiceException {
        List<String> include = new ArrayList<String>();
        if(BiocodeService.isDownloadWorkflows(query)) {
            include.add("workflows");
        }
        if(BiocodeService.isDownloadPlates(query)) {
            include.add("plates");
        }
        if(BiocodeService.isDownloadSequences(query)) {
            include.add("sequences");
        }

        QueryUtils.Query restQuery = QueryUtils.createRestQuery(query);
        WebTarget target = this.target.path("search").queryParam("q", restQuery.getQueryString()).
                queryParam("type", restQuery.getType());
        if(!include.isEmpty()) {
            target = target.queryParam("include", StringUtilities.join(",", include));
        }

        System.out.println(target.getUri().toString());
        Invocation.Builder request = target.request(MediaType.APPLICATION_XML_TYPE);
        LimsSearchResult result = request.get(LimsSearchResult.class);
        if(BiocodeService.isDownloadPlates(query) && callback != null) {
            for (PlateDocument plateDocument : result.getPlates()) {
                callback.add(plateDocument, Collections.<String, Object>emptyMap());
            }
        }
        if(BiocodeService.isDownloadWorkflows(query) && callback != null) {
            for (WorkflowDocument workflow : result.getWorkflows()) {
                callback.add(workflow, Collections.<String, Object>emptyMap());
            }
        }
        return result;
    }

    @Override
    public void savePlate(Plate plate, ProgressListener progress) throws BadDataException, DatabaseServiceException {
        Invocation.Builder request = target.path("plates").request();
        Response response = request.put(Entity.entity(plate, MediaType.APPLICATION_XML_TYPE));
        System.out.println(response);
    }

    @Override
    public List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws DatabaseServiceException {
        return target.path("plates").path("empty").request(MediaType.APPLICATION_XML_TYPE).get(
                new GenericType<XMLSerializableList<Plate>>(){}
        ).getList();
    }

    @Override
    public void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException {

        Invocation.Builder request = target.path("plates").path("reactions").
                queryParam("reactions", new XMLSerializableList<Reaction>(Reaction.class, Arrays.asList(reactions))).
                queryParam("type", type.name()).
                request();
        Response response = request.get();
        System.out.println(response);
    }

    @Override
    public Set<Integer> deletePlate(Plate plate, ProgressListener progress) throws DatabaseServiceException {
        Invocation.Builder request = target.path("plates").path("delete").request(MediaType.TEXT_PLAIN_TYPE);
        Response response = request.post(Entity.entity(plate, MediaType.APPLICATION_XML_TYPE));
        String result = response.getEntity().toString();
        if(result == null) {
            return Collections.emptySet();
        } else {
            HashSet<Integer> set = new HashSet<Integer>();
            for (String idString : result.split("\\n")) {
                try {
                    set.add(Integer.parseInt(idString));
                } catch (NumberFormatException e) {
                    throw new DatabaseServiceException("Server returned bad plate ID: " + idString + " is not a number", false);
                }
            }
            return set;
        }
    }

    @Override
    public void renamePlate(int id, String newName) throws DatabaseServiceException {
        Invocation.Builder request = target.path("plates").path(id + "/name").
                queryParam("name", newName).
                request();
        Response response = request.get();
        System.out.println(response);
    }

    @Override
    public List<FailureReason> getPossibleFailureReasons() {
        return target.path("failureReasons").request(MediaType.APPLICATION_XML_TYPE).get(
                new GenericType<List<FailureReason>>() { });
    }

    @Override
    public boolean deleteAllowed(String tableName) {
        return target.path("permissions").path("delete").path(tableName).request(MediaType.TEXT_PLAIN_TYPE).get(Boolean.class);
    }

    @Override
    public List<AssembledSequence> getAssemblyDocuments(List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws DatabaseServiceException {
        return target.path("sequences").
                queryParam("includeFailed", includeFailed).
                queryParam("ids", StringUtilities.join(",", sequenceIds)).
                request(MediaType.APPLICATION_XML_TYPE).get(
                new GenericType<List<AssembledSequence>>(){}
        );
    }

    @Override
    public int addAssembly(boolean isPass, String notes, String technician, FailureReason failureReason, String failureNotes, boolean addChromatograms, AssembledSequence seq, List<Integer> reactionIds, Cancelable cancelable) throws DatabaseServiceException {
        Response response = target.path("sequences").
                queryParam("isPass", isPass).
                queryParam("notes", notes).
                queryParam("technician", technician).
                queryParam("failureReason", failureReason).
                queryParam("addChromatograms", addChromatograms).
                queryParam("reactionIds", StringUtilities.join(",", reactionIds)).request(MediaType.TEXT_PLAIN_TYPE).
                post(Entity.entity(seq, MediaType.APPLICATION_XML_TYPE));
        Object entity = response.getEntity();
        if(entity instanceof Integer) {
            return (Integer)entity;
        } else {
            throw new DatabaseServiceException("Bad result from server, expected sequence ID, received " + entity, false);
        }
    }

    @Override
    public void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException {
        target.path("sequences").path("deletes").request().post(Entity.entity(
                StringUtilities.join(",", sequencesToDelete), MediaType.TEXT_PLAIN_TYPE));
    }

    @Override
    public void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException {
        for (Integer id : ids) {
            target.path("sequences").path(""+id).path("submitted").request().put(Entity.entity(submitted, MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @Override
    public void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException {
        target.path("workflows").path(""+workflowId).path("sequences").path(extractionId).request().delete();
    }

    @Override
    public Map<String, String> getTissueIdsForExtractionIds(String tableName, List<String> extractionIds) throws DatabaseServiceException {
        return target.path("plates").path("tissues").
                        queryParam("type", tableName).
                        queryParam("extractionIds", StringUtilities.join(",", extractionIds)).
                        request(MediaType.APPLICATION_XML_TYPE).
                        get(StringMap.class).getMap();
    }

    @Override
    public List<ExtractionReaction> getExtractionsForIds(List<String> extractionIds) throws DatabaseServiceException {
        return target.path("plates").path("extractions").
                queryParam("ids", StringUtilities.join(",", extractionIds)).
                request(MediaType.APPLICATION_XML_TYPE).
                get(new GenericType<XMLSerializableList<ExtractionReaction>>() {
                }).getList();
    }

    @Override
    public Map<Integer, List<MemoryFile>> downloadTraces(List<Integer> reactionIds, ProgressListener progressListener) throws DatabaseServiceException {
        Map<Integer, List<MemoryFile>> result = new HashMap<Integer, List<MemoryFile>>();
        for (int reactionId : reactionIds) {
            List<MemoryFile> memoryFiles = target.path("reactions").path(""+reactionId).path("traces").
                    request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<List<MemoryFile>>() {
                    }
            );
            if(memoryFiles != null) {
                result.put(reactionId, memoryFiles);
            }
        }
        return result;
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return null;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void disconnect() {
        target = null;
    }

    @Override
    public void doAnyExtraInitialziation() throws DatabaseServiceException {
        // Nothing required on the client side
    }

    @Override
    public Set<String> getAllExtractionIdsForTissueIds(List<String> tissueIds) throws DatabaseServiceException {
        return new HashSet<String>(Arrays.asList(
                target.path("tissues").path("extractions").queryParam("tissues",
                        StringUtilities.join(",",tissueIds)).request(MediaType.TEXT_PLAIN_TYPE).get(String.class).split("\\n")
        ));
    }

    @Override
    public List<ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws DatabaseServiceException {
        return target.path("extractions").
                        queryParam("barcodes", StringUtilities.join(",", barcodes)).
                        request(MediaType.APPLICATION_XML_TYPE).
                        get(new GenericType<XMLSerializableList<ExtractionReaction>>() {
                        }).getList();
    }

    @Override
    public Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws DatabaseServiceException {
        Map<Integer, List<GelImage>> images = new HashMap<Integer, List<GelImage>>();
        for (Integer plateId : plateIds) {
            List<GelImage> gelImages = target.path("plates").path(String.valueOf(plateId)).path("gels").request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<List<GelImage>>() {
                    }
            );
            images.put(plateId, gelImages);
        }
        return images;
    }

    @Override
    public void setProperty(String key, String value) throws DatabaseServiceException {
        target.path("info").path("properties").path(key).request().put(Entity.entity(value, MediaType.TEXT_PLAIN_TYPE));
    }

    @Override
    public String getProperty(String key) throws DatabaseServiceException {
        return target.path("info").path("properties").path(key).request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
    }

    private static final String WORKFLOWS = "workflows";
    @Override
    public List<Workflow> getWorkflows(Collection<String> workflowIds) throws DatabaseServiceException {
        return target.path(WORKFLOWS).queryParam("ids", StringUtilities.join(",", workflowIds)).
                request(MediaType.APPLICATION_XML_TYPE).get(new GenericType<XMLSerializableList<Workflow>>(){}).getList();
    }

    @Override
    public Map<String, String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException {
        return target.path("extractions").path(WORKFLOWS).
                queryParam("extractionIds", StringUtilities.join(",", idsToCheck)).
                queryParam("loci", StringUtilities.join(",", loci)).
                queryParam("type", reactionType.name()).
                        request(MediaType.APPLICATION_XML_TYPE).get(StringMap.class).getMap();
    }

    @Override
    public void renameWorkflow(int id, String newName) throws DatabaseServiceException {
        Invocation.Builder request = target.path(WORKFLOWS).path(id + "/name").
                queryParam("name", newName).
                request();
        Response response = request.get();
        System.out.println(response);
    }

    @Override
    public void testConnection() throws DatabaseServiceException {
        try {
            target.path("info").path("details").request(MediaType.TEXT_PLAIN_TYPE).get();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static final String COCKTAILS = "cocktails";

    @Override
    public Collection<String> getPlatesUsingCocktail(Reaction.Type type, int cocktailId) throws DatabaseServiceException {
        String platesList = target.path(COCKTAILS).path(type.name()).path("" + cocktailId).path("plates").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
        return Arrays.asList(platesList.split("\\n"));
    }

    @Override
    public void addCocktails(List<? extends Cocktail> cocktails) throws DatabaseServiceException {
        target.path(COCKTAILS).request().put(Entity.entity(
                new XMLSerializableList<Cocktail>(Cocktail.class, new ArrayList<Cocktail>(cocktails)),
                MediaType.APPLICATION_XML_TYPE));
    }

    @Override
    public void deleteCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {
        target.path(COCKTAILS).path("delete").request().post(Entity.entity(
                new XMLSerializableList<Cocktail>(Cocktail.class, new ArrayList<Cocktail>(deletedCocktails)),
                MediaType.APPLICATION_XML_TYPE));
    }

    @Override
    public List<PCRCocktail> getPCRCocktailsFromDatabase() throws DatabaseServiceException {
        return target.path(COCKTAILS).path(Cocktail.Type.pcr.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                new GenericType<XMLSerializableList<PCRCocktail>>(){}
        ).getList();
    }

    @Override
    public List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws DatabaseServiceException {
        return target.path(COCKTAILS).path(Cocktail.Type.cyclesequencing.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                        new GenericType<XMLSerializableList<CycleSequencingCocktail>>(){}
                ).getList();
    }

    private static final String THERMOCYCLES = "thermocycles";

    @Override
    public List<Thermocycle> getThermocyclesFromDatabase(Thermocycle.Type type) throws DatabaseServiceException {
        return target.path(THERMOCYCLES).path(type.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                new GenericType<XMLSerializableList<Thermocycle>>(){}
        ).getList();
    }

    @Override
    public void addThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        target.path(THERMOCYCLES).path(type.name()).request().post(Entity.entity(
                new XMLSerializableList<Thermocycle>(Thermocycle.class, cycles), MediaType.APPLICATION_XML_TYPE));
    }

    @Override
    public void deleteThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        target.path(THERMOCYCLES).path(type.name()).path("delete").request().post(Entity.entity(
                        new XMLSerializableList<Thermocycle>(Thermocycle.class, cycles), MediaType.APPLICATION_XML_TYPE));
    }

    @Override
    public List<String> getPlatesUsingThermocycle(int thermocycleId) throws DatabaseServiceException {
        String result = target.path(THERMOCYCLES).path("" + thermocycleId).path("plates").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
        if(result == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(result.split("\\n"));
        }
    }

    @Override
    public boolean supportReporting() {
        return false;
    }

    @Override
    protected Connection getConnectionInternal() throws SQLException {
        throw new UnsupportedOperationException("Does not support getting a SQL Connection");
    }
}
