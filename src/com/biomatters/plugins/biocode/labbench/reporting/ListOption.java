package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.ListSelectionEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.awt.*;

import org.jdom.Element;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 15/12/2011 10:55:14 AM
 */


public class ListOption extends Options.Option<List<Options.OptionValue>, JPanel>{

    private static final String SEPARATOR = ",";
    private List<Options.OptionValue> possibleValues;
    private boolean settingValue = false;

    public ListOption(Element element) throws XMLSerializationException {
        super(element);
        possibleValues = new ArrayList<Options.OptionValue>();
        Element possibleValuesElement = element.getChild("possibleValues");
        for(Element e : possibleValuesElement.getChildren("possibleValue")) {
            possibleValues.add(XMLSerializer.classFromXML(e, Options.OptionValue.class));
        }
    }

    public ListOption(String name, String label, List<Options.OptionValue> possibleValues, List<Options.OptionValue> defaultValue) {
        super(name, label, defaultValue);
        this.possibleValues = possibleValues;
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        Element possibleValues = new Element("possibleValues");
        for(Options.OptionValue possibleValue : this.possibleValues) {
            possibleValues.addContent(XMLSerializer.classToXML("possibleValue", possibleValue));
        }
        element.addContent(possibleValues);
        return element;
    }

    public List<Options.OptionValue> getValueFromString(String valueString) {
        String[] values = valueString.split(SEPARATOR);
        List<Options.OptionValue> optionValues = new ArrayList<Options.OptionValue>();
        for(String value : values) {
            Options.OptionValue optionValue = getOptionValue(value);
            if(optionValue != null) {
                optionValues.add(optionValue);
            }
        }
        return optionValues;
    }

    @Override
    protected void handleSetEnabled(JPanel component, boolean enabled) {
        super.handleSetEnabled(component, enabled);    //To change body of overridden methods use File | Settings | File Templates.
    }

    protected JPanel createComponent() {
        JPanel panel = new JPanel(new BorderLayout());
        final JList list = new JList(getLabelsFromOptionValues(possibleValues));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        list.addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                setValue(getOptionValuesFrom(list.getSelectedIndices()));
            }
        });
        return panel;
    }

    protected void setValueOnComponent(JPanel component, List<Options.OptionValue> values) {
        if(settingValue) {
            return;
        }
        settingValue = true;
        JScrollPane scroller = (JScrollPane)component.getComponent(0);
        JList list = (JList)scroller.getViewport().getView();
        int[] selectedIndices = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Options.OptionValue value = values.get(i);
            selectedIndices[i] = possibleValues.indexOf(value);
        }
        list.setSelectedIndices(selectedIndices);
        settingValue = false;
    }

    private List<Options.OptionValue> getOptionValuesFrom(int[] selectedIndices) {
        List<Options.OptionValue> optionValues = new ArrayList<Options.OptionValue>();
        for(int i : selectedIndices) {
            optionValues.add(possibleValues.get(i));
        }
        return optionValues;
    }

    private static Vector<String> getLabelsFromOptionValues(List<Options.OptionValue> optionValues) {
        Vector<String> labels = new Vector<String>();
        for(Options.OptionValue optionValue : optionValues) {
            labels.add(optionValue.getLabel());
        }
        return labels;
    }

    @Override
    public String getValueAsString(List<Options.OptionValue> values) {
        return StringUtilities.join(SEPARATOR, values);
    }



    private Options.OptionValue getOptionValue(String name) {
        for(Options.OptionValue optionValue : possibleValues) {
            if(optionValue.getName().equals(name)) {
                return optionValue;
            }
        }
        return null;
    }
}
