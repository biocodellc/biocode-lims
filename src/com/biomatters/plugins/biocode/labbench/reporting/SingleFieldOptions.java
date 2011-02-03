package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 29/01/11
 * Time: 5:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class SingleFieldOptions extends Options {
    
    public SingleFieldOptions(){
        super(SingleFieldOptions.class);
        init();
        initListeners();
    }
    
    public SingleFieldOptions(Element e) throws XMLSerializationException{
        super(e);
        initListeners();
    }


    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        super.fromXML(element);
        initListeners();
    }

    public SingleFieldOptions(Class cl) {
        super(cl);
        init();
        initListeners();
    }

    public SingleFieldOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
        initListeners();
    }

    public void init() {
        beginAlignHorizontally("", false);
        Options.OptionValue[] reactionTypes = new Options.OptionValue[] {
                new Options.OptionValue("Extraction", "Extraction reactions"),
                new Options.OptionValue("PCR", "PCR reactions"),
                new Options.OptionValue("CycleSequencing", "Sequencing reactions"),
                new Options.OptionValue("Sequences", "Sequences")
        };
        final Options.ComboBoxOption<Options.OptionValue> reactionType = addComboBoxOption("reactionType", "Number of ", reactionTypes, reactionTypes[0]);
        List<OptionValue> fieldValue = ReportGenerator.getPossibleFields(reactionType.getValue().getName());
        addComboBoxOption("field", " whose ", fieldValue, fieldValue.get(0));

        addComboBoxOption("isValue", " is ", ReportGenerator.getEnumeratedFieldValues(null), ReportGenerator.getEnumeratedFieldValues(null).get(0));
        addStringOption("containsValue", " contains the value ", "");


        endAlignHorizontally();    
    }
    
    public void initListeners() {
        final ComboBoxOption<OptionValue> reactionType = (ComboBoxOption)getOption("reactionType");
        final ComboBoxOption<OptionValue> fieldOption = (ComboBoxOption)getOption("field");
        final StringOption containsValueOption = (StringOption)getOption("containsValue");
        final ComboBoxOption<OptionValue> isValueOption = (ComboBoxOption<OptionValue>)getOption("isValue");
        SimpleListener reactionTypeListener = new SimpleListener() {
            public void objectChanged() {
                fieldOption.setPossibleValues(ReportGenerator.getPossibleFields(reactionType.getValue().getName()));
            }
        };
        reactionType.addChangeListener(reactionTypeListener);
        reactionTypeListener.objectChanged();

        SimpleListener fieldOptionListener = new SimpleListener() {
            public void objectChanged() {
                DocumentField field = ReportGenerator.getField(reactionType.getValue().getName(), fieldOption.getValue().getName());
                if (field.isEnumeratedField()) {
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

    public boolean isExactMatch() {
        final ComboBoxOption<OptionValue> isValueOption = (ComboBoxOption<OptionValue>)getOption("isValue");
        return isValueOption.isVisible();
    }
    
}
