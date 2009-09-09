package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 12/05/2009
 * Time: 5:55:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class PasswordOption extends Options.Option<String, JPanel>{

    private boolean updatingComponent = false;
    boolean createdComponent = false;
    private String password;
    private boolean save = false;

    public PasswordOption(Element e) throws XMLSerializationException {
        super(e);
        setRestorePreferenceApplies(false);
    }

    public PasswordOption(String name, String label, String defaultValue){
        super(name, label, defaultValue);
    }


    protected void setValueOnComponent(JPanel panel, String value) {
    }

    public String getPassword() {
        return password != null ? password : "";
    }

    private void setPassword(String password) {
        this.password = password;
        if(save) {
            setValue(password);
        }
    }

    protected JPanel createComponent() {
        JPanel panel = new JPanel();
        this.password = getValue();
        panel.setOpaque(false);
        panel.setBorder(null);
        panel.setLayout(new BorderLayout(0,0));
        final JPasswordField passwordField = new JPasswordField(getValue());
        passwordField.setColumns(30);


        passwordField.getDocument().addDocumentListener(new DocumentListener() {
                private void update() {
                    if (updatingComponent) return;
                    updatingComponent = true;
                    setPassword(new String(passwordField.getPassword()));
                    updatingComponent = false;
                }

                public void insertUpdate(DocumentEvent e) {
                    update();
                }

                public void removeUpdate(DocumentEvent e) {
                    update();
                }

                public void changedUpdate(DocumentEvent e) {
                    update();
                }
            });
        createdComponent = true;
        panel.add(passwordField, BorderLayout.CENTER);
        save = getValue().length() > 0;
        final JCheckBox saveBox = new JCheckBox("Save", save);
        panel.add(saveBox, BorderLayout.EAST);
        saveBox.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e) {
                save = saveBox.isSelected();
                if(save) {
                    setValue(getPassword());
                }
                else {
                    setValue("");
                }
            }
        });
        return panel;
    }


    public String getValueFromString(String value) {
        return value;
    }
}
