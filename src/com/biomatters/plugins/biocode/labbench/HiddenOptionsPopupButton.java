package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author steve
 * @version $Id: 14/05/2009 5:56:05 PM steve $
 */
public class HiddenOptionsPopupButton extends Options.Option<String, JPanel>{
    private String childOptionsName;

    public HiddenOptionsPopupButton(String name, String label, String childOptionsName, String defaultValue){
        super(name, label, defaultValue);
        this.childOptionsName = childOptionsName;
    }

    public HiddenOptionsPopupButton(Element e) throws XMLSerializationException {
        super(e);
        childOptionsName = e.getAttributeValue("childOptionsName");
    }

    public String getValueFromString(String value) {
        return value;
    }

    protected void setValueOnComponent(JPanel component, String value) {
    }

    protected JPanel createComponent() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setOpaque(false);
        JButton button = new JButton(getDefaultValue());
        button.setOpaque(false);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Options options = getParentOptions().getChildOptions().get(childOptionsName);
                Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_ONLY, "States", panel), options.getAdvancedPanel());
            }
        });
        panel.add(button);
        return panel;
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        element.setAttribute("childOptionsName", childOptionsName);
        return element;
    }
}
