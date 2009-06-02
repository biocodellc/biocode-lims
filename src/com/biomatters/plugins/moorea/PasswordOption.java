package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 12/05/2009
 * Time: 5:55:26 AM
 * To change this template use File | Settings | File Templates.
 */
public class PasswordOption extends Options.Option<String, JPasswordField>{

    private boolean updatingComponent = false;
    boolean createdComponent = false;

    public PasswordOption(Element e) throws XMLSerializationException {
        super(e);
    }

    public PasswordOption(String name, String label, String defaultValue){
        super(name, label, defaultValue);
    }


    protected void setValueOnComponent(JPasswordField password, String value) {
        if (updatingComponent) return;
        
        updatingComponent = true;
        password.setText(value);
        updatingComponent = false;
    }

    protected JPasswordField createComponent() {
        final JPasswordField passwordField = new JPasswordField();
        passwordField.setColumns(30);


        passwordField.getDocument().addDocumentListener(new DocumentListener() {
                private void update() {
                    if (updatingComponent) return;
                    updatingComponent = true;
                    setValue(new String(passwordField.getPassword()));
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
        return passwordField;
    }


    public String getValueFromString(String value) {
        return value;
    }
}
