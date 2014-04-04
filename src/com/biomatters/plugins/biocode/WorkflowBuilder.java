package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseService;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.*;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: Steven Stones-Havas
 * Date: 6/10/11
 * Time: 9:49 AM
 */
public class WorkflowBuilder extends DocumentOperation {

    //taken from the alignment plugin...
    public static final DocumentField IS_FORWARD_FIELD = DocumentField.createBooleanField("Is Forward Read",
            "Whether this read is in the forward direction", "isForwardRead", true, false);
    private final String TRACE_FOLDER = "traceFolder";
    private final String USE_TRACES = "attachTraces";

    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Workflow Builder").setMainMenuLocation(GeneiousActionOptions.MainMenu.Tools);
    }

    @Override
    public String getHelp() {
        return "Builds LIMS workflows and plates from sequences and traces.  Plate information is retrieved from FIMS.";
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(SequenceListDocument.class, 1, 1),
                new DocumentSelectionSignature(SequenceDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    private static final String LOCUS = "locus";
    private static final String FWD = "fwdPrimer";
    private static final String REV = "reversePrimer";

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Options options = new Options(WorkflowBuilder.class);
        options.addStringOption(LOCUS, "Locus:", "");
        options.addPrimerSelectionOption(FWD, "Forward Primer", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());
        options.addPrimerSelectionOption(REV, "Reverse Primer", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());
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

    Map<String, List<String>> prefixes = new HashMap<String, List<String>>();
    {
        prefixes.put("C", Collections.singletonList("Carter-"));
        prefixes.put("K", Collections.singletonList("Kraichak-"));
        prefixes.put("F", Collections.singletonList("Fok-"));
        prefixes.put("N", Arrays.asList("Nitta-", "Nitta_", "JN"));
    }

    // Is it worth making this a general thing
    @Override
    public void performOperation(final AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options, SequenceSelection sequenceSelection, final OperationCallback callback) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException("Must be logged into LIMS");
        }
//        renameSequencesFromFIMS(annotatedDocuments, progressListener);
//        if(true)return;

        // todo Currently requries Manual annotate from FIMS step here

//        doubleCheckTaxonInNameAgainstFIMS(annotatedDocuments);

        // Map seqs to plates from FIMS values
        Map<String, List<AnnotatedPluginDocument>> plateToSequences = new HashMap<String, List<AnnotatedPluginDocument>>();
        for (AnnotatedPluginDocument document : annotatedDocuments) {
            Object plateName = document.getFieldValue("biocode_tissue.format_name96");
            if(plateName == null) {
                System.out.println("Ignoring " + document.getName() + " because it has no annotated plate.");
                continue;
            }
            List<AnnotatedPluginDocument> seqs = plateToSequences.get(plateName.toString());
            if(seqs == null) {
                seqs = new ArrayList<AnnotatedPluginDocument>();
                plateToSequences.put(plateName.toString(), seqs);
                System.out.println("Found New Plate: " + plateName.toString());
            }
            seqs.add(document);
        }

        try {
            String locus = options.getValueAsString(LOCUS);
            String forwardPrimer = options.getValueAsString(FWD);
            String reversePrimer = options.getValueAsString(REV);
            String tech = "Automatic";

            buildPlates(plateToSequences, locus, forwardPrimer, reversePrimer, tech, progressListener);
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
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        }
    }

    private static final Pattern BIOCODE_ID_PATTERN = Pattern.compile(".*(b.+code\\d+\\(\\d+\\))_(.*)");

    private void doubleCheckTaxonInNameAgainstFIMS(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        // Iterate through seqs and match FIMS values, double check lowest taxon
        for (AnnotatedPluginDocument document : annotatedDocuments) {
            Matcher matcher = BIOCODE_ID_PATTERN.matcher(document.getName());
            if(!matcher.matches()) {
                throw new DocumentOperationException("Name " + document.getName() + " does not match pattern!");
            }
            String taxon = matcher.group(2);
            taxon = taxon.replace("_", " ");
            Object lowestAnnotatedTaxon = document.getFieldValue("biocode.LowestTaxon");
            if(lowestAnnotatedTaxon != null && !taxon.equals(lowestAnnotatedTaxon)) {
                System.out.println(document.getName() + ": " + lowestAnnotatedTaxon + " -> " + taxon);
            }
        }
    }

    private void renameSequencesFromFIMS(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener) throws DocumentOperationException {
        Pattern pattern = Pattern.compile("([A-Z])(\\d+[A-Za-z]?)");
        String ID = "biocode.Specimen_Num_Collector";

        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        DocumentField specNumCollectorField = null;
        for (DocumentField field : fimsConnection.getCollectionAttributes()) {
            if(field.getCode().equals(ID)) {
                specNumCollectorField = field;
            }
        }
        if(specNumCollectorField == null) {
            throw new DocumentOperationException("Cannot find field for " + ID + " in FIMS connection");
        }
        List<AnnotatedPluginDocument> renamed = new ArrayList<AnnotatedPluginDocument>();
        List<AnnotatedPluginDocument> missing = new ArrayList<AnnotatedPluginDocument>();
        CompositeProgressListener composite = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        try {
            for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
                composite.beginSubtask("Examining " + annotatedDocument.getName());
                if(composite.isCanceled()) {
                    return;
                }
                String name = annotatedDocument.getName();
                String[] parts = name.split("_");
                String id = parts[0];
                Matcher matcher = pattern.matcher(id);
                if(!matcher.matches()) {
                    System.out.println(id + " did not fit pattern!");
                    missing.add(annotatedDocument);
                    continue;
                }
                String prefixToConvert = matcher.group(1);
                List<Query> queries = new ArrayList<Query>();
                for (String newPrefix : prefixes.get(prefixToConvert)) {
                    queries.add(Query.Factory.createFieldQuery(specNumCollectorField, Condition.EQUAL, newPrefix + matcher.group(2)));
                }
                List<FimsSample> samples = fimsConnection.getMatchingSamples(Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.<String, Object>emptyMap()));
                FimsSample toRenameFrom = null;
                for (FimsSample sample : samples) {
                    Object plateName = sample.getFimsAttributeValue("biocode_tissue.format_name96");
                    if(plateName != null && plateName.toString().trim().length() > 0) {
                        if(toRenameFrom == null) {
                            toRenameFrom = sample;
                        } else {
                            throw new DocumentOperationException("Multiple matches for " + prefixToConvert + ": " + toRenameFrom.getId() + " and " + sample.getId());
                        }
                    }
                }
                if(toRenameFrom != null) {
                    String newName = toRenameFrom.getFimsAttributeValue(ID) + "_" + StringUtilities.join("_", Arrays.asList(parts).subList(1, parts.length));
                    System.out.println("Renaming " + annotatedDocument.getName() + " to " + newName);
                    annotatedDocument.setName(newName);
                    annotatedDocument.save();
                    renamed.add(annotatedDocument);
                } else {
                    missing.add(annotatedDocument);
                }
            }

            System.out.println("Renamed " + renamed.size() + " documents!");

            if(!missing.isEmpty()) {
                System.err.println("Couldn't find samples in FIMS to match the following:");
                for (AnnotatedPluginDocument name : missing) {
                    System.err.println(name.getName());
                }
                System.err.println("");
            }
        } catch (ConnectionException e) {
            throw new DocumentOperationException("Problem communicating with server: " + e.getMessage(), e);
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

    public void buildPlates(Map<String, List<AnnotatedPluginDocument>> platesToSequences, String locus, String forwardPrimer, String reversePrimer, String tech, final ProgressListener progressListener) throws ConnectionException, SQLException, BadDataException, DocumentOperationException, IOException, DocumentImportException, DatabaseServiceException {
        final CompositeProgressListener plateComposite = new CompositeProgressListener(progressListener, platesToSequences.size());

        Set<String> assemblyMissing = new HashSet<String>();
        for(Map.Entry<String, List<AnnotatedPluginDocument>> plateEntry : platesToSequences.entrySet()) {
            String plateName = plateEntry.getKey();
            plateComposite.beginSubtask();
            final CompositeProgressListener composite = new CompositeProgressListener(plateComposite, 6);
            //==================(1) EXTRACTION PLATE====================================================================
            composite.beginSubtask("Creating extraction plate");
            String extractionPlateName = plateName + "_X1";
            List<PlateDocument> existing = BiocodeService.getInstance().getActiveLIMSConnection().getMatchingDocumentsFromLims(
                    Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{extractionPlateName},
                            BiocodeService.getSearchDownloadOptions(false, false, true, false)), null, null, false
            ).getPlates();

            Plate extractionPlate;
            if(existing.isEmpty()) {
                extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction);
                System.out.println("\tCreated plate " + extractionPlateName);
                System.out.println();
            } else {
                extractionPlate = existing.get(0).getPlate();
                System.out.println("\tUsing existing plate " + extractionPlateName);
                System.out.println();
            }

            // Fill in any missing
            Map<String, String> tissueIds = BiocodeService.getInstance().getActiveFIMSConnection().getTissueIdsFromFimsTissuePlate(plateName);
            Set<String> extractionIds = BiocodeService.getInstance().getActiveLIMSConnection().getAllExtractionIdsStartingWith(new ArrayList<String>(tissueIds.values()));

                Set<String> wellsToExtract = new HashSet<String>();
                for (AnnotatedPluginDocument document : plateEntry.getValue()) {
                    Object value = document.getFieldValue("biocode_tissue.well_number96");
                    if(value != null) {
                        wellsToExtract.add(value.toString());
                    }
                }

            int saved = 0;
            for(Map.Entry<String, String> entry : tissueIds.entrySet()) {
                if(!wellsToExtract.contains(entry.getKey())) {
                    continue;
                }
                BiocodeUtilities.Well well = new BiocodeUtilities.Well(entry.getKey());
                ExtractionReaction reaction = (ExtractionReaction) extractionPlate.getReaction(well);
                reaction.setTissueId(entry.getValue());
                String extractionId = ReactionUtilities.getNewExtractionId(extractionIds, entry.getValue());
                reaction.setExtractionId(extractionId);
                extractionIds.add(extractionId);
                saved++;
            }


            extractionPlate.setName(extractionPlateName);
            int emptyCount = 0;
            for (Reaction r : extractionPlate.getReactions()) {
                if(r.isEmpty()) {
                    emptyCount++;
                }
            }

            BiocodeService.getInstance().savePlate(extractionPlate, progressListener);
            List<PlateDocument> plates = BiocodeService.getInstance().getActiveLIMSConnection().getMatchingDocumentsFromLims(
                    Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{extractionPlateName},
                            BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                    null, null, false
            ).getPlates();
            if(plates.size() != 1) {
                throw new DocumentOperationException("Could not find the plate "+extractionPlateName);
            }
            extractionPlate = plates.get(0).getPlate();
            System.out.println("\tSaved extractions: "+saved);
            System.out.println("\tEmpty extractions: "+emptyCount);


            //====================(2) PCR PLATE=========================================================================
            composite.beginSubtask("Creating PCR plate");
            Plate pcrPlate = new Plate(Plate.Size.w96, Reaction.Type.PCR);

            // We don't want to copy all extractions.  Just the ones we have traces for.
            Set<String> idsToMatch = new HashSet<String>();
            for (AnnotatedPluginDocument doc : plateEntry.getValue()) {
                Object id = doc.getFieldValue("biocode.Specimen_Num_Collector");
                if(id != null) {
                    idsToMatch.add(id.toString());
                }
            }
            copyPlateOfSameSizeIfIdFound(extractionPlate, pcrPlate, idsToMatch);

            for(Reaction r : pcrPlate.getReactions()) {
                if(r.getExtractionId() != null && r.getExtractionId().length() > 0 && !r.isEmpty()){
                    r.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
                    r.getOptions().setValue(ReactionOptions.RUN_STATUS, ReactionOptions.RUN_VALUE);
                    r.getOptions().setValue("notes", "Automatically entered from Sonia's raw data");
                    r.getOptions().setValue(PCROptions.PRIMER_OPTION_ID, forwardPrimer);
                    r.getOptions().setValue(PCROptions.PRIMER_REVERSE_OPTION_ID, reversePrimer);
                }
            }
            String pcrPlateName = plateName + "_PCR01_" + locus;
            pcrPlate.setName(pcrPlateName);
            pcrPlate.setThermocycle(BiocodeService.getInstance().getPCRThermocycles().get(0));

            emptyCount = 0;
            for (Reaction r : pcrPlate.getReactions()) {
                if(r.getExtractionId() == null || r.getExtractionId().length() == 0) {
                    emptyCount++;
                }
            }

            BiocodeService.getInstance().savePlate(pcrPlate, composite);
            System.out.println("\tCreated PCR plate " + pcrPlateName);
            System.out.println("\tempty PCR: "+emptyCount);
            System.out.println();

            // Get the plate and annotate the workflow onto sequences
            Query plateQuery = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{pcrPlateName},
                    BiocodeService.getSearchDownloadOptions(false, false, true, false));
            List<AnnotatedPluginDocument> plateDoc = BiocodeService.getInstance().retrieve(plateQuery, ProgressListener.EMPTY);
            if(plateDoc.isEmpty()) {
                throw new DocumentOperationException("Failed to retrieve PCR plate after creating it");
            }
            PlateDocument platePluginDoc = null;
            if(PlateDocument.class.isAssignableFrom(plateDoc.get(0).getDocumentClass())) {
                platePluginDoc = (PlateDocument)plateDoc.get(0).getDocumentOrNull();
            }
            if(platePluginDoc == null) {
                throw new DocumentOperationException("Failed to load plate " + plateDoc.get(0).getName());
            }

            //==================(3) CS FORWARD PLATE====================================================================
            composite.beginSubtask("Creating Sequencing plate (forward)");
            String seqFPlateName = createCycleSequencingPlate(pcrPlate, plateName, true, locus, tech, forwardPrimer, composite);

            //==================(4) CS REVERSE PLATE====================================================================
            composite.beginSubtask("Creating Sequencing plate (reverse)");
            String seqRPlateName = createCycleSequencingPlate(pcrPlate, plateName, false, locus, tech, reversePrimer, composite);

            //==================(5) Link our sequences to the original contigs and traces  =============================
            composite.beginSubtask("Linking sequences to assemblies and traces");
            // todo This whole section isn't geneic.  It relies on BIOCODE_ID_PATTERN and also relies on the working directory
            WritableDatabaseService workingDir = (WritableDatabaseService) plateEntry.getValue().get(0).getDatabase().getParentService().getParentService();
            Map<String, AnnotatedPluginDocument> biocodeIdToSeq = new HashMap<String, AnnotatedPluginDocument>();
            Map<String, AnnotatedPluginDocument> biocodeIdToAssembly = new HashMap<String, AnnotatedPluginDocument>();
            for (AnnotatedPluginDocument seq : plateEntry.getValue()) {
                Matcher matcher = BIOCODE_ID_PATTERN.matcher(seq.getName());
                if(!matcher.matches()) {
                    throw new DocumentOperationException(seq.getName() + " does not match pattern!");
                }
                String id = matcher.group(1);
                List<AnnotatedPluginDocument> docsMatchingId = workingDir.retrieve(Query.Factory.createFieldQuery(DocumentField.NAME_FIELD, Condition.CONTAINS, new Object[]{id},
                        Collections.<String, Object>singletonMap(WritableDatabaseService.KEY_SEARCH_SUBFOLDERS, Boolean.TRUE)), ProgressListener.EMPTY);
                for (AnnotatedPluginDocument document : docsMatchingId) {
                    if(SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass()) && document.getName().startsWith(id)) {
                        if(biocodeIdToAssembly.get(id) != null) {
                            throw new DocumentOperationException("Duplicate assemblies for " + id);
                        }
                        biocodeIdToAssembly.put(id, document);
                    }
                }
                if(biocodeIdToAssembly.get(id) == null) {
                    assemblyMissing.add(id);
                } else {
                    biocodeIdToSeq.put(id, seq);
                }
            }

            for (Map.Entry<String, AnnotatedPluginDocument> entry : biocodeIdToAssembly.entrySet()) {
                String id = entry.getKey();
                AnnotatedPluginDocument assemblyDoc = entry.getValue();
                AnnotatedPluginDocument finalSequence = biocodeIdToSeq.get(id);
                setOperationRecordForConsensusAndAssembly(assemblyDoc, finalSequence);
            }

            for (String biocodeId : biocodeIdToSeq.keySet()) {
                AnnotatedPluginDocument seq = biocodeIdToSeq.get(biocodeId);
                AnnotatedPluginDocument assembly = biocodeIdToAssembly.get(biocodeId);
                if(assembly == null) {
                    throw new DocumentOperationException("Assembly missing for " + biocodeId);
                }
                if(seq == null) {
                    throw new DocumentOperationException("Seq missing for " + biocodeId);
                }

                String well = String.valueOf(seq.getFieldValue("biocode_tissue.well_number96"));
                Reaction reaction = platePluginDoc.getPlate().getReaction(new BiocodeUtilities.Well(well));

                String workflowName = reaction.getWorkflow().getName();
                if(workflowName == null || workflowName.trim().length() == 0) {
                    throw new DocumentOperationException("No workflow for found in " + platePluginDoc.getName() + "(" + well + ")" + " for " + seq.getName());
                }
                seq.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, workflowName);
                seq.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, well);
                seq.save();

                assembly.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, workflowName);
                assembly.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, well);
                assembly.save();

                if (SequenceAlignmentDocument.class.isAssignableFrom(assembly.getDocumentClass())) {
                    SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)assembly.getDocument();
                    for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                        if (i == alignment.getContigReferenceSequenceIndex()) continue;
                        AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);

                        if (referencedDocument == null) {
                            SequenceDocument toExtract = alignment.getSequence(i);
                            AnnotatedPluginDocument newRef = DocumentUtilities.createAnnotatedPluginDocument(
                                    SequenceExtractionUtilities.extract(toExtract, new SequenceExtractionUtilities.ExtractionOptions(1, toExtract.getSequenceLength())));
                            referencedDocument = ((WritableDatabaseService)assembly.getDatabase()).addDocumentCopy(newRef, ProgressListener.EMPTY);
                            alignment.setReferencedDocument(i, referencedDocument);
                            assembly.saveDocument();
                            System.out.println("Contig \"" + assembly.getName() + "\" is missing a referened document.  Created " + referencedDocument.getName());
                        }
                        if (!NucleotideSequenceDocument.class.isAssignableFrom(referencedDocument.getDocumentClass())) {
                            throw new DocumentOperationException("Contig \"" + assembly.getName() + "\" contains a sequence which is not DNA");
                        }
                        boolean originalReversed = referencedDocument.getName().endsWith(SequenceExtractionUtilities.REVERSED_NAME_SUFFIX);
                        boolean reversedInAssembly =  alignment.isReferencedDocumentReversed(i);
                        boolean reversed = (originalReversed || reversedInAssembly) && !(originalReversed && reversedInAssembly);
                        referencedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD,
                                reversed ? seqRPlateName : seqFPlateName);
                        referencedDocument.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, workflowName);
                        referencedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, well);
                        referencedDocument.save();
                    }
                }
            }

            //=================(6) ASSEMBLIES===========================================================================
            composite.beginSubtask("Saving sequences to plates");
            DocumentOperation markAsPassOperation = PluginUtilities.getDocumentOperation("MarkAssemblyAsPassInLims");
            Options markAsPassOptions = markAsPassOperation.getOptions(plateEntry.getValue());
            markAsPassOptions.setValue("trace.attachChromatograms", true);
            markAsPassOptions.setValue("details.technician", tech);
            markAsPassOptions.setValue("details.notes", "Automatically entered from Sonia's raw data");
            markAsPassOperation.performOperation(new ArrayList<AnnotatedPluginDocument>(biocodeIdToSeq.values()), composite, markAsPassOptions);

        }
        if(!assemblyMissing.isEmpty()) {
            Dialogs.showMessageDialog("Assemblies Missing:\n" + StringUtilities.join("\n", assemblyMissing));
        }
    }

    private void copyPlateOfSameSizeIfIdFound(Plate srcPlate, Plate destPlate, Collection<String> ids) {
        if(srcPlate.getReactionType() == destPlate.getReactionType()) { //copy everything
            destPlate.setName(srcPlate.getName());
            destPlate.setThermocycle(srcPlate.getThermocycle());
        }
        Reaction[] srcReactions = srcPlate.getReactions();
        Reaction[] destReactions = destPlate.getReactions();
        int count = 0;
        for(int i=0; i < srcReactions.length; i++) {
            boolean copy = ids.contains(String.valueOf(srcReactions[i].getFieldValue("biocode.Specimen_Num_Collector")));
            if(copy) {
                count++;
                ReactionUtilities.copyReaction(srcReactions[i], destReactions[i]);
            }
            else {
                System.out.println("didn't copy!");
            }
        }
        System.out.println("Copied " + count + " reactions from " + srcPlate.getName() + " to " + destPlate.getName());
    }

    private void setOperationRecordForConsensusAndAssembly(AnnotatedPluginDocument assemblyDoc, AnnotatedPluginDocument consensus) throws DocumentOperationException, DatabaseServiceException {
        URN parentOperationRecord = consensus.getParentOperationRecord();
        if(parentOperationRecord == null) {
            OperationRecordDocument record = new OperationRecordDocument(Collections.singletonList(assemblyDoc.getURN()), "Generate_Consensus", System.currentTimeMillis());
            record.addOutputDocument(consensus.getURN());
            record.linkDocumentsInDatabase(ProgressListener.EMPTY);
        }
    }

    private String createCycleSequencingPlate(Plate pcrPlateToCopyFrom, String plateName, boolean forwardNotReverse, String locus, String tech, String primerUrn, ProgressListener progressListener) throws DocumentOperationException, DatabaseServiceException, BadDataException {
        System.out.println("Creating Cycle Sequencing Plate");
        Plate csPlate = new Plate(Plate.Size.w96, Reaction.Type.CycleSequencing);
        NewPlateDocumentOperation.copyPlateOfSameSize(pcrPlateToCopyFrom, csPlate, false);

        for(Reaction r : csPlate.getReactions()) {
            if(r.getExtractionId() != null && r.getExtractionId().length() > 0 && !r.isEmpty()) {
                r.getOptions().setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), locus);
                r.getOptions().setValue(ReactionOptions.RUN_STATUS, ReactionOptions.RUN_VALUE);
                r.getOptions().setValue("technician", tech);
                r.getOptions().setValue(CycleSequencingOptions.DIRECTION, forwardNotReverse ? CycleSequencingOptions.FORWARD_VALUE : CycleSequencingOptions.REVERSE_VALUE);
                r.getOptions().setValue("notes", "Automatically entered from Sonia's raw data");
                r.getOptions().setValue(CycleSequencingOptions.PRIMER_OPTION_ID, primerUrn);
            }
        }
        String seqPlateName = plateName + "_CYC01_" + locus + (forwardNotReverse ? "_F" : "_R" );
        csPlate.setName(seqPlateName);
        csPlate.setThermocycle(BiocodeService.getInstance().getCycleSequencingThermocycles().get(0));

        BiocodeService.getInstance().savePlate(csPlate, progressListener);
        System.out.println("\tCreated cycle sequencing plate " + seqPlateName);
        System.out.println();
        return seqPlateName;
    }


    private static Thermocycle getThermocycle(List<Thermocycle> thermocycles, String name) throws DocumentOperationException{
        for(Thermocycle cycle : thermocycles) {
            if(cycle.getName().equals(name)) {
                return cycle;
            }
        }
        throw new DocumentOperationException("Could not find the thermocycle "+name);
    }

    private static Cocktail getCocktail(List<? extends Cocktail> cocktails, String name) throws DocumentOperationException{
        for(Cocktail cocktail : cocktails) {
            if(cocktail.getName().equals(name)) {
                return cocktail;
            }
        }
        throw new DocumentOperationException("Could not find the cocktail "+name);
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

