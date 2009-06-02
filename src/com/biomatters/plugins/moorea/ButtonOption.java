package com.biomatters.plugins.moorea;

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
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 14/05/2009
 * Time: 4:55:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class ButtonOption extends Options.Option<String, JPanel> {
        List<ActionListener> actionListeners = new ArrayList<ActionListener>();

        public ButtonOption(String name, String label, String defaultValue){
            super(name, label, defaultValue);
        }

        public ButtonOption(Element e) throws XMLSerializationException {
            super(e);
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

        protected void setValueOnComponent(JPanel component, String value) {
        }

        protected JPanel createComponent() {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
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
    }
