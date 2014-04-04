package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOperation;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOptions;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.server.QueryUtils;
import com.biomatters.plugins.biocode.server.XMLSerializableMessageReader;
import com.biomatters.plugins.biocode.server.XMLSerializableMessageWriter;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
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
    public Map<URN, String> addAssembly(AddAssemblyResultsToLimsOptions options, CompositeProgressListener progress, Map<URN, AddAssemblyResultsToLimsOperation.AssemblyResult> assemblyResults, boolean isPass) throws DatabaseServiceException {
        return Collections.emptyMap();
    }

    @Override
    public void saveReactions(Plate plate, ProgressListener progress) throws BadDataException, DatabaseServiceException {

    }

    @Override
    public void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException {

    }

    @Override
    public void createOrUpdatePlate(Plate plate, ProgressListener progress) throws DatabaseServiceException {

    }

    @Override
    public void renamePlate(int id, String newName) throws DatabaseServiceException {

    }

    @Override
    public void isPlateValid(Plate plate) throws DatabaseServiceException {

    }

    @Override
    public List<FailureReason> getPossibleFailureReasons() {
        return Collections.emptyList();
    }

    @Override
    public boolean deleteAllowed(String tableName) {
        return false;
    }

    @Override
    public void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException {

    }

    @Override
    public void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException {

    }

    @Override
    public Map<String, String> getReactionToTissueIdMapping(String tableName, List<? extends Reaction> reactions) throws DatabaseServiceException {
        return null;
    }

    @Override
    public Map<Integer, List<ReactionUtilities.MemoryFile>> downloadTraces(List<String> reactionIds, ProgressListener progressListener) throws DatabaseServiceException {
        return null;
    }

    @Override
    public List<ExtractionReaction> getExtractionsForIds(List<String> extractionIds) throws DatabaseServiceException {
        return null;
    }

    @Override
    public void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException {

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

    }

    @Override
    public Set<Integer> deleteRecords(String tableName, String term, Iterable ids) throws DatabaseServiceException {
        return Collections.emptySet();
    }

    @Override
    protected Connection getConnectionInternal() throws SQLException {
        throw new UnsupportedOperationException("Does not support getting a SQL Connection");
    }

    @Override
    public List<AnnotatedPluginDocument> getMatchingAssemblyDocumentsForIds(Collection<WorkflowDocument> workflows, List<FimsSample> samples, List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Reaction> getExtractionReactions(List<Reaction> sourceReactions) throws DatabaseServiceException {
        return null;
    }

    @Override
    protected Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws DatabaseServiceException {
        return Collections.emptyMap();
    }

    @Override
    public Set<String> getAllExtractionIdsStartingWith(List<String> tissueIds) throws DatabaseServiceException {
        return Collections.emptySet();
    }

    @Override
    public Map<String, ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws DatabaseServiceException {
        return Collections.emptyMap();
    }

    @Override
    protected void setProperty(String key, String value) throws DatabaseServiceException {

    }

    @Override
    protected String getProperty(String key) throws DatabaseServiceException {
        return null;
    }

    @Override
    public Set<Integer> deleteWorkflows(ProgressListener progress, Plate plate) throws DatabaseServiceException {
        return Collections.emptySet();
    }

    @Override
    public Map<String, Workflow> getWorkflows(Collection<String> workflowIds) throws DatabaseServiceException {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException {
        return Collections.emptyMap();
    }

    @Override
    public void renameWorkflow(int id, String newName) throws DatabaseServiceException {

    }

    @Override
    public void testConnection() throws DatabaseServiceException {

    }

    @Override
    public List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getPlatesUsingCocktail(Cocktail cocktail) throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<String> getPlatesUsingThermocycle(Thermocycle thermocycle) throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public void addCocktails(List<? extends Cocktail> cocktails) throws DatabaseServiceException {

    }

    @Override
    public void deleteCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {

    }

    @Override
    public List<PCRCocktail> getPCRCocktailsFromDatabase() throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public List<Thermocycle> getThermocyclesFromDatabase(String thermocycleIdentifierTable) throws DatabaseServiceException {
        return Collections.emptyList();
    }

    @Override
    public void addThermoCycles(String tableName, List<Thermocycle> cycles) throws DatabaseServiceException {

    }

    @Override
    public void deleteThermoCycles(String tableName, List<Thermocycle> cycles) throws DatabaseServiceException {

    }

    @Override
    public boolean supportReporting() {
        return false;
    }
}
