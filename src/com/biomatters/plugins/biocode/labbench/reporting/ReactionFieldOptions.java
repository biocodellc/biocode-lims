package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
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
    protected static String ENUM_FIELD = "enumeratedValue";
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
            Options.OptionValue[] values = new OptionValue[] {new OptionValue("None", "None")};
            ComboBoxOption<OptionValue> valuesOption = addComboBoxOption(ENUM_FIELD, "is", values, values[0]);
            StringOption stringOption = addStringOption(VALUE_FIELD, "has the value", "");
            stringOption.setVisible(false);
                        
        }
        List<OptionValue> loci = fimsToLims.getLoci();
        ComboBoxOption<OptionValue> locusOption = addComboBoxOption(LOCUS, "Locus", loci, loci.get(0));
        locusOption.setDisabledValue(loci.get(0));
        endAlignHorizontally();
    }

    public void initListeners() {
        final ComboBoxOption<OptionValue> reactionType = (ComboBoxOption)getOption(REACTION_TYPE);
        final ComboBoxOption<OptionValue> field = (ComboBoxOption)getOption(FIELDS);
        final ComboBoxOption locus = (ComboBoxOption)getOption(LOCUS);
        final StringOption value = (StringOption)getOption(VALUE_FIELD);
        final ComboBoxOption enumValue = (ComboBoxOption)getOption(ENUM_FIELD);

        final boolean enumOnly = value == null;

        SimpleListener reactionTypesListener = new SimpleListener() {

            public void objectChanged() {
                field.setPossibleValues(ReportGenerator.getPossibleFields(reactionType.getValue().getName(), enumOnly));
                locus.setEnabled(!reactionType.getValue().equals(reactionTypes[0]));
            }
        };
        reactionType.addChangeListener(reactionTypesListener);
        reactionTypesListener.objectChanged();
        SimpleListener fieldListener = new SimpleListener() {
            public void objectChanged() {
                if (value != null) {
                    value.setEnabled(!field.getValue().equals(field.getPossibleOptionValues().get(0)));
                }
            }
        };
        field.addChangeListener(fieldListener);
        fieldListener.objectChanged();
        if(!enumOnly) {
            SimpleListener listener = new SimpleListener() {
                public void objectChanged() {
                    List<OptionValue> enumValues = ReportGenerator.getEnumeratedFieldValues(reactionType.getValue().getName(), field.getValue().getName());
                    if (enumValues != null) {
                        value.setVisible(false);
                        enumValue.setPossibleValues(enumValues);
                        enumValue.setVisible(true);
                    } else {
                        value.setVisible(true);
                        enumValue.setVisible(false);
                    }
                }
            };
            field.addChangeListener(listener);
            listener.objectChanged();
        }
    }

    private String getLocus() {
        ComboBoxOption<Options.OptionValue> locusOption = (ComboBoxOption<Options.OptionValue>)getOption(LOCUS);
        if(!locusOption.isEnabled() || (locusOption.getValue().getName().equals("all") && locusOption.getValue().getLabel().equals("All..."))) {
            return null;
        }
        return locusOption.getValue().getName();
    }

    public String getValue() {
        if(getOption(ENUM_FIELD) != null && getOption(ENUM_FIELD).isVisible()) {
            return getValueAsString(ENUM_FIELD);
        }
        return getValue(VALUE_FIELD) != null && getOption(VALUE_FIELD).isEnabled() ? getValueAsString(VALUE_FIELD) : null;
    }

    public String getField() {
        return getValueAsString(FIELDS);
    }

    public String getComparator() {
        if(getOption(ENUM_FIELD) != null && getOption(ENUM_FIELD).isVisible()) {
            return "=";
        }
        return "like";
    }

//    public String getSql(boolean fimsTable) {
//        String extraTable = fimsTable ? "fims_values" : null;
//        return getSql(extraTable, false, null);
//    }

    public String getSql(String extraTable, String extraWhere) {
        String fieldName = getField();
        boolean hasWorkflow = getLocus() != null;
        String reactionType = ((OptionValue)getValue(REACTION_TYPE)).getName();
        if(reactionType.equals("Extraction")) {
            String start = "SELECT COUNT(extraction.id) FROM extraction " + (hasWorkflow ? ", workflow" : "") + (extraTable != null ? ", " + extraTable : "")+" WHERE ";
            List<String> terms = new ArrayList<String>();
            if(hasWorkflow) {
                terms.add("workflow.extractionId = extracion.id");
            }
            if(extraWhere != null) {
                terms.add(extraWhere);
            }
            if(getOption(VALUE_FIELD) == null || getValue() != null) {
                terms.add(ReportGenerator.getTableFieldName("extraction", fieldName)+" "+getComparator()+" "+"?");
            }
            return getSql(start, terms);
        }
        else if(reactionType.equals("PCR")) {
            String start = "SELECT COUNT(pcr.id) FROM pcr, extraction, workflow "+(extraTable != null ? ", "+extraTable : "")+" WHERE ";
            List<String> terms = new ArrayList<String>();
            terms.add("pcr.workflow = workflow.id AND extraction.id = workflow.extractionId");
            if(extraWhere != null) {
                terms.add(extraWhere);
            }
            if(hasWorkflow && getLocus() != null) {
                terms.add("workflow.locus='"+getLocus()+"'");
            }
            if(getOption(VALUE_FIELD) == null || getValue() != null) {
                terms.add(ReportGenerator.getTableFieldName("pcr", fieldName)+" "+getComparator()+" "+"?");
            }
            return getSql(start, terms);
        }
        else if(reactionType.equals("CycleSequencing")) {
            String start = "SELECT COUNT(cyclesequencing.id) FROM cyclesequencing, extraction, workflow "+(extraTable != null ? ", "+extraTable : "")+" WHERE ";
            List<String> terms = new ArrayList<String>();
            terms.add("cyclesequencing.workflow = workflow.id AND extraction.id = workflow.extractionId");
            if(extraWhere != null) {
                terms.add(extraWhere);
            }
            if(hasWorkflow && getLocus() != null) {
                terms.add("workflow.locus='"+getLocus());
            }
            if(getOption(VALUE_FIELD) == null || getValue() != null) {
                terms.add(ReportGenerator.getTableFieldName("cyclesequencing", fieldName)+" "+getComparator()+" "+"?");
            }
            return getSql(start, terms);
        }
        else if(reactionType.equals("assembly")) {
            String start = "SELECT COUNT(assembly.id) from assembly, extraction, workflow "+(extraTable != null ? ", "+extraTable : "")+" WHERE ";
            List<String> terms = new ArrayList<String>();
            terms.add("assembly.workflow = workflow.id AND workflow.extractionId = extraction.id");
            if(extraWhere != null) {
                terms.add(extraWhere);
            }
            if(hasWorkflow && getLocus() != null) {
                terms.add("workflow.locus='"+getLocus());
            }
            if(getOption(VALUE_FIELD) == null || getValue() != null) {
                terms.add(ReportGenerator.getTableFieldName("assembly", fieldName)+" "+getComparator()+" "+"?");
            }
            return getSql(start, terms);
        }
        else {
            throw new RuntimeException("Unknown reaction type: "+reactionType);
        }
    }

    private String getSql(String start, List<String> terms) {
        return start + " " + StringUtilities.join(" AND ", terms);
    }

    public String getTable() {
        String table = getValueAsString(REACTION_TYPE);
        return table.toLowerCase();
    }

    public String getFriendlyTableName() {
        OptionValue value = (OptionValue)getValue(REACTION_TYPE);
        return value.getLabel();
    }

    public String getNiceName() {
        String reactionType = ((OptionValue)getValue(REACTION_TYPE)).getName();
        String niceReactionName = ((OptionValue)getValue(REACTION_TYPE)).getLabel();
        OptionValue locus = (OptionValue)getValue(LOCUS);
        String locusString = " ["+getValueAsString(LOCUS)+"]";
        if((locus.getName().equals("all") && locus.getLabel().equals("All..."))) {
            locusString = "";
        }
        if(getValue() == null) {
            return niceReactionName+locusString;
        }
        return reactionType+" "+getField()+" "+getComparator()+" "+getValue()+locusString;
    }
}
