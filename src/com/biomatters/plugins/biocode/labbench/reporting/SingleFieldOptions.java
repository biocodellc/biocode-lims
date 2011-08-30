package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.List;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 5:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class SingleFieldOptions extends Options {

    private String reactionTypeString;

    public SingleFieldOptions(List<DocumentField> fimsFields) {
        super(SingleFieldOptions.class);
        init(fimsFields);
        initListeners();
    }


    public SingleFieldOptions(Element element) throws XMLSerializationException {
        super(element);
        initListeners();
    }

    public void init(List<DocumentField> fields) {
        beginAlignHorizontally("", false);
        List<OptionValue> fieldValue;
        fieldValue = ReportGenerator.getOptionValues(fields);

        addComboBoxOption("field", fields == null ? " whose " : "", fieldValue, fieldValue.get(0));
        addComboBoxOption("isValue", " is ", ReportGenerator.getEnumeratedFieldValues(null), ReportGenerator.getEnumeratedFieldValues(null).get(0));
        addStringOption("containsValue", " contains the value ", "");


        endAlignHorizontally();    
    }

    public void setFields(List<DocumentField> fields, String reactionType) {
        reactionTypeString = reactionType;
        List<OptionValue> optionValues = ReportGenerator.getOptionValues(fields);
        if(reactionType != null) {
            optionValues.addAll(0, ReportGenerator.getPossibleFields(reactionType, false));
        }
        ((ComboBoxOption)getOption("field")).setPossibleValues(optionValues);
    }
    
    public void initListeners() {
        final ComboBoxOption<OptionValue> reactionType = (ComboBoxOption)getOption("reactionType");
        final ComboBoxOption<OptionValue> fieldOption = (ComboBoxOption)getOption("field");
        final StringOption containsValueOption = (StringOption)getOption("containsValue");
        final ComboBoxOption<OptionValue> isValueOption = (ComboBoxOption<OptionValue>)getOption("isValue");

        if(reactionType != null) {
            SimpleListener reactionTypeListener = new SimpleListener() {
                public void objectChanged() {
                    fieldOption.setPossibleValues(ReportGenerator.getPossibleFields(reactionType.getValue().getName(), true));
                }
            };
            reactionType.addChangeListener(reactionTypeListener);
            reactionTypeListener.objectChanged();
        }

        SimpleListener fieldOptionListener = new SimpleListener() {
            public void objectChanged() {
                long time = System.currentTimeMillis();
                DocumentField field;
                if(reactionType != null) {
                    field = ReportGenerator.getField(reactionType.getValue().getName(), fieldOption.getValue().getName());
                }
                else if(reactionTypeString != null) {
                    field = ReportGenerator.getField(reactionTypeString, fieldOption.getValue().getName());    
                }
                else {
                    field = BiocodeService.getInstance().getReportingService().getReportGenerator().fimsToLims.getFimsOrLimsField(fieldOption.getValue().getName());
                }
                System.out.println("singleFieldOptions "+(System.currentTimeMillis()-time));
                if (field != null && field.isEnumeratedField()) {
                    containsValueOption.setVisible(false);
                    isValueOption.setVisible(true);
                    isValueOption.setPossibleValues(ReportGenerator.getEnumeratedFieldValues(field));
                } else {
                    isValueOption.setVisible(false);
                    containsValueOption.setVisible(true);
                }
            }
        };
        fieldOption.addChangeListener(fieldOptionListener);
        fieldOptionListener.objectChanged();
    }

    public String getTableName() {
        final ComboBoxOption<OptionValue> reactionType = (ComboBoxOption)getOption("reactionType");
        return reactionType.getValue().getName();
    }

    public String getFieldName() {
        final ComboBoxOption<OptionValue> fieldOption = (ComboBoxOption)getOption("field");
        return fieldOption.getValue().getName();
    }

    public String getFieldLabel() {
        final ComboBoxOption<OptionValue> fieldOption = (ComboBoxOption)getOption("field");
        return fieldOption.getValue().getLabel();    
    }

    public String getFriendlyDescription() {
        //todo: proper comparators
        return getFieldLabel()+" "+(isExactMatch() ? "is" : "contains the value")+" '"+(isExactMatch() ? getOption("isValue").getValue() : getOption("containsValue").getValue())+"'";
    }

    public Object getValue() {
        final StringOption containsValueOption = (StringOption)getOption("containsValue");
        final ComboBoxOption<OptionValue> isValueOption = (ComboBoxOption<OptionValue>)getOption("isValue");

        //special cases
        if(getFieldName().equals("cleanupPerformed") || getFieldName().equals("concentrationStored")) {
            return isValueOption.getValue().getName().equals("Yes");
        }

        String value;
        if(containsValueOption.isVisible()) {
            value = containsValueOption.getValue();
        }
        else {
            value = isValueOption.getValue().getName();
        }

        if(!isExactMatch()) {
            value = "%"+value+"%";
        }
        return value;
    }

    public String getComparitor() {
        return isExactMatch() ? "=" : "LIKE";
    }

    public boolean isExactMatch() {
        final ComboBoxOption<OptionValue> isValueOption = (ComboBoxOption<OptionValue>)getOption("isValue");
        return isValueOption.isVisible();
    }
    
}
