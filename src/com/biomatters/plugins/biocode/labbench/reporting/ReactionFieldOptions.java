package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.List;

/**
 * @author Steve
 * @version $Id$
 */
public class ReactionFieldOptions extends Options {
    protected String LOCUS = "locus";
    protected static String FIELDS = "fields";
    protected static String REACTION_TYPE = "reactionType";
    protected static String VALUE_FIELD = "value";
    protected static final OptionValue[] reactionTypes = new OptionValue[] {
            new OptionValue("Extraction", "Extraction reactions"),
            new OptionValue("PCR", "PCR reactions"),
            new OptionValue("CycleSequencing", "Sequencing reactions"),
            new OptionValue("assembly", "Sequences")
    };


    public ReactionFieldOptions(Class cl, FimsToLims fimsToLims, boolean includeValue) {
        super(cl);
        init(fimsToLims, includeValue);
        initListeners();
    }

    public ReactionFieldOptions(Element element) throws XMLSerializationException {
        super(element);
        initListeners();
    }

    private void init(FimsToLims fimsToLims, boolean includeValue) {
        beginAlignHorizontally("", false);
        ComboBoxOption<OptionValue> reactionType = addComboBoxOption(REACTION_TYPE, "Reaction type ", reactionTypes, reactionTypes[0]);
        List<OptionValue> fieldValue = ReportGenerator.getPossibleFields(reactionType.getValue().getName(), !includeValue);
        addComboBoxOption(FIELDS, "Field to compare", fieldValue, fieldValue.get(0));
        if(includeValue) {
            addStringOption(VALUE_FIELD, "has the value", "");
        }
        addComboBoxOption(LOCUS, "Locus", fimsToLims.getLoci(), fimsToLims.getLoci().get(0));
        endAlignHorizontally();
    }

    public void initListeners() {
        final ComboBoxOption<OptionValue> reactionType = (ComboBoxOption)getOption(REACTION_TYPE);
        final ComboBoxOption field = (ComboBoxOption)getOption(FIELDS);
        final ComboBoxOption locus = (ComboBoxOption)getOption(LOCUS);

        reactionType.addChangeListener(new SimpleListener(){
            final boolean enumOnly = getOption(VALUE_FIELD) == null;
            public void objectChanged() {
                field.setPossibleValues(ReportGenerator.getPossibleFields(reactionType.getValue().getName(), enumOnly));
                locus.setEnabled(!reactionType.getValue().equals(reactionTypes[0]));
            }
        });
    }

    private String getLocus() {
        ComboBoxOption<Options.OptionValue> locusOption = (ComboBoxOption<Options.OptionValue>)getOption(LOCUS);
        if(!locusOption.isEnabled() || (locusOption.getValue().getName().equals("all") && locusOption.getValue().getLabel().equals("All..."))) {
            return null;
        }
        return locusOption.getValue().getName();
    }

    public String getValue() {
        return getValueAsString(VALUE_FIELD);
    }

    public String getField() {
        return getValueAsString(FIELDS);
    }

    public String getComparator() {
        return "like";
    }

//    public String getSql(boolean fimsTable) {
//        String extraTable = fimsTable ? "fims_values" : null;
//        return getSql(extraTable, false, null);
//    }

    public String getSql(String extraTable, String extraWhere) {
        String fieldName = getValueAsString(FIELDS);
        boolean hasWorkflow = getLocus() != null;
        String reactionType = ((OptionValue)getValue(REACTION_TYPE)).getName();
        if(reactionType.equals("Extraction")) {
            return "SELECT COUNT(extraction.id) FROM extraction "+(hasWorkflow ? ", workflow" : "")+(extraTable != null ? ", "+extraTable : "")+" WHERE "+(hasWorkflow ? "workflow.extractionId = extracion.id AND " : "")+(extraWhere != null ? extraWhere+" AND " : "")+ ReportGenerator.getTableFieldName("extraction", fieldName)+"=?";
        }
        else if(reactionType.equals("PCR")) {
            return "SELECT COUNT(pcr.id) FROM pcr, extraction, workflow "+(extraTable != null ? ", "+extraTable : "")+" WHERE pcr.workflow = workflow.id AND extraction.id = workflow.extractionId "+(extraWhere != null ? " AND "+extraWhere : "")+(hasWorkflow && getLocus() != null ? " AND workflow.locus='"+getLocus()+"'" : "")+" AND "+ReportGenerator.getTableFieldName("pcr", fieldName)+"=?";
        }
        else if(reactionType.equals("CycleSequencing")) {
            return "SELECT COUNT(cyclesequencing.id) FROM cyclesequencing, extraction, workflow "+(extraTable != null ? ", "+extraTable : "")+" WHERE cyclesequencing.workflow = workflow.id AND extraction.id = workflow.extractionId "+(extraWhere != null ? " AND "+extraWhere : "")+(hasWorkflow && getLocus() != null ? " AND workflow.locus='"+getLocus()+"'" : "")+" AND "+ReportGenerator.getTableFieldName("cyclesequencing", fieldName)+"=?";
        }
        else if(reactionType.equals("assembly")) {
            return "SELECT COUNT(assembly.id) from assembly, extraction, workflow "+(extraTable != null ? ", "+extraTable : "")+" WHERE assembly.workflow = workflow.id AND workflow.extractionId = extraction.id "+(extraWhere != null ? " AND "+extraWhere : "")+(hasWorkflow && getLocus() != null ? " AND workflow.locus='"+getLocus()+"'" : "")+" AND "+ReportGenerator.getTableFieldName("assembly", fieldName)+"=?";
        }
        else {
            throw new RuntimeException("Unknown reaction type: "+reactionType);
        }
    }

    public String getTable() {
        String table = getValueAsString(REACTION_TYPE);
        return table.toLowerCase();
    }
}
