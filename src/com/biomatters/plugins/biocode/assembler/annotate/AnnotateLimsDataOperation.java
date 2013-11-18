package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.QueryField;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.ProgressListener;

import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateLimsDataOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Annotate with FIMS/LIMS Data...",
                "Annotate sequences/assemblies with data from the Lab and Field Information Management Systems. eg. Taxonomy, Collector, Primers used")
                .setInPopupMenu(true, 0.22);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequencing reads to annotate them with data from the FIMS (Field Information Management System) and LIMS (Lab Information Managment System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE),
                new DocumentSelectionSignature(SequenceAlignmentDocument.class,1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new AnnotateLimsDataOptions(documents);
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        final AnnotateLimsDataOptions options = (AnnotateLimsDataOptions) o;
        AnnotateUtilities.annotateFimsData(annotatedDocuments, progressListener, getFimsDataGetter(annotatedDocuments, options), true);
        return null;
    }

    private static FimsDataGetter getFimsDataGetter(AnnotatedPluginDocument[] documents, final AnnotateLimsDataOptions options) throws DocumentOperationException {
        if(options.isAnnotateWithSpecifiedPlate()) {
            return new FimsDataGetter() {
                @Override
                public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                    return options.getFIMSDataForGivenPlate(document);
                }
            };
        } else {
            final Map<String, WorkflowDocument> workflowMap = getWorkflowDocumentsForDocs(documents);
            if(workflowMap.isEmpty()) {
                throw new DocumentOperationException("Could not find any matching samples in FIMS for annotated workflow or plate/well.");
            }

            return new FimsDataGetter() {
                public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                    WorkflowDocument workflowDoc = null;
                    Object workflowName = document.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD);
                    Object plateName = document.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD);
                    Object wellName = document.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD);

                    if(workflowName != null) {
                        workflowDoc = workflowMap.get(workflowName.toString());
                    } else if(plateName != null && wellName != null) {
                        // If we have only annotated from FIMS and not LIMS there will be no workflow, only a plate/well
                        workflowDoc = getMatchingWorkflow(plateName.toString(), wellName.toString());
                    }

                    if(workflowDoc != null) {
                        Reaction extractionReaction = workflowDoc.getMostRecentReaction(Reaction.Type.Extraction);
                        if(extractionReaction != null) {
                            return new FimsData(workflowDoc, plateName == null ? null : plateName.toString(),
                                    new BiocodeUtilities.Well(extractionReaction.getLocationString()));
                        }
                    }
                    return null;
                }

                private WorkflowDocument getMatchingWorkflow(String plateName, String wellName) {
                    WorkflowDocument workflowDoc = null;
                    for (WorkflowDocument workflow : workflowMap.values()) {
                        for (Reaction reaction : workflow.getReactions()) {
                            if(plateName.equals(reaction.getPlateName()) && wellName.equals(reaction.getLocationString())) {
                                workflowDoc = workflow;
                            }
                        }
                    }
                    return workflowDoc;
                }
            };
        }
    }

    private static Map<String, WorkflowDocument> getWorkflowDocumentsForDocs(AnnotatedPluginDocument[] documents) throws DocumentOperationException, DocumentOperationException {
        Set<String> workflowNames = new HashSet<String>();
        Set<String> plateNames = new HashSet<String>();
        for (AnnotatedPluginDocument document : documents) {
            Object workflowName = document.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD);
            if(workflowName != null) {
                workflowNames.add(workflowName.toString());
            }
            Object plateName = document.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD);
            if(plateName != null) {
                plateNames.add(plateName.toString());
            }
        }
        List<Query> queries = new ArrayList<Query>();
        for (String workflowName : workflowNames) {
            queries.add(Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL, workflowName));
        }

        if(workflowNames.isEmpty()) {
            for (String plateName : plateNames) {
                queries.add(Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, plateName));
            }
        }
        if(queries.isEmpty()) {
            throw new DocumentOperationException("Could not find any FIMS samples because document" +
                    (documents.length > 1 ? "s" : "") +
                    " do not have any annotated workflow names or plates/wells.");
        }

        List<AnnotatedPluginDocument> workflowDocs;
        try {
            workflowDocs = BiocodeService.getInstance().retrieve(Query.Factory.createOrQuery(
                    queries.toArray(new Query[queries.size()]),
                    BiocodeService.getSearchDownloadOptions(false, false, true, false)
                    ), ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("Unable to load workflows for documents", e);
        }
        final Map<String, WorkflowDocument> workflowMap = new HashMap<String, WorkflowDocument>();
        for (AnnotatedPluginDocument workflowDoc : workflowDocs) {
            PluginDocument pluginDoc = workflowDoc.getDocument();
            if(pluginDoc instanceof WorkflowDocument) {
                WorkflowDocument workflowPluginDoc = (WorkflowDocument) pluginDoc;
                workflowMap.put(workflowPluginDoc.getWorkflow().getName(), workflowPluginDoc);
            }
        }
        return workflowMap;
    }
}
