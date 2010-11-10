package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author steve
 * @version $Id: 14/05/2009 4:55:18 PM steve $
 */
public class ButtonOption extends Options.Option<String, JPanel> {
    List<ActionListener> actionListeners = new ArrayList<ActionListener>();
    private boolean displayInMultiOptions = true;

    public ButtonOption(String name, String label, String defaultValue){
        super(name, label, defaultValue);
    }

    public ButtonOption(String name, String label, String defaultValue, boolean displayInMultiOptions){
        super(name, label, defaultValue);
        this.displayInMultiOptions = displayInMultiOptions;
    }

    public ButtonOption(Element e) throws XMLSerializationException {
        super(e);
        displayInMultiOptions = "true".equals(e.getChildText("displayInMultiOptions"));
    }

    public String getValueFromString(String value) {
        return value;
    }

    public void addActionListener(ActionListener listener) {
        actionListeners.add(listener);
    }

    public void removeActionListener(ActionListener listener) {
        actionListeners.remove(listener);
    }

    public List<ActionListener> getActionListeners() {
        return new ArrayList<ActionListener>(actionListeners);
    }

    protected void setValueOnComponent(JPanel component, String value) {
    }

    protected JPanel createComponent() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT)){
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                for(Component c : getComponents()) {
                    c.setEnabled(enabled);
                }
            }
        };
        panel.setOpaque(false);
        JButton button = new JButton(getDefaultValue());
        button.setOpaque(false);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                for(ActionListener a : actionListeners){
                    a.actionPerformed(new ActionEvent(getComponent(), 0, "Advanced"));
                }
            }
        });
        panel.add(button);
        return panel;
    }

    @Override
     public Element toXML() {
        Element element = super.toXML();
        if(displayInMultiOptions) {
            element.addContent(new Element("displayInMultiOptions").setText("true"));
        }
        return element;
    }

    public boolean displayInMultiOptions() {
        return displayInMultiOptions;
    }
}
