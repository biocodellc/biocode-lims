package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

import javax.swing.*;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 11/06/2009 7:32:20 PM
 */
public class TextAreaOption extends Options.Option<String, JScrollPane>{
    private String title;


    public TextAreaOption(Element e) throws XMLSerializationException{
        super(e);
        title = e.getChildText("title");
        setSpanningComponent(true);
    }

    public TextAreaOption(String name, String label, String defaultValue) {
        super(name, "", defaultValue);
        title = label;
        setSpanningComponent(true);
    }

    public String getValueFromString(String value) {
        return value;
    }

    protected void setValueOnComponent(JScrollPane component, String value) {
        ((JTextArea)component.getViewport().getView()).setText(value);
    }

    protected JScrollPane createComponent() {
        final JTextArea notes = new JTextArea(5,40);
        JScrollPane notesScroller = new JScrollPane(notes){
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                getViewport().getView().setEnabled(enabled);
            }
        };
        notesScroller.setBorder(BorderFactory.createTitledBorder(title));
        notes.addKeyListener(new KeyListener(){
            public void keyTyped(KeyEvent e) {
                setValue(notes.getText());
            }

            public void keyPressed(KeyEvent e) {
                setValue(notes.getText());
            }

            public void keyReleased(KeyEvent e) {
                setValue(notes.getText());
            }
        });
        return notesScroller;
    }

    @Override
    public Element toXML() {
        Element xml = super.toXML();
        xml.addContent(new Element("title").setText(title));
        return xml;
    }
}
