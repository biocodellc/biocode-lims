package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.GButton;
import com.biomatters.geneious.publicapi.components.Dialogs;
import org.jdom.Element;
import org.virion.jam.panels.OptionsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 */
public class ButtonMultipleOptions extends Options {
    private Options masterOptions;
    private String addButtonText;
    private List<Options> optionsList;
    OptionsToLabel optionsToLabel;

    public ButtonMultipleOptions(Class cl, Options masterOptions, String addButtonText, OptionsToLabel optionsToLabel) {
        super(cl);
        this.masterOptions = masterOptions;
        this.addButtonText = addButtonText;
        this.optionsToLabel = optionsToLabel;
        optionsList = new ArrayList<Options>();
    }

    public ButtonMultipleOptions(Class cl, String preferenceNameSuffix, Options masterOptions, String addButtonText, OptionsToLabel optionsToLabel) {
        super(cl, preferenceNameSuffix);
        this.masterOptions = masterOptions;
        this.addButtonText = addButtonText;
        this.optionsToLabel = optionsToLabel;
        optionsList = new ArrayList<Options>();
    }

    public ButtonMultipleOptions(Element element) throws XMLSerializationException {
        super(element);
        masterOptions = XMLSerializer.classFromXML(element.getChild("MasterOptions"), Options.class);
        optionsList = new ArrayList<Options>();
        for(Element e : element.getChildren("ButtonMultipleOptions")) {
            optionsList.add(XMLSerializer.classFromXML(e, Options.class));
        }
        addButtonText = element.getChildText("AddButtonText");
        String optionsToLabelName = element.getChildText("OptionsToLabel");
        if(optionsToLabelName != null) {
            Class optionsToLabelClass = null;
            try {
                optionsToLabelClass = Class.forName(optionsToLabelName);
                optionsToLabel = (OptionsToLabel)optionsToLabelClass.newInstance();
            } catch (ClassNotFoundException e) {
                throw new XMLSerializationException("Could not find the class "+optionsToLabelName);
            } catch (IllegalAccessException e) {
                throw new XMLSerializationException("Could not instantiate the class "+optionsToLabelName+".  Make sure it has a public empty constructor.  "+e.toString());
            } catch (InstantiationException e) {
                throw new XMLSerializationException("Could not instantiate the class "+optionsToLabelName+".  Make sure it has a public empty constructor.  "+e.toString());
            }
        }
    }

    public void setMultipleOptions(String name, Options multipleOptions) {
        addMultipleOptions(name, multipleOptions, false);
    }

    @Override
    protected JPanel createPanel() {
        final GPanel panel = new GPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        for(final Options options : optionsList) {
            final GPanel optionsPanel = getPanelForOptions(panel, options);
            panel.add(optionsPanel);
            panel.revalidate();
        }
        GPanel addButtonPanel = new GPanel(new FlowLayout());
        GButton addButton = new GButton(addButtonText != null ? addButtonText : "Add");
        addButtonPanel.add(addButton);
        addButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                try {
                    Options newOptions = XMLSerializer.classFromXML(XMLSerializer.classToXML("options", masterOptions), Options.class);
                    if(Dialogs.showOptionsDialog(newOptions, "Edit", false, panel)) {
                        GPanel optionsPanel = getPanelForOptions(panel, newOptions);
                        panel.add(optionsPanel, panel.getComponentCount()-1);
                        panel.revalidate();
                        if(optionsToLabel != null) {
                            for(Component c : optionsPanel.getComponents()) {
                                if(c instanceof JLabel) {
                                    ((JLabel)c).setText(optionsToLabel.getLabel(newOptions));
                                }
                            }
                        }
                    }
                } catch (XMLSerializationException e1) {
                    throw new RuntimeException("Could not serialize an instance of "+masterOptions.getClass());
                }
            }
        });
        panel.add(addButtonPanel);

        return panel;
    }

    public List<Options> getMultipleOptions() {
        return new ArrayList<Options>(optionsList);
    }

    private GPanel getPanelForOptions(final GPanel panel, final Options options) {
        final GPanel optionsPanel = new GPanel(new BorderLayout());
        optionsPanel.setBorder(new EmptyBorder(0,10,0,0));
        GPanel buttonPanel = new GPanel(new FlowLayout());
        GButton editButton = new GButton("Edit");
        buttonPanel.add(editButton);
        editButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                try {
                    Options tempOptions = XMLSerializer.classFromXML(XMLSerializer.classToXML("options", options), Options.class);
                    if(Dialogs.showOptionsDialog(tempOptions, "Edit", false, panel)) {
                        options.valuesFromXML(tempOptions.valuesToXML("options"));
                        if(optionsToLabel != null) {
                            for(Component c : optionsPanel.getComponents()) {
                                if(c instanceof JLabel) {
                                    ((JLabel)c).setText(optionsToLabel.getLabel(options));
                                }
                            }
                        }
                    }
                } catch (XMLSerializationException e1) {
                    throw new RuntimeException("Could not serialize an instance of "+options.getClass());
                }
            }
        });

        GButton removeButton = new GButton("Remove");
        buttonPanel.add(removeButton);
        removeButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                optionsList.remove(options);
                panel.remove(optionsPanel);
                panel.revalidate();
            }
        });
        optionsPanel.add(new JLabel("TODO: label here"), BorderLayout.CENTER);
        optionsPanel.add(buttonPanel, BorderLayout.EAST);
        return optionsPanel;
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        element.addContent(XMLSerializer.classToXML("MasterOptions", masterOptions));
        for(Options options : optionsList) {
            element.addContent(XMLSerializer.classToXML("ButtonMultipleOptions", options));
        }
        if(addButtonText != null) {
            element.addContent(new Element("AddButtonText").setText(addButtonText));
        }
        if(optionsToLabel != null) {
            element.addContent(new Element("OptionsToLabel").setText(optionsToLabel.getClass().getCanonicalName()));
        }
        return element;
    }

    public static interface OptionsToLabel<T extends Options>{
        public String getLabel(T options);
    }
}
