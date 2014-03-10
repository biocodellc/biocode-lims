package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentImportException;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;

import javax.swing.*;
import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.List;
import java.io.IOException;
import java.io.File;
import java.lang.ref.WeakReference;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 6:02:38 PM
 */
public class CycleSequencingReaction extends Reaction<CycleSequencingReaction>{
    public static final DocumentField NUM_TRACES_FIELD = DocumentField.createIntegerField("# Traces", "Number of traces attached to reaction", "numTraces");

    private CycleSequencingOptions options;
    private Set<Integer> tracesToRemoveOnSave = new LinkedHashSet<Integer>();

    private WeakReference<List<Trace>> traces;
    private List<Trace> tracesStrongReference;

    public List<SequencingResult> getSequencingResults() {
        return Collections.unmodifiableList(sequencingResults);
    }

    public void addSequencingResults(Collection<SequencingResult> results) {
        sequencingResults.addAll(results);
    }

    private List<SequencingResult> sequencingResults = new ArrayList<SequencingResult>();

    private int cacheNumTraces;

    public CycleSequencingReaction() {

    }

    public CycleSequencingReaction(ResultSet r) throws SQLException {
        this();
        init(r);
//        System.out.println(getWorkflow());
    }

    private static final String NUM_TRACES_ATTRIBUTE = "cachedNumTraces";
    private static final String SEQ_RESULTS = "sequencingResults";

    @Override
    public Element toXML() {
        Element element = super.toXML();
        if(traces != null && traces.get() != null) {
            for(Trace trace : traces.get()) {
                element.addContent(XMLSerializer.classToXML("trace", trace));
            }
        }
        element.setAttribute(NUM_TRACES_ATTRIBUTE, "" + cacheNumTraces);
        Element resultsElement = new Element(SEQ_RESULTS);
        element.addContent(resultsElement);
        for (SequencingResult sequencingResult : sequencingResults) {
            resultsElement.addContent(sequencingResult.toXML());
        }
        return element;
    }

    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        super.fromXML(element);
        String numTracesString = element.getAttributeValue(NUM_TRACES_ATTRIBUTE);
        cacheNumTraces = numTracesString == null ? 0 : Integer.parseInt(numTracesString);
        List<Element> traceElements = element.getChildren("trace");
        if(traceElements.size() > 0) {
            List<Trace> traces = new ArrayList<Trace>();
            for(Element traceElement : traceElements) {
                traces.add(XMLSerializer.classFromXML(traceElement, Trace.class));
            }
            this.traces = new WeakReference<List<Trace>>(traces);
        }

        Element resultsElement = element.getChild(SEQ_RESULTS);
        if(resultsElement != null) {
            for (Element child : resultsElement.getChildren()) {
                sequencingResults.add(new SequencingResult(child));
            }
        }
    }

    private void init(ResultSet r) throws SQLException {
        setPlateId(r.getInt("cyclesequencing.plate"));
        setPosition(r.getInt("cyclesequencing.location"));
        setCreated(r.getTimestamp("cyclesequencing.date"));
        extractionBarcode = r.getString("extraction.extractionBarcode");
        setId(r.getInt("cyclesequencing.id"));
        Options options = getOptions();

        String extractionId = r.getString("extraction.extractionId");
        if(extractionId != null) {
            options.setValue("extractionId", extractionId);
        }
        String s = r.getString("workflow.name");
        if(s != null) {
            options.setValue("workflowId", s);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("extraction.extractionId"), r.getString("workflow.locus"), r.getDate("workflow.date")));
            options.setValue(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), r.getString("workflow.locus"));
        }

        options.getOption(ReactionOptions.RUN_STATUS).setValueFromString(r.getString("cyclesequencing.progress"));

        DocumentSelectionOption primerOption = (DocumentSelectionOption)options.getOption(CycleSequencingOptions.PRIMER_OPTION_ID);
        String primerName = r.getString("cyclesequencing.primerName");
        String primerSequence = r.getString("cyclesequencing.primerSequence");
        if(primerSequence.length() > 0) {
            primerOption.setValue(new DocumentSelectionOption.FolderOrDocuments(BiocodeUtilities.createPrimerDocument(primerName, primerSequence)));
        }
        options.setValue("direction", r.getString("direction"));
        //options.setValue("prAmount", r.getInt("cyclesequencing.primerAmount"));
        options.setValue("notes", r.getString("cyclesequencing.notes"));
        options.setValue(ReactionOptions.RUN_STATUS, r.getString("cyclesequencing.progress"));
        options.setValue("cocktail", r.getString("cyclesequencing.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("cyclesequencing.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getString("cyclesequencing.cleanupMethod"));
        options.getOption("date").setValue(r.getDate("cyclesequencing.date")); //we use getOption() here because the toString() method of java.sql.Date is different to the toString() method of java.util.Date, so setValueFromString() fails in DateOption
        options.setValue("technician", r.getString("cyclesequencing.technician"));
        setPlateName(r.getString("plate.name"));
        setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))).toString());

        byte[] imageBytes = r.getBytes("gelimage");
        if(imageBytes != null) {
            setGelImage(new GelImage(imageBytes, getLocationString()));
        }

        int thermocycleId = r.getInt("plate.thermocycle");
        if(thermocycleId >= 0) {
            for(Thermocycle tc : BiocodeService.getInstance().getCycleSequencingThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }

        SequencingResult result = SequencingResult.fromResultSet(r);
        if(result != null) {
            sequencingResults.add(result);
        }
    }

    public void setCacheNumTraces(int cacheNumTraces) {
        this.cacheNumTraces = cacheNumTraces;
    }

    public Type getType() {
        return Type.CycleSequencing;
    }


    public ReactionOptions _getOptions() {
        if(options == null) {
            options = new CycleSequencingOptions(this.getClass());
        }
        return options;
    }

    public void setOptions(ReactionOptions op) {
        if(!(op instanceof CycleSequencingOptions)) {
            throw new IllegalArgumentException("Options must be instances of CycleSequencingOptions");
        }
        this.options = (CycleSequencingOptions)op;
    }

    public String getLocus() {
        return getOptions().getValueAsString(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode());
    }

    public Cocktail getCocktail() {
        String cocktailId = ((Options.OptionValue)getOptions().getOption("cocktail").getValue()).getName();
        for(Cocktail c : Cocktail.getAllCocktailsOfType(getType())) {
            if((""+c.getId()).equals(cocktailId)) {
                return c;
            }
        }
        return null;
    }

    public static List<DocumentField> getDefaultDisplayedFields() {
        if(BiocodeService.getInstance().isLoggedIn()) {
            return Arrays.asList(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(),
                    new DocumentField("Primer", "", CycleSequencingOptions.PRIMER_OPTION_ID, String.class, true, false),
                    new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false));
        }
        return Arrays.asList(new DocumentField("Primer", "", CycleSequencingOptions.PRIMER_OPTION_ID, String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false));
    }

    public void addSequences(List<Trace> traces) {
        if(traces != null) {
            addTraces(traces);
        }
    }

    public Color _getBackgroundColor() {
        String runStatus = options.getValueAsString(ReactionOptions.RUN_STATUS);
        if(runStatus.equals("none"))
                return Color.white;
        else if(runStatus.equals("passed"))
                return Color.green.darker();
        else if(runStatus.equals("failed"))
            return Color.red.darker();
        return Color.white;
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    public void setExtractionId(String s) {
        getOptions().setValue("extractionId", s);
    }

    void getChromats() {
        tracesStrongReference = new ArrayList<Trace>();
        traces = new WeakReference<List<Trace>>(tracesStrongReference);

        if(getId() < 0) {
            return;
        }

        try {
            String sql = "SELECT * FROM traces WHERE reaction = ?";

            PreparedStatement statement = BiocodeService.getInstance().getActiveLIMSConnection().createStatement(sql);
            statement.setInt(1, getId());
            ResultSet set = statement.executeQuery();

            List<Trace> result = new ArrayList<Trace>();
            while(set.next()) {
                result.add(new Trace(new ReactionUtilities.MemoryFile(set.getString("name"), set.getBytes("data")), set.getInt("id")));
            }
            statement.close();

            addTraces(result);
        } catch (SQLException e1) {
            Dialogs.showMessageDialog("Could not get the sequences: "+e1.getMessage());
        } catch (IOException e1) {
            Dialogs.showMessageDialog("Could not write temp files to disk: "+e1.getMessage());
        } catch (DocumentImportException e1) {
            Dialogs.showMessageDialog("Could not import the sequences.  Perhaps your traces have become corrupted in the LIMS database?: "+e1.getMessage());
        }
    }

    public boolean hasDownloadedChromats() {
        return getTraces() != null;
    }

    public String areReactionsValid(List<CycleSequencingReaction> reactions, JComponent dialogParent, boolean showDialogs) {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        String error = "";

        //List<Query> queries = new ArrayList<Query>();
        Set<String> samplesToGet = new HashSet<String>();

        Map<String, String> tissueMapping;
        try {
            tissueMapping = BiocodeService.getInstance().getReactionToTissueIdMapping("cyclesequencing", reactions);
        } catch (SQLException e) {
            return "Could not connect to the LIMS database";
        }

        for(Reaction reaction : reactions) {
            if(reaction.isEmpty()) {
                continue;
            }
            reaction.isError = false;
            Options option = reaction.getOptions();
            String tissue = tissueMapping.get(option.getValueAsString("extractionId"));
            if(tissue != null) {
                samplesToGet.add(tissue);
            }
        }

        if(samplesToGet.size() == 0) {
            return  error.length() == 0 ? null : error;
        }

        try {
            List<FimsSample> docList = fimsConnection.retrieveSamplesForTissueIds(samplesToGet);
            Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
            for(FimsSample sample : docList) {
                docMap.put(sample.getFimsAttributeValue(tissueField.getCode()).toString(), sample);
            }

            for(Reaction reaction : reactions) {
                Options op = reaction.getOptions();
                String extractionId = op.getValueAsString("extractionId");
                FimsSample currentFimsSample = docMap.get(tissueMapping.get(extractionId));
                if(currentFimsSample == null) {
//                    error += "The tissue sample '"+tissueMapping.get(extractionId)+"' does not exist in the database.\n";
//                    reaction.isError = true;
                }
                else {
                    reaction.setFimsSample(currentFimsSample);
                }
            }

        } catch (ConnectionException e) {
            return "Could not query the FIMS database.  "+e.getMessage();
        }



        //check the workflows exist in the database
        Set<String> workflowIdStrings = new HashSet<String>();
        for(Reaction reaction : reactions) {
            Object workflowId = reaction.getFieldValue("workflowId");
            if(!reaction.isEmpty() && reaction.getExtractionId().length() > 0 && (reaction.getLocus() == null || reaction.getLocus().length() == 0 || reaction.getLocus().equalsIgnoreCase("none"))) {
                reaction.setHasError(true);
                error += "The reaction in well "+reaction.getLocationString()+" does not have a locus set.<br>";
            }
            if((!reaction.isEmpty() && workflowId != null && reaction.getType() != Reaction.Type.Extraction)
            && (reaction.getWorkflow() == null || !reaction.getWorkflow().getName().equals(workflowId))){

                reaction.setWorkflow(null);
                if(workflowId.toString().length() > 0) {
                    workflowIdStrings.add(workflowId.toString());
                }
                
            }
        }

        if(workflowIdStrings.size() > 0) {
            try {
                Map<String,Workflow> map = BiocodeService.getInstance().getWorkflows(new ArrayList<String>(workflowIdStrings));

                for(Reaction reaction : reactions) {
                    Object workflowId = reaction.getFieldValue("workflowId");
                    if(workflowId != null && workflowId.toString().length() > 0) {
                        Workflow workflow = map.get(workflowId.toString());
                        if(workflow == null) {
                            error += "The workflow "+workflowId+" does not exist in the database.\n";
                        }
                        reaction.setWorkflow(workflow);
                    }
                }
            } catch (SQLException e) {
                return "Could not query the LIMS database.  "+e.getMessage();
            }
        }

        if(error.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>"+error+"<br>The affected reactions have been highlighted in yellow.";
        }
        return null;
    }

    /**
     * nullify the strong reference to trace documents to free up memory!.
     */
    public void purgeChromats() {
        if(tracesStrongReference != null) {
            tracesStrongReference = null;
        }
    }

    public Collection<Integer> getTracesToRemoveOnSave() {
        return tracesToRemoveOnSave;
    }

    public void addTraceToRemoveOnSave(int i) {
        tracesToRemoveOnSave.add(i);
    }

    public void clearTracesToRemoveOnSave() {
        tracesToRemoveOnSave.clear();
    }

    public List<Trace> getTraces() {
        return traces == null ? null : traces.get();
    }

    public void addTraces(List<Trace> traces) {
        if(this.traces == null || this.traces.get() == null) {
            tracesStrongReference = new ArrayList<Trace>();
            this.traces = new WeakReference<List<Trace>>(tracesStrongReference);
        }
        this.traces.get().addAll(traces);
    }

    public void setTraces(List<Trace> traces) {
        tracesStrongReference = traces;
        this.traces = new WeakReference<List<Trace>>(tracesStrongReference);
    }

    public void setChromats(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        if (tracesStrongReference == null) {
            tracesStrongReference = new ArrayList<Trace>();
        } else {
            tracesStrongReference.clear();
        }
        if (traces == null) {
            traces = new WeakReference<List<Trace>>(tracesStrongReference);
        }
        if(files == null) {
            return;
        }

        convertRawTracesToTraceDocuments(files);
    }

    public void addChromats(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        if(tracesStrongReference == null) {
            if(traces != null && traces.get() != null) {
                tracesStrongReference = traces.get();
            }
            else {
                getChromats();
            }
        }
        if(traces == null) {
            traces = new WeakReference<List<Trace>>(tracesStrongReference);
        }


        convertRawTracesToTraceDocuments(files);
    }

    private void convertRawTracesToTraceDocuments(List<ReactionUtilities.MemoryFile> files) throws IOException, DocumentImportException {
        List<AnnotatedPluginDocument> docs = new ArrayList<AnnotatedPluginDocument>();
        File tempFolder = null;
        for(ReactionUtilities.MemoryFile mFile : files) {
            tracesStrongReference.add(new Trace(mFile));
        }
    }

//    private void getChromats() {
//        try {
//            List<Trace> chromatFiles = ((CycleSequencingReaction) reaction).getChromats();
//            tracesStrongReference = new ArrayList<Trace>();
//            traces = new WeakReference<List<Trace>>(tracesStrongReference);
//            addTraces(chromatFiles);
//
//        } catch (SQLException e1) {
//            Dialogs.showMessageDialog("Could not get the sequences: "+e1.getMessage());
//        } catch (IOException e1) {
//            Dialogs.showMessageDialog("Could not write temp files to disk: "+e1.getMessage());
//        } catch (DocumentImportException e1) {
//            Dialogs.showMessageDialog("Could not import the sequences.  Perhaps your traces have become corrupted in the LIMS database?: "+e1.getMessage());
//        }
//    }

    @Override
    public List<DocumentField> getDisplayableFields() {
        List<DocumentField> fields = super.getDisplayableFields();
        fields.add(NUM_TRACES_FIELD);
        return fields;
    }

    @Override
    public String getDisplayableValue(DocumentField field) {
        if(NUM_TRACES_FIELD.getCode().equals(field.getCode())) {
            List<Trace> cachedTracesList = getTraces();
            if(cachedTracesList == null) {
                return ""+ cacheNumTraces;
            } else {
                return "" + cachedTracesList.size();
            }
        } else {
            return super.getDisplayableValue(field);
        }
    }
}
