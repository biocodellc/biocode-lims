package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.BCIDRoot;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.server.RestQueryUtils;
import com.biomatters.plugins.biocode.server.StringMap;
import com.biomatters.plugins.biocode.server.XMLSerializableList;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
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
 *          <p/>
 *          Created on 4/04/14 11:14 AM
 */
public class ServerLimsConnection extends LIMSConnection {
    private String username;
    WebTarget target;
    private static Map<String, String> BCIDRoots = new HashMap<String, String>();

    @Override
    protected void _connect(PasswordOptions options) throws ConnectionException {
        LimsConnectionOptions allLimsOptions = (LimsConnectionOptions)options;
        PasswordOptions selectedLimsOptions = allLimsOptions.getSelectedLIMSOptions();

        if (!(selectedLimsOptions instanceof RESTConnectionOptions)) {
            throw new IllegalArgumentException("Expected instance of " + RESTConnectionOptions.class.getSimpleName() + " but was " + selectedLimsOptions.getClass().getName());
        }

        RESTConnectionOptions connectionOptions = (RESTConnectionOptions)selectedLimsOptions;
        this.username = connectionOptions.getUsername();
        String host = connectionOptions.getHost();
        if (!host.matches("https?://.*")) {
            host = "http://" + host;
        }

        target = RestQueryUtils.getBiocodeWebTarget(host, connectionOptions.getUsername(), connectionOptions.getPassword(), requestTimeout);
        try {
            testConnection();
        } catch (DatabaseServiceException e) {
            throw new ConnectionException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, Cancelable cancelable) throws DatabaseServiceException {
        updateBCIDRoots();

        String tissueIdsToMatchString = tissueIdsToMatch == null ? null : StringUtilities.join(",", tissueIdsToMatch);

        boolean downloadWorkflows = BiocodeService.isDownloadWorkflows(query);
        boolean downloadPlates = BiocodeService.isDownloadPlates(query);
        try {
            WebTarget target = this.target.path("search")
                    .queryParam("q", RestQueryUtils.geneiousQueryToRestQueryString(query))
                    .queryParam("matchTissues", tissueIdsToMatch != null)
                    .queryParam("showTissues", BiocodeService.isDownloadTissues(query))
                    .queryParam("showWorkflows", downloadWorkflows)
                    .queryParam("showPlates", downloadPlates)
                    .queryParam("showSequences", BiocodeService.isDownloadSequences(query));
            Invocation.Builder request = target.request(MediaType.APPLICATION_XML_TYPE);
            return request.post(Entity.entity(tissueIdsToMatchString, MediaType.TEXT_PLAIN_TYPE), LimsSearchResult.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<WorkflowDocument> getWorkflowsById_(Collection<Integer> workflowIds, Cancelable cancelable) throws DatabaseServiceException {
        if(workflowIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Invocation.Builder request = target.path("workflows")
                    .queryParam("ids", StringUtilities.join(",", workflowIds))
                    .request(MediaType.APPLICATION_XML_TYPE);
            return request.get(
                    new GenericType<XMLSerializableList<WorkflowDocument>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<Plate> getPlates_(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException {
        if(plateIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Invocation.Builder request = target.path("plates")
                    .queryParam("ids", StringUtilities.join(",", plateIds))
                    .request(MediaType.APPLICATION_XML_TYPE);
            return request.get(
                    new GenericType<XMLSerializableList<Plate>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void savePlates(List<Plate> plates, ProgressListener progress) throws BadDataException, DatabaseServiceException {
        try {
            Invocation.Builder request = target.path("plates").request();
            Response response = request.put(Entity.entity(new XMLSerializableList<Plate>(Plate.class, plates), MediaType.APPLICATION_XML_TYPE));
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
                Dialogs.showMessageDialog("Could not add plate: " + response.readEntity(String.class));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws DatabaseServiceException {
        try {
            return target.path("plates").path("empty").request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<XMLSerializableList<Plate>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path("plates").path("reactions").queryParam("type", type.name()).request();
            request.put(Entity.entity(
                    new XMLSerializableList<Reaction>(Reaction.class, Arrays.asList(reactions)),
                    MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Set<Integer> deletePlates(List<Plate> plates, ProgressListener progress) throws DatabaseServiceException {
        String result;
        try {
            Invocation.Builder request = target.path("plates").path("delete").request(MediaType.TEXT_PLAIN_TYPE);
            Response response = request.post(Entity.entity(new XMLSerializableList<Plate>(Plate.class, plates), MediaType.APPLICATION_XML_TYPE));
            result = response.readEntity(String.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }

        if (result == null || result.isEmpty()) {
            return Collections.emptySet();
        } else {
            HashSet<Integer> set = new HashSet<Integer>();
            for (String idString : result.split("\\n")) {
                try {
                    set.add(Integer.parseInt(idString));
                } catch (NumberFormatException e) {
                    throw new DatabaseServiceException("Server returned bad plate IDs: " + result, false);
                }
            }
            return set;
        }
    }

    @Override
    public void renamePlate(int id, String newName) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path("plates").path(id + "/name").request();
            Response response = request.put(Entity.entity(newName, MediaType.TEXT_PLAIN_TYPE));
            response.close();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<FailureReason> getPossibleFailureReasons() {
        try {
            return target.path("failureReasons").request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<List<FailureReason>>() {
                    }
            );
        } catch (WebApplicationException e) {
            return Collections.emptyList();
        } catch (ProcessingException e) {
            // todo Handle this better. Perhaps cache on start up and have an updating thread
            return Collections.emptyList();
        }
    }

    @Override
    public boolean deleteAllowed(String tableName) {
        try {
            return target.path("permissions").path("delete").path(tableName).request(MediaType.TEXT_PLAIN_TYPE).get(Boolean.class);
        } catch (WebApplicationException e) {
            return false;
        } catch (ProcessingException e) {
            return false;
        }
    }

    @Override
    public List<AssembledSequence> getAssemblySequences_(Collection<Integer> sequenceIds, Cancelable cancelable, boolean includeFailed) throws DatabaseServiceException {
        try {
            return target.path("sequences").
                    queryParam("includeFailed", includeFailed).
                    queryParam("ids", StringUtilities.join(",", sequenceIds)).
                    request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<List<AssembledSequence>>() {
                    }
            );
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void setAssemblySequences(Map<Integer, String> assemblyIDToAssemblySequenceToSet, ProgressListener progressListener) throws DatabaseServiceException {
        Map<String, String> assemblyIDAsStringToAssemblySequenceToSet = new HashMap<String, String>();

        for (Map.Entry<Integer, String> assemblyIDAndAssemblySequenceToSet : assemblyIDToAssemblySequenceToSet.entrySet()) {
            assemblyIDAsStringToAssemblySequenceToSet.put(String.valueOf(assemblyIDAndAssemblySequenceToSet.getKey()), assemblyIDAndAssemblySequenceToSet.getValue());
        }
        try {
            target.path("sequences").path("update").request().put(Entity.entity(new StringMap(assemblyIDAsStringToAssemblySequenceToSet), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public int addAssembly(String isPass, String notes, String technician, FailureReason failureReason, String failureNotes, boolean addChromatograms, AssembledSequence seq, List<Integer> reactionIds, Cancelable cancelable) throws DatabaseServiceException {
        //not sure if this need batch
        try {
            WebTarget resource = target.path("sequences").
                    queryParam("isPass", isPass).
                    queryParam("notes", notes).
                    queryParam("technician", technician).
                    queryParam("failureReason", failureReason != null ? failureReason.getId() : null).
                    queryParam("addChromatograms", addChromatograms).
                    queryParam("reactionIds", StringUtilities.join(",", reactionIds));
            System.out.println(resource.getUri());
            Response response = resource.request(MediaType.TEXT_PLAIN_TYPE).
                    post(Entity.entity(seq, MediaType.APPLICATION_XML_TYPE));
            return response.readEntity(Integer.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException {
        try {
            for (int id : sequencesToDelete) {
                target.path("sequences").path(Integer.toString(id)).request().delete();
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException {
        try {
            for (Integer id : ids) {
                target.path("sequences").path("" + id).path("submitted").request().put(Entity.entity(submitted, MediaType.TEXT_PLAIN_TYPE));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException {
        try {
            target.path("workflows").path("" + workflowId).path("sequences").path(extractionId).request().delete();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<String, String> getTissueIdsForExtractionIds_(String tableName, List<String> extractionIds) throws DatabaseServiceException {
        try {
            return target.path("plates").path("tissues").
                    queryParam("type", tableName).
                    queryParam("extractionIds", StringUtilities.join(",", extractionIds)).
                    request(MediaType.APPLICATION_XML_TYPE).
                    get(StringMap.class).getMap();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<ExtractionReaction> getExtractionsForIds_(List<String> extractionIds) throws DatabaseServiceException {
        try {
            return target.path("plates").path("extractions").
                    queryParam("ids", StringUtilities.join(",", extractionIds)).
                    request(MediaType.APPLICATION_XML_TYPE).
                    get(new GenericType<XMLSerializableList<ExtractionReaction>>() {
                    }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<Integer, List<MemoryFile>> downloadTraces(List<Integer> reactionIds, ProgressListener progressListener) throws DatabaseServiceException {
        try {
            Map<Integer, List<MemoryFile>> result = new HashMap<Integer, List<MemoryFile>>();
            for (int reactionId : reactionIds) {
                List<MemoryFile> memoryFiles;
                try {
                    Response response = target.path("reactions").path("" + reactionId).path("traces").
                            request(MediaType.APPLICATION_XML_TYPE).get();
                    memoryFiles = getListFromResponse(response, new GenericType<List<MemoryFile>>() { });
                } catch (NotFoundException e) {
                    continue;
                }
                if (memoryFiles != null) {
                    result.put(reactionId, memoryFiles);
                }
            }
            return result;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new RESTConnectionOptions();
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
    public void doAnyExtraInitialization(ProgressListener progressListener) throws DatabaseServiceException {
        // Nothing required on the client side
    }

    @Override
    public Set<String> getAllExtractionIdsForTissueIds_(List<String> tissueIds) throws DatabaseServiceException {
        try {
            final Set<String> ret = new HashSet<String>();
            ret.addAll(Arrays.asList(
                    target.path("tissues").path("extractions").queryParam("tissues",
                            StringUtilities.join(",", tissueIds)).request(MediaType.TEXT_PLAIN_TYPE).get(String.class).split("\\n")
            ));
            return ret;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }

    }

    @Override
    public List<ExtractionReaction> getExtractionsFromBarcodes_(List<String> barcodes) throws DatabaseServiceException {
        try {
            return target.path("extractions").
                    queryParam("barcodes", StringUtilities.join(",", barcodes)).
                    request(MediaType.APPLICATION_XML_TYPE).
                    get(new GenericType<XMLSerializableList<ExtractionReaction>>() {
                    }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws DatabaseServiceException {
        try {
            Map<Integer, List<GelImage>> images = new HashMap<Integer, List<GelImage>>();
            for (Integer plateId : plateIds) {
                Response response = target.path("plates").path(String.valueOf(plateId)).path("gels").
                        request(MediaType.APPLICATION_XML_TYPE).get();
                if(response.getStatus() == 204) {
                    return Collections.emptyMap();
                }
                images.put(plateId, getListFromResponse(response, new GenericType<List<GelImage>>() { }));
            }
            return images;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void setProperty(String key, String value) throws DatabaseServiceException {
        try {
            target.path("info").path("properties").path(key).request().put(Entity.entity(value, MediaType.TEXT_PLAIN_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public String getProperty(String key) throws DatabaseServiceException {
        try {
            return target.path("info").path("properties").path(key).request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static final String WORKFLOWS = "workflows";

    @Override
    public List<Workflow> getWorkflowsByName(Collection<String> workflowNames) throws DatabaseServiceException {
        if(workflowNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Workflow> data = new ArrayList<Workflow>();
        try {
            for (String id : workflowNames) {
                if (id.isEmpty()) {
                    continue;
                }
                data.add(target.path(WORKFLOWS).path(id).request(MediaType.APPLICATION_XML_TYPE).get(Workflow.class));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
        return data;
    }

    @Override
    public Map<String, String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException {
        try {
            return target.path("extractions").path(WORKFLOWS).
                    queryParam("extractionIds", StringUtilities.join(",", idsToCheck)).
                    queryParam("loci", StringUtilities.join(",", loci)).
                    queryParam("type", reactionType.name()).
                    request(MediaType.APPLICATION_XML_TYPE).get(StringMap.class).getMap();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void renameWorkflow(int id, String newName) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path(WORKFLOWS).path(id + "/name").request();
            request.put(Entity.entity(newName, MediaType.TEXT_PLAIN_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void testConnection() throws DatabaseServiceException {
        try {
            target.path("info").path("details").request(MediaType.TEXT_PLAIN_TYPE).get();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static final String COCKTAILS = "cocktails";

    @Override
    public Collection<String> getPlatesUsingCocktail(Reaction.Type type, int cocktailId) throws DatabaseServiceException {
        try {
            String platesList = target.path(COCKTAILS).path(type.name()).path("" + cocktailId).path("plates").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            return Arrays.asList(platesList.split("\\n"));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void addCocktails(List<? extends Cocktail> cocktails) throws DatabaseServiceException {
        try {
            target.path(COCKTAILS).request().put(Entity.entity(
                    new XMLSerializableList<Cocktail>(Cocktail.class, new ArrayList<Cocktail>(cocktails)),
                    MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {
        try {
            target.path(COCKTAILS).path("delete").request().post(Entity.entity(
                    new XMLSerializableList<Cocktail>(Cocktail.class, new ArrayList<Cocktail>(deletedCocktails)),
                    MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<PCRCocktail> getPCRCocktailsFromDatabase() throws DatabaseServiceException {
        try {
            return target.path(COCKTAILS).path(Cocktail.Type.pcr.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<XMLSerializableList<PCRCocktail>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws DatabaseServiceException {
        try {
            return target.path(COCKTAILS).path(Cocktail.Type.cyclesequencing.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<XMLSerializableList<CycleSequencingCocktail>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static final String THERMOCYCLES = "thermocycles";

    @Override
    public List<Thermocycle> getThermocyclesFromDatabase(Thermocycle.Type type) throws DatabaseServiceException {
        try {
            return target.path(THERMOCYCLES).path(type.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<XMLSerializableList<Thermocycle>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static final String BCIDROOTS = "bcid-roots";

    private void updateBCIDRoots() throws DatabaseServiceException {
        try {
            List<BCIDRoot> BCIDRootsList = target.path(BCIDROOTS).request(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<BCIDRoot>>() {});
            BCIDRoots.clear();
            for (BCIDRoot val : BCIDRootsList) {
                BCIDRoots.put(val.type, val.value);
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public Map<String, String> getBCIDRoots() { return BCIDRoots; }

    @Override
    public void addThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        try {
            target.path(THERMOCYCLES).path(type.name()).request().post(Entity.entity(
                    new XMLSerializableList<Thermocycle>(Thermocycle.class, cycles), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        try {
            target.path(THERMOCYCLES).path(type.name()).path("delete").request().post(Entity.entity(
                    new XMLSerializableList<Thermocycle>(Thermocycle.class, cycles), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<String> getPlatesUsingThermocycle(int thermocycleId) throws DatabaseServiceException {
        try {
            String result = target.path(THERMOCYCLES).path("" + thermocycleId).path("plates").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            if (result == null) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(result.split("\\n"));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
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

//    @Override
//    public Condition[] getFieldConditions(Class fieldClass) {
//        List<Condition> valid = new ArrayList<Condition>();
//        for (Condition condition : super.getFieldConditions(fieldClass)) {
//            if(RestQueryUtils.supportsConditionForRestQuery(condition)) {
//                valid.add(condition);
//            }
//        }
//        return valid.toArray(new Condition[valid.size()]);
//    }

    public static <T> List<T> getListFromResponse(Response response, GenericType<List<T>> type) {
        if(response.getStatus() == 204) {  // HTTP 204 is No Content
            return Collections.emptyList();
        }
        return response.readEntity(type);
    }
}
