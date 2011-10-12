package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Arrays;

/**
 * @author Steve
 * @version $Id$
 */
public class ReactionFieldOptions extends Options {
    protected String LOCUS = "locus";
    protected static String FIELDS = "fields";
    protected static String REACTION_TYPE = "reactionType";
    protected static String CONDITION_FIELD = "condition";
    protected static String VALUE_FIELD = "value";
    protected static String ENUM_FIELD = "enumeratedValue";
    protected static String DATE_FIELD = "dateValue";
    protected static final OptionValue[] reactionTypes = new OptionValue[] {
            new OptionValue("Extraction", "Extraction reactions"),
            new OptionValue("PCR", "PCR reactions"),
            new OptionValue("CycleSequencing", "Sequencing reactions"),
            new OptionValue("assembly", "Sequences")
    };

    protected static final OptionValue[] stringComparators = new OptionValue[] {
            new OptionValue("contains", "contains"),
            new OptionValue("equal", "equals"),
            new OptionValue("notEqual", "does not equal")
    };


    protected static final OptionValue[] numbersComparators = new OptionValue[] {
            new OptionValue("equal", "equals"),
            new OptionValue("lessThan", "is less than"),
            new OptionValue("greaterThan", "is greater than"),
            new OptionValue("notEqual", "does not equal")
    };
    private FimsToLims fimsToLims;
    private boolean allowAll;

    public void setFimsToLims(FimsToLims fimsToLims) {
        this.fimsToLims = fimsToLims;
    }

    public ReactionFieldOptions(Class cl, FimsToLims fimsToLims, boolean includeValue, boolean allowAll, boolean includeLocus) {
        super(cl);
        this.fimsToLims = fimsToLims;
        this.allowAll = allowAll;
        init(includeValue, includeLocus);
        initListeners();
    }

    public ReactionFieldOptions(Element element) throws XMLSerializationException {
        super(element);
        allowAll = "true".equals(element.getChildText("allowAll"));
        initListeners();
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        element.addContent(new Element("allowAll").setText(""+allowAll));
        return element;
    }


    private void init(boolean includeValue, boolean includeLocus) {
        beginAlignHorizontally("", false);
        ComboBoxOption<OptionValue> reactionType = addComboBoxOption(REACTION_TYPE, "Reaction type ", reactionTypes, reactionTypes[0]);
        List<OptionValue> fieldValue = ReportGenerator.getPossibleFields(reactionType.getValue().getName(), includeValue, allowAll);
        addComboBoxOption(FIELDS, "Field to compare", fieldValue, fieldValue.get(0));
        if(includeValue) {
            Options.OptionValue[] values = new OptionValue[] {new OptionValue("None", "None")};
            ComboBoxOption<OptionValue> valuesOption = addComboBoxOption(ENUM_FIELD, "is", values, values[0]);
            ComboBoxOption<OptionValue> comparatorOption = addComboBoxOption(CONDITION_FIELD, "", values, values[0]);
            addDateOption(DATE_FIELD, "", new Date());
            StringOption stringOption = addStringOption(VALUE_FIELD, "", "");
            stringOption.setVisible(false);
                        
        }
        if(includeLocus) {
            List<OptionValue> loci = fimsToLims.getLociOptionValues();
            ComboBoxOption<OptionValue> locusOption = addComboBoxOption(LOCUS, "Locus", loci, loci.get(0));
            locusOption.setDisabledValue(loci.get(0));
        }
        endAlignHorizontally();
    }

    public void initListeners() {
        final ComboBoxOption<OptionValue> reactionType = (ComboBoxOption)getOption(REACTION_TYPE);
        final ComboBoxOption<OptionValue> field = (ComboBoxOption)getOption(FIELDS);
        final ComboBoxOption locus = (ComboBoxOption)getOption(LOCUS);
        final StringOption value = (StringOption)getOption(VALUE_FIELD);
        final ComboBoxOption enumValue = (ComboBoxOption)getOption(ENUM_FIELD);
        final ComboBoxOption condition = (ComboBoxOption)getOption(CONDITION_FIELD);
        final DateOption dateOption = (DateOption)getOption(DATE_FIELD);

        final boolean enumOnly = value == null;

        SimpleListener reactionTypesListener = new SimpleListener() {

            public void objectChanged() {
                field.setPossibleValues(ReportGenerator.getPossibleFields(reactionType.getValue().getName(), !enumOnly, allowAll));
                if(locus != null) {
                    locus.setEnabled(!reactionType.getValue().equals(reactionTypes[0]));
                }
            }
        };
        reactionType.addChangeListener(reactionTypesListener);
        reactionTypesListener.objectChanged();
        SimpleListener fieldListener = new SimpleListener() {
            public void objectChanged() {
                if (value != null) {
                    boolean enable = !allowAll || !field.getValue().equals(field.getPossibleOptionValues().get(0));
                    value.setEnabled(enable);
                    condition.setEnabled(enable);
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
                        dateOption.setVisible(false);
                        condition.setVisible(false);
                        enumValue.setPossibleValues(enumValues);
                        enumValue.setVisible(true);
                    } else {
                        condition.setVisible(true);
                        Class valueClass = getValueClass();
                        if(valueClass == Integer.class || valueClass == Double.class || valueClass == Date.class) {
                            condition.setPossibleValues(Arrays.asList(numbersComparators));
                        }
                        else {
                            condition.setPossibleValues(Arrays.asList(stringComparators));
                        }
                        value.setVisible(valueClass != Date.class);
                        dateOption.setVisible(valueClass == Date.class);
                        enumValue.setVisible(false);
                    }
                }
            };
            field.addChangeListener(listener);
            listener.objectChanged();
        }
    }

    public String getLocus() {
        ComboBoxOption<Options.OptionValue> locusOption = (ComboBoxOption<Options.OptionValue>)getOption(LOCUS);
        if(locusOption == null || !locusOption.isEnabled() || (locusOption.getValue().getName().equals("all") && locusOption.getValue().getLabel().equals("All..."))) {
            return null;
        }
        return locusOption.getValue().getName();
    }

    public Class getValueClass() {
        DocumentField field = ReportGenerator.getField(getReactionType(), getField());

        if(field != null && ReportGenerator.isBooleanField(field)) {
            return Boolean.class;
        }
        if(field != null && isCocktailField(field.getName())) {
            return Integer.class;
        }
        return field != null ? field.getValueType() : String.class;
    }

//    protected static final OptionValue[] stringComparators = new OptionValue[] {
//            new OptionValue("contains", "contains"),
//            new OptionValue("equal", "equals"),
//            new OptionValue("notEqual", "does not equal")
//    };
//
//
//    protected static final OptionValue[] numbersComparators = new OptionValue[] {
//            new OptionValue("equal", "equals"),
//            new OptionValue("lessThan", "is less than"),
//            new OptionValue("greaterThan", "is greater than"),
//            new OptionValue("notEqual", "does not equal")
//    };

    public String getComparator() {
        Option comparatorOption = getOption(CONDITION_FIELD);
        if(comparatorOption != null && comparatorOption.isVisible()) {
            String conditionCode = getValueAsString(CONDITION_FIELD);
            if("equal".equals(conditionCode)) {
                return "=";
            }
            else if("lessThan".equals(conditionCode)) {
                return "<";
            }
            else if("greaterThan".equals(conditionCode)) {
                return ">";
            }
            else if("notEqual".equals(conditionCode)) {
                return "<>";
            }
            else if("contains".equals(conditionCode)) {
                return "LIKE";
            } 
        }
        return "=";
    }

    private boolean isCocktailField(String fieldName) {
        return fieldName.toLowerCase().contains("cocktail");
    }



    public String getDisplayValue() {
        DocumentField field = ReportGenerator.getField(getReactionType(), getField());
        String value;
        if(getOption(ENUM_FIELD) != null && getOption(ENUM_FIELD).isVisible()) {
            return getValueAsString(ENUM_FIELD);
        }
        else {
            return  getValue(VALUE_FIELD) != null && getOption(VALUE_FIELD).isEnabled() ? getValueAsString(VALUE_FIELD) : null;
        }
    }

    public Object getValue() {
        DocumentField field = ReportGenerator.getField(getReactionType(), getField());
        String value;
        if(getOption(ENUM_FIELD) != null && getOption(ENUM_FIELD).isVisible()) {
            value = getValueAsString(ENUM_FIELD);
        }
        else {
            value =  getValue(VALUE_FIELD) != null && getOption(VALUE_FIELD).isEnabled() ? getValueAsString(VALUE_FIELD) : null;
        }
        if (value != null && field != null) {
            if (ReportGenerator.isBooleanField(field)) {//booleans
                if (value.toLowerCase().equals("yes") || value.toLowerCase().equals("true")) {
                    return true;
                }
                if (value.toLowerCase().equals("no") || value.toLowerCase().equals("false")) {
                    return false;
                }
            }
            if(isCocktailField(field.getName())) {
                return fimsToLims.getCocktailId(getReactionType(), value);
            }
        }
        if(field == null) {
            return null;
        }
        Class valueType = field.getValueType();
        if(Integer.class.equals(valueType)) {
            return Integer.parseInt(value);
        }
        if(Double.class.equals(valueType)) {
            return Double.parseDouble(value);
        }
        if(Date.class.equals(valueType)) {
            return getValue(DATE_FIELD);
        }
        if(!isExactMatch()) {
            return "%"+value+"%";   
        }
        return value;
    }

    public String getField() {
        return getValueAsString(FIELDS);
    }

    public String getReactionType(){
        return ((OptionValue)getValue(REACTION_TYPE)).getName();
    }

//    public String getComparator() {
//        if(getOption(ENUM_FIELD) != null && getOption(ENUM_FIELD).isVisible()) {
//            return "=";
//        }
//        return "like";
//    }

//    public String getSql(boolean fimsTable) {
//        String extraTable = fimsTable ? FimsToLims.FIMS_VALUES_TABLE : null;
//        return getSql(extraTable, false, null);
//    }

    public String getSql(List<String> extraTables, String extraWhere) {
        String fieldName = getField();
        boolean hasWorkflow = getLocus() != null;
        String extraTableString = extraTables != null && extraTables.size() > 0 ? StringUtilities.join(", ", extraTables) : null;
        String reactionType = ((OptionValue)getValue(REACTION_TYPE)).getName();
        if(reactionType.equals("Extraction")) {
            String start = "SELECT COUNT(extraction.id) FROM extraction " + (hasWorkflow ? ", workflow" : "") + (extraTables != null ? ", " + extraTableString : "")+" WHERE ";
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
            String start = "SELECT COUNT(pcr.id) FROM pcr, extraction, workflow "+(extraTableString != null ? ", "+extraTableString : "")+" WHERE ";
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
            String start = "SELECT COUNT(cyclesequencing.id) FROM cyclesequencing, extraction, workflow "+(extraTableString != null ? ", "+extraTableString : "")+" WHERE ";
            List<String> terms = new ArrayList<String>();
            terms.add("cyclesequencing.workflow = workflow.id AND extraction.id = workflow.extractionId");
            if(extraWhere != null) {
                terms.add(extraWhere);
            }
            if(hasWorkflow && getLocus() != null) {
                terms.add("workflow.locus='"+getLocus()+"'");
            }
            if(getOption(VALUE_FIELD) == null || getValue() != null) {
                terms.add(ReportGenerator.getTableFieldName("cyclesequencing", fieldName)+" "+getComparator()+" "+"?");
            }
            return getSql(start, terms);
        }
        else if(reactionType.equals("assembly")) {
            String start = "SELECT COUNT(assembly.id) from assembly, extraction, workflow "+(extraTableString != null ? ", "+extraTableString : "")+" WHERE ";
            List<String> terms = new ArrayList<String>();
            terms.add("assembly.workflow = workflow.id AND workflow.extractionId = extraction.id");
            if(extraWhere != null) {
                terms.add(extraWhere);
            }
            if(hasWorkflow && getLocus() != null) {
                terms.add("workflow.locus='"+getLocus()+"'");
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

    public boolean isExactMatch() {
        ComboBoxOption condition = (ComboBoxOption)getOption(CONDITION_FIELD);
        if(condition != null && condition.isEnabled() && condition.isVisible()) {
            return !condition.getValue().toString().equals("contains");
        }
        return true;
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
        String locusString;
        if(locus == null || ((locus.getName().equals("all") && locus.getLabel().equals("All...")))) {
            locusString = "";
        }
        else {
            locusString = " ["+getValueAsString(LOCUS)+"]";
        }
        if(getValue() == null) {
            return niceReactionName+locusString;
        }
        return reactionType+" "+getField()+" "+getComparator()+" "+getDisplayValue()+locusString;
    }
}
