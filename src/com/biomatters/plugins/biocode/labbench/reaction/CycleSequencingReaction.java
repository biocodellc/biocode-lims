package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
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
import java.sql.Connection;
import java.util.*;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 6:02:38 PM
 */
public class CycleSequencingReaction extends Reaction<CycleSequencingReaction>{
    private CycleSequencingOptions options;
    private boolean removeExistingTracesOnSave = true;

    public CycleSequencingReaction() {

    }

    public CycleSequencingReaction(ResultSet r) throws SQLException {
        this();
        init(r);
//        System.out.println(getWorkflow());
    }

    private Options init(ResultSet r) throws SQLException {
        setPlateId(r.getInt("cyclesequencing.plate"));
        setPosition(r.getInt("cyclesequencing.location"));
        setCreated(r.getTimestamp("cyclesequencing.date"));
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
            options.setValue("locus", r.getString("workflow.locus"));
        }



        options.getOption(ReactionOptions.RUN_STATUS).setValueFromString(r.getString("cyclesequencing.progress"));

        DocumentSelectionOption primerOption = (DocumentSelectionOption)options.getOption(CycleSequencingOptions.PRIMER_OPTION_ID);
        String primerName = r.getString("cyclesequencing.primerName");
        String primerSequence = r.getString("cyclesequencing.primerSequence");
        if(primerSequence.length() > 0) {
            primerOption.setValue(Arrays.asList(BiocodeUtilities.createPrimerDocument(primerName, primerSequence)));
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

//        String sequenceString = r.getString("cyclesequencing.sequences");
//        if(sequenceString != null && sequenceString.length() > 0) {
//            SAXBuilder builder = new SAXBuilder();
//            try {
//                Element sequenceElement = builder.build(new StringReader(sequenceString)).detachRootElement();
//                DefaultSequenceListDocument defaultSequenceListDocument = XMLSerializer.classFromXML(sequenceElement, DefaultSequenceListDocument.class);
//                ((CycleSequencingOptions)options).setSequences(defaultSequenceListDocument.getNucleotideSequences());
//            } catch (JDOMException e) {
//                e.printStackTrace();
//                throw new SQLException("Could not deserialize the sequences: "+e.getMessage());
//            } catch (IOException e) {
//                e.printStackTrace();
//                throw new SQLException("Could not read the sequences: "+e.getMessage());
//            } catch (XMLSerializationException e) {
//                e.printStackTrace();
//                throw new SQLException("Couldn't deserialize the sequences: "+e.getMessage());
//            }
//        }
        return options;
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
        return getOptions().getValueAsString("locus");
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
            return Arrays.asList(new DocumentField[] {
                    BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(),
                    new DocumentField("Primer", "", CycleSequencingOptions.PRIMER_OPTION_ID, String.class, true, false),
                    new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
            });
        }
        return Arrays.asList(new DocumentField[] {
                new DocumentField("Primer", "", CycleSequencingOptions.PRIMER_OPTION_ID, String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
        });
    }

    public void addSequences(List<NucleotideSequenceDocument> sequences, List<ReactionUtilities.MemoryFile> rawTraces) {
        if(sequences != null) {
            options.addSequences(sequences);
        }
        if(rawTraces != null) {
            options.addRawTraces(rawTraces);
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

    List<ReactionUtilities.MemoryFile> getChromats() throws SQLException {
        if(getId() < 0) {
            return new ArrayList<ReactionUtilities.MemoryFile>();
        }

        String sql = "SELECT * FROM traces WHERE reaction = ?";

        Connection connection = BiocodeService.getInstance().getActiveLIMSConnection().getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setInt(1, getId());
        ResultSet set = statement.executeQuery();

        List<ReactionUtilities.MemoryFile> result = new ArrayList<ReactionUtilities.MemoryFile>();
        while(set.next()) {
            result.add(new ReactionUtilities.MemoryFile(set.getString("name"), set.getBytes("data")));
        }
        statement.close();
        return result;
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

        Map<String, String> tissueMapping = null;
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
            List<FimsSample> docList = fimsConnection.getMatchingSamples(samplesToGet);
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
            if(!reaction.isEmpty() && (reaction.getLocus() == null || reaction.getLocus().length() == 0)) {
                reaction.setHasError(true);
                error += "The reaction "+reaction.getExtractionId()+" does not have a locus set.<br>";
            }
            if(!reaction.isEmpty() && workflowId != null && workflowId.toString().length() > 0 && reaction.getType() != Reaction.Type.Extraction) {
                if(reaction.getWorkflow() != null && reaction.getWorkflow().getName().equals(workflowId)){
                    continue;
                }
                else {
                    reaction.setWorkflow(null);
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
                        Workflow workflow = map.get(workflowId);
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

    public void purgeChromats() {
        options.purgeChromats();
    }

    public boolean removeExistingTracesOnSave() {
        return removeExistingTracesOnSave;
    }

    public void setRemoveExistingTracesOnSave(boolean b) {
        this.removeExistingTracesOnSave = b;
    }
}
