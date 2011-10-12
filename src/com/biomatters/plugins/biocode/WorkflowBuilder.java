package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseService;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.assembler.lims.MarkInLimsUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
import org.jdom.Element;

import java.awt.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 6/10/11
 * Time: 9:49 AM
 * To change this template use File | Settings | File Templates.
 */
public class WorkflowBuilder extends DocumentOperation {

    //taken from the alignment plugin...
    public static final DocumentField IS_FORWARD_FIELD = DocumentField.createBooleanField("Is Forward Read",
            "Whether this read is in the forward direction", "isForwardRead", true, false);

    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Workflow Builder").setMainMenuLocation(GeneiousActionOptions.MainMenu.Tools);
    }

    @Override
    public String getHelp() {
        return "Builds workflows";
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(SequenceListDocument.class, 1, 1)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {

        Options options = new Options(getClass());

        options.addFileSelectionOption("traceFolder", "TraceLocation", "", new String[0], "Browse...", new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return new File(dir, name).isDirectory();
            }
        });
        return options;
    }

    private Collection<String> getPlateIds(String traceLocation) {
        File traceFolder = new File(traceLocation);
        Set<String> plateNames = new LinkedHashSet<String>();
        for(String s : traceFolder.list()) {
            if(!s.endsWith(".scf")) {
                continue;
            }
            String[] elements = s.split("_");
            plateNames.add(elements[0]);
        }
        return plateNames;
    }

    private String getTissuePlateName(String plateName) {
        String number = plateName.substring(plateName.indexOf("EP")+2);
        return "plate_EP_"+number;
    }

    @Override
    public void performOperation(final AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options, SequenceSelection sequenceSelection, final OperationCallback callback) throws DocumentOperationException {
        String traceLocation = options.getValueAsString("traceFolder");

        final List<AnnotatedPluginDocument> documents = new ArrayList<AnnotatedPluginDocument>();

        OperationCallback myCallback = new OperationCallback(){
            @Override
            public void setResumableState(Element e) {
                callback.setResumableState(e);
            }

            @Override
            public Element getResumableState() {
                return callback.getResumableState();
            }

            @Override
            public boolean canResume() {
                return callback.canResume();
            }

            @Override
            public void setSubFolder(String name) throws DatabaseServiceException {
                callback.setSubFolder(name);
            }

            @Override
            public AnnotatedPluginDocument addDocument(AnnotatedPluginDocument doc, boolean dontSelectDocumentWhenComplete, ProgressListener progress) throws DocumentOperationException {
                AnnotatedPluginDocument annotatedPluginDocument = callback.addDocument(doc, dontSelectDocumentWhenComplete, progress);
                documents.add(annotatedPluginDocument);
                return annotatedPluginDocument;
            }

            @Override
            public void setRemoteJobId(GeneiousService service, String jobId) {
                callback.setRemoteJobId(service, jobId);
            }
        };

        CompositeProgressListener composite = new CompositeProgressListener(progressListener, 2);

        SequenceListDocument sequenceList = (SequenceListDocument)annotatedDocuments[0].getDocument();

        //buildContigs(traceLocation, sequenceList, composite, myCallback);

        WritableDatabaseService assembliesFolder = ((WritableDatabaseService)getFolder(annotatedDocuments[0])).getChildService("assemblies");
//        for(AnnotatedPluginDocument document : documents) {
//            if(SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass())) {
//                assembliesFolder = (WritableDatabaseService)getFolder(document);
//                break;
//            }
//        }

        composite.beginNextSubtask();

        try {
            buildPlates(traceLocation, sequenceList, assembliesFolder, composite);
        } catch (ConnectionException e) {
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (BadDataException e) {
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (DocumentImportException e) {
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        }
    }

    public List<ReactionUtilities.MemoryFile> getChromats(String traceLocation, String plateName, String well, boolean forward) throws IOException {
        File traceFolder = new File(traceLocation);
        List<ReactionUtilities.MemoryFile> files = new ArrayList<ReactionUtilities.MemoryFile>(1);
        for(File f : traceFolder.listFiles()) {
            if(f.getName().startsWith(plateName) && f.getName().toLowerCase().contains(well.toLowerCase()) && f.getName().contains(forward ? "LCO" : "HCO")) {
                ReactionUtilities.MemoryFile mf = ReactionUtilities.loadFileIntoMemory(f);
                files.add(mf);
            }
        }
        return files;
    }

    public void buildPlates(String traceLocation, SequenceListDocument sequenceList, WritableDatabaseService contigFolder, final ProgressListener progressListener) throws ConnectionException, SQLException, BadDataException, DocumentOperationException, IOException, DocumentImportException {
        Collection<String> plateNames = getPlateIds(traceLocation);


        final CompositeProgressListener plateComposite = new CompositeProgressListener(progressListener, plateNames.size());

        BiocodeService.BlockingProgress messagePassingProgress = new BiocodeService.BlockingProgress(){
            public void setMessage(String s) {
                plateComposite.setMessage(s);
            }

            public void dispose() {}

            public Component getComponentForOwner() {return null;}
        };

        for(String plateName : plateNames) {
            final CompositeProgressListener composite = new CompositeProgressListener(plateComposite, 5);
            //===================EXTRACTION PLATE=======================================================================
            composite.beginSubtask("Creating extraction plate");
            Plate extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction);
            Map<String, String> tissueIds = BiocodeService.getInstance().getActiveFIMSConnection().getTissueIdsFromFimsTissuePlate(getTissuePlateName(plateName));
            Set<String> extractionIds = BiocodeService.getInstance().getActiveLIMSConnection().getAllExtractionIdsStartingWith(new ArrayList<String>(tissueIds.values()));

            for(Map.Entry<String, String> entry : tissueIds.entrySet()) {
                BiocodeUtilities.Well well = new BiocodeUtilities.Well(entry.getKey());
                ExtractionReaction reaction = (ExtractionReaction) extractionPlate.getReaction(well);
                reaction.setTissueId(entry.getValue());
                String extractionId = ReactionUtilities.getNewExtractionId(extractionIds, entry.getValue());
                reaction.setExtractionId(extractionId);
                extractionIds.add(extractionId);
                System.out.println("Extraction position for "+well+": "+reaction.getPosition());
            }

            //todo: set extraction options
            String extractionPlateName = plateName + "_X1";
            extractionPlate.setName(extractionPlateName);
            int emptyCount = 0;
            for (Reaction r : extractionPlate.getReactions()) {
                if(r.isEmpty()) {
                    emptyCount++;
                }
            }
            System.out.println("empty extractions: "+emptyCount);

            BiocodeService.getInstance().saveExtractions(messagePassingProgress, extractionPlate);
            List<PlateDocument> plates = BiocodeService.getInstance().getActiveLIMSConnection().getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, extractionPlateName), Collections.<WorkflowDocument>emptyList(), null);
            if(plates.size() != 1) {
                throw new DocumentOperationException("Could not find the plate "+extractionPlateName);
            }

            extractionPlate = plates.get(0).getPlate();
            //====================PCR PLATE=============================================================================
            composite.beginSubtask("Creating PCR plate");
            Plate pcrPlate = new Plate(Plate.Size.w96, Reaction.Type.PCR);

            NewPlateDocumentOperation.copyPlateOfSameSize(extractionPlate, pcrPlate, null);

            //todo: set PCR options
            for(Reaction r : pcrPlate.getReactions()) {
                if(r.getExtractionId() != null && r.getExtractionId().length() > 0)
                r.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), "COI");
            }
            String pcrPlateName = plateName + "_PCR01_COI";
            pcrPlate.setName(pcrPlateName);
            pcrPlate.setThermocycle(BiocodeService.getInstance().getPCRThermocycles().get(0));

            emptyCount = 0;
            for (Reaction r : pcrPlate.getReactions()) {
                if(r.getExtractionId() == null || r.getExtractionId().length() == 0) {
                    emptyCount++;
                }
            }
            System.out.println("empty PCR: "+emptyCount);

            BiocodeService.getInstance().saveReactions(messagePassingProgress, pcrPlate);

//            List<PlateDocument> pcrPlates = BiocodeService.getInstance().getActiveLIMSConnection().getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, pcrPlateName), Collections.<WorkflowDocument>emptyList(), null);
//            if(plates.size() != 1) {
//                throw new DocumentOperationException("Could not find the plate "+extractionPlateName);
//            }
//
//            pcrPlate = pcrPlates.get(0).getPlate();


            //===================CS FORWARD PLATE=======================================================================
            composite.beginSubtask("Creating Sequencing plate (forward)");
            Plate csForwardPlate = new Plate(Plate.Size.w96, Reaction.Type.CycleSequencing);
            NewPlateDocumentOperation.copyPlateOfSameSize(pcrPlate, csForwardPlate, null);

            for(Reaction r : csForwardPlate.getReactions()) {
                CycleSequencingReaction reaction = (CycleSequencingReaction)r;
                if(reaction.getExtractionId() == null || reaction.getExtractionId().length() == 0) {
                    continue;
                }
                reaction.addChromats(getChromats(traceLocation, new BiocodeUtilities.Well(reaction.getLocationString()).toPaddedString(), plateName, false));
            }

            //todo: set CS options
            for(Reaction r : csForwardPlate.getReactions()) {
                if(r.getExtractionId() != null && r.getExtractionId().length() > 0) {
                    r.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), "COI");
                    r.getOptions().setValue(ReactionOptions.RUN_STATUS, "failed");
                }
            }
            csForwardPlate.setName(plateName+"_CYC01_LCO");
            csForwardPlate.setThermocycle(BiocodeService.getInstance().getCycleSequencingThermocycles().get(0));

            BiocodeService.getInstance().saveReactions(messagePassingProgress, csForwardPlate);

            //===================CS REVERSE PLATE=======================================================================
            composite.beginSubtask("Creating Sequencing plate (reverse)");
            Plate csReversePlate = new Plate(Plate.Size.w96, Reaction.Type.CycleSequencing);
            NewPlateDocumentOperation.copyPlateOfSameSize(pcrPlate, csReversePlate, null);

            for(Reaction r : csReversePlate.getReactions()) {
                CycleSequencingReaction reaction = (CycleSequencingReaction)r;
                if(reaction.getExtractionId() == null || reaction.getExtractionId().length() == 0) {
                    continue;
                }
                reaction.addChromats(getChromats(traceLocation, new BiocodeUtilities.Well(reaction.getLocationString()).toPaddedString(), plateName, false));
            }

            for(Reaction r : csReversePlate.getReactions()) {
                if(r.getExtractionId() != null && r.getExtractionId().length() > 0) {
                    r.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), "COI");
                    r.getOptions().setValue(ReactionOptions.RUN_STATUS, "failed");
                }
            }

            //todo: set CS options
            csReversePlate.setName(plateName+"_CYC01_HCO");
            csReversePlate.setThermocycle(BiocodeService.getInstance().getCycleSequencingThermocycles().get(0));

            BiocodeService.getInstance().saveReactions(messagePassingProgress, csReversePlate);


            //================ASSEMBLIES================================================================================
            composite.beginSubtask("Saving sequence");
            AnnotatedPluginDocument[] folderContents = contigFolder.retrieve("").toArray(new AnnotatedPluginDocument[0]);
            Map<AnnotatedPluginDocument, SequenceDocument> docsToMark = MarkInLimsUtilities.getDocsToMark(folderContents, null);
            for(Map.Entry<AnnotatedPluginDocument, SequenceDocument> entry : docsToMark.entrySet()) {
                AnnotatedPluginDocument assemblyDoc = entry.getKey();
                SequenceAlignmentDocument assembly = (SequenceAlignmentDocument)assemblyDoc.getDocument();
                if(!assemblyDoc.getName().contains(plateName))  {
                    continue;
                }
                String[] nameParts = entry.getKey().getName().split(" ");
                String fimsPlateName = nameParts[0].split("_")[0];
                BiocodeUtilities.Well fimsWell = new BiocodeUtilities.Well(nameParts[0].split("_")[1]);


                for(int i=0; i < assembly.getNumberOfSequences(); i++) {
                    if(i == assembly.getContigReferenceSequenceIndex()) {
                        continue;
                    }
                    AnnotatedPluginDocument referencedDocument = assembly.getReferencedDocument(i);
                    referencedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD, plateName+"_CYC01_"+(referencedDocument.getName().contains("HCO") ? "HCO" : "LCO"));
                    referencedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, fimsWell.toPaddedString());
                    referencedDocument.save();
                }

                DocumentOperation markAsPassedOperation = PluginUtilities.getDocumentOperation("MarkAssemblyAsPassInLims");

                Options operationOptions = markAsPassedOperation.getOptions(assemblyDoc);
                operationOptions.setValue("attachChromatograms", false);
                operationOptions.setValue("technician", "Steve");
                operationOptions.setValue("notes", "Automatically entered from Sylvain's raw data");
                operationOptions.setValue("consensus.trimToReference", true);
                operationOptions.setValue("consensus.thresholdPercent", true);
                operationOptions.setValue("consensus.thresholdPercent", "weighted_60");

                markAsPassedOperation.performOperation(new AnnotatedPluginDocument[] {assemblyDoc}, progressListener, operationOptions);
            }


        }

    }

    public static int getBlankReactionCount(Collection<Reaction> reactions) {
        int count = 0;
        for(Reaction r : reactions) {
            if(r.getExtractionId() == null || r.getExtractionId().length() == 0) {
                count++;
            }
        }
        return count;
    }

    public void buildContigs(String traceLocation, SequenceListDocument sequenceList, ProgressListener progressListener, OperationCallback callback) throws DocumentOperationException {


        DocumentOperation assemblyOperation = PluginUtilities.getDocumentOperation("com.biomatters.plugins.alignment.AssemblyOperation");
        if(assemblyOperation == null) {
            throw new DocumentOperationException("Cannot find the assembly operation");
        }



            CompositeProgressListener composite2 = new CompositeProgressListener(progressListener, sequenceList.getNucleotideSequences().size());
            for(SequenceDocument sequence : sequenceList.getNucleotideSequences()) {
                try {
                    callback.setSubFolder(null);
                } catch (DatabaseServiceException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                if(progressListener.isCanceled()) {
                    return;
                }
                composite2.beginSubtask();
                List<File> traces = getTraces(traceLocation, sequence.getName());
                if(traces == null) {
                    continue;
                }
                System.out.println(sequence.getName()+": "+ StringUtilities.join(", ", traces));
                List<AnnotatedPluginDocument> reads = new ArrayList<AnnotatedPluginDocument>();
                try {
                    for(File f : traces) {
                        reads.addAll(PluginUtilities.importDocuments(f, ProgressListener.EMPTY));
                    }
                } catch (IOException e) {
                    throw new DocumentOperationException(e.getMessage(), e);
                } catch (DocumentImportException e) {
                     throw new DocumentOperationException(e.getMessage(), e);
                }
                List<AnnotatedPluginDocument> assemblyInput = new ArrayList<AnnotatedPluginDocument>();
                for(int i=0; i < reads.size(); i++) {
                    assemblyInput.add(callback.addDocument(reads.get(i), true, ProgressListener.EMPTY));
                }
                SequenceDocument ungappedSequence = new DefaultNucleotideSequence(sequence.getName(), sequence.getSequenceString().replace("-", ""));
                AnnotatedPluginDocument sequenceDocument = DocumentUtilities.createAnnotatedPluginDocument(ungappedSequence);
                assemblyInput.add(sequenceDocument);
                Options assemblyOptions = assemblyOperation.getOptions(assemblyInput);
                assemblyOptions.setValue("data.useReferenceSequence", true);
                assemblyOptions.setValue("data.referenceSequenceName", sequenceDocument.getName());
                assemblyOptions.setValue("data.groupAssemblies", false);
                assemblyOptions.setValue("data.assembleListsSeparately", false);
                assemblyOptions.setValue("method.algorithm.referenceAssembly", "Geneious.reference");
                assemblyOptions.setValue("trimOptions.method", "noTrim");
                assemblyOptions.setValue("results.saveReport", false);
                assemblyOptions.setValue("results.resultsInSubfolder", false);
                assemblyOptions.setValue("results.generateContigs", true);
                assemblyOptions.setValue("results.generateConsensusSequencesReference", false);
                assemblyOptions.setValue("results.saveUnusedReads", true);

                try {
                    callback.setSubFolder("assemblies");
                } catch (DatabaseServiceException e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
                List<AnnotatedPluginDocument> assemblyResults = assemblyOperation.performOperation(assemblyInput, ProgressListener.EMPTY, assemblyOptions);
                for(AnnotatedPluginDocument result : assemblyResults) {
                    if(SequenceAlignmentDocument.class.isAssignableFrom(result.getDocumentClass())) {
                        SequenceAlignmentDocument assembly = (SequenceAlignmentDocument )result.getDocument();

                        for(int i=0; i < assemblyInput.size(); i++) {
                            AnnotatedPluginDocument inputDocument = assemblyInput.get(i);
                            AnnotatedPluginDocument assemblyReference = null;
                            for(int j=0; j < assembly.getNumberOfSequences(); j++) {
                                if(j == assembly.getContigReferenceSequenceIndex()) {
                                    continue;
                                }
                                AnnotatedPluginDocument referencedDocument = assembly.getReferencedDocument(j);
                                if(referencedDocument.getURN().equals(inputDocument.getURN())) {
                                    assemblyReference = referencedDocument;
                                    break;
                                }
                            }

                            if(assemblyReference == null) {
                                DatabaseService database = getFolder(inputDocument);
                                if(database != null && database instanceof WritableDatabaseService) {
                                    try {
                                        WritableDatabaseService childFolder = ((WritableDatabaseService) database).createChildFolder("unassembledTraces");
                                        childFolder.moveDocument(inputDocument, ProgressListener.EMPTY);
                                    } catch (DatabaseServiceException e) {
                                        e.printStackTrace();
                                        throw new DocumentOperationException(e.getMessage(), e);
                                    }
                                }
                                else if(database == null) {
                                    System.out.println("fail");
                                }


                            }
                            else if(assembly.isReferencedDocumentReversed(i)) {
                                AnnotatedPluginDocument referencedDocument = assemblyReference;
                                referencedDocument.setFieldValue(IS_FORWARD_FIELD, false);
                                referencedDocument.save();
                            }

                        }

                    }
                    callback.addDocument(result, true, ProgressListener.EMPTY);
                }
            }

    }

    private DatabaseService getFolder(AnnotatedPluginDocument inputDocument) {
        DatabaseService database = inputDocument.getDatabase();
        if(database == null) {
            try {
                database = PluginUtilities.getWritableDatabaseServiceRoots().get(0).getDocumentLocation(inputDocument.getURN());
            } catch (DatabaseServiceException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        return database;
    }

    private List<File> getTraces(String traceLocation, String name) {
        File tracesFolder = new File(traceLocation);
        List<File> result = new ArrayList<File>();
        for(File f : tracesFolder.listFiles()) {
            if(f.getName().toLowerCase().startsWith(name.toLowerCase())) {
                result.add(f);
            }
        }
        return result;
    }
}

