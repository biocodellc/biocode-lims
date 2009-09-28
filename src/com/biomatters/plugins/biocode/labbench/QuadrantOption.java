package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 25/09/2009 11:25:26 AM
 */
public class QuadrantOption extends Options.Option<Integer, JPanel> {

    public QuadrantOption(String name, String label, Integer defaultValue) {
        super(name, label, defaultValue);
    }

    public QuadrantOption(Element e) throws XMLSerializationException{
        super(e);
    }

    public Integer getValueFromString(String value) {
        return Integer.parseInt(value);
    }

    protected void setValueOnComponent(JPanel component, Integer value) {
        ((JRadioButton)component.getComponent(value-1)).setSelected(true);
    }

    protected JPanel createComponent() {
        final JPanel panel = new JPanel(new GridLayout(2,2,5,5));

        JRadioButton quadrant1 = new JRadioButton("Quadrant 1", true);
        JRadioButton quadrant2 = new JRadioButton("Quadrant 2");
        JRadioButton quadrant3 = new JRadioButton("Quadrant 3");
        JRadioButton quadrant4 = new JRadioButton("Quadrant 4");

        ChangeListener cl = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                for (int i = 0; i < panel.getComponents().length; i++) {
                    JRadioButton radio = (JRadioButton)panel.getComponents()[i];
                    if(radio.isSelected()) {
                        setValue(i+1);
                    }
                }
            }
        };

        quadrant1.addChangeListener(cl);
        quadrant2.addChangeListener(cl);
        quadrant3.addChangeListener(cl);
        quadrant4.addChangeListener(cl);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(quadrant1);
        buttonGroup.add(quadrant2);
        buttonGroup.add(quadrant3);
        buttonGroup.add(quadrant4);

        panel.add(quadrant1);
        panel.add(quadrant2);
        panel.add(quadrant3);
        panel.add(quadrant4);

        return panel;
    }
}
