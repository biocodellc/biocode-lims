package com.biomatters.plugins.moorea.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.Workflow;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;
import com.biomatters.plugins.moorea.labbench.plates.Plate;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.awt.*;
import java.io.IOException;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 24/06/2009 6:02:38 PM
 */
public class CycleSequencingReaction extends Reaction{
    private CycleSequencingOptions options;

    public CycleSequencingReaction() {
        options = new CycleSequencingOptions(this.getClass());
    }

    public CycleSequencingReaction(ResultSet r) throws SQLException {
        this();
        init(r);
        System.out.println(getWorkflow());
    }

    private Options init(ResultSet r) throws SQLException {
        setPlateId(r.getInt("cyclesequencing.plate"));
        setPosition(r.getInt("cyclesequencing.location"));
        setCreated(r.getDate("cyclesequencing.date"));
        setId(r.getInt("cyclesequencing.id"));
        Options options = getOptions();
        options.setValue("extractionId", r.getString("cyclesequencing.extractionId"));
        String s = r.getString("workflow.name");
        if(s != null) {
            options.setValue("workflowId", s);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("cyclesequencing.extractionId")));
        }


        options.getOption("runStatus").setValueFromString(r.getString("cyclesequencing.progress"));

        PrimerOption primerOption = (PrimerOption)options.getOption(CycleSequencingOptions.PRIMER_OPTION_ID);
        String primerName = r.getString("cyclesequencing.primerName");
        String primerSequence = r.getString("cyclesequencing.primerSequence");
        primerOption.setAndAddValue(primerName, primerSequence);
        options.setValue("prAmount", r.getInt("cyclesequencing.primerAmount"));
        options.setValue("notes", r.getString("cyclesequencing.notes"));
        options.setValue("runStatus", r.getString("cyclesequencing.progress"));
        options.setValue("cocktail", r.getString("cyclesequencing.cocktail"));
        options.setValue("cleanupPerformed", r.getBoolean("cyclesequencing.cleanupPerformed"));
        options.setValue("cleanupMethod", r.getBoolean("cyclesequencing.cleanupMethod"));

        setPlateName(r.getString("plate.name"));
        setLocationString(Plate.getWellName(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))));

        int thermocycleId = r.getInt("plate.thermocycle");
        if(thermocycleId >= 0) {
            for(Thermocycle tc : MooreaLabBenchService.getInstance().getCycleSequencingThermocycles()) {
                if(tc.getId() == thermocycleId) {
                    setThermocycle(tc);
                    break;
                }
            }
        }

        String sequenceString = r.getString("cyclesequencing.sequences");
        if(sequenceString != null && sequenceString.length() > 0) {
            SAXBuilder builder = new SAXBuilder();
            try {
                Element sequenceElement = builder.build(new StringReader(sequenceString)).detachRootElement();
                DefaultSequenceListDocument defaultSequenceListDocument = XMLSerializer.classFromXML(sequenceElement, DefaultSequenceListDocument.class);
                ((CycleSequencingOptions)options).setSequences(defaultSequenceListDocument.getNucleotideSequences());
            } catch (JDOMException e) {
                e.printStackTrace();
                throw new SQLException("Could not deserialize the sequences: "+e.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                throw new SQLException("Could not read the sequences: "+e.getMessage());
            } catch (XMLSerializationException e) {
                e.printStackTrace();
                throw new SQLException("Couldn't deserialize the sequences: "+e.getMessage());
            }
        }
        return options;
    }

    public Type getType() {
        return Type.CycleSequencing;
    }

    public ReactionOptions getOptions() {
        return options;
    }

    public void setOptions(ReactionOptions op) {
        if(!(op instanceof CycleSequencingOptions)) {
            throw new IllegalArgumentException("Options must be instances of CycleSequencingOptions");
        }
        this.options = (CycleSequencingOptions)op;
    }

    public Cocktail getCocktail() {
        String cocktailId = ((Options.OptionValue)getOptions().getOption("cocktail").getValue()).getName();
        for(Cocktail c : new CycleSequencingCocktail().getAllCocktailsOfType()) {
            if((""+c.getId()).equals(cocktailId)) {
                return c;
            }
        }
        return null;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(new DocumentField[] {
                new DocumentField("Tissue ID", "", "tissueId", String.class, true, false),
                new DocumentField("Primer", "", CycleSequencingOptions.PRIMER_OPTION_ID, String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
        });
    }

    public void addSequences(List<NucleotideSequenceDocument> sequences) {
        options.addSequences(sequences);
    }

    public Color _getBackgroundColor() {
        String runStatus = options.getValueAsString("runStatus");
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

    public String areReactionsValid(List<? extends Reaction> reactions) {
        if(!MooreaLabBenchService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        String error = "";

        List<Query> queries = new ArrayList<Query>();

        Map<String, String> tissueMapping = null;
        try {
            tissueMapping = MooreaLabBenchService.getInstance().getReactionToTissueIdMapping("pcr", reactions);
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
                Query fieldQuery = Query.Factory.createFieldQuery(tissueField, Condition.EQUAL, tissue);
                if(!queries.contains(fieldQuery)) {
                     queries.add(fieldQuery);
                }
            }
        }

        if(queries.size() == 0) {
            return  error.length() == 0 ? null : error;
        }
        Query orQuery = Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.<String, Object>emptyMap());

        try {
            List<FimsSample> docList = fimsConnection.getMatchingSamples(orQuery);
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
                Map<String,Workflow> map = MooreaLabBenchService.getInstance().getWorkflows(new ArrayList<String>(workflowIdStrings));

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
}
