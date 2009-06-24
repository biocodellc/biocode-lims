package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.components.OptionsPanel;

import javax.swing.*;
import java.awt.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 7:09:36 PM
 */
public class MultiPartDocumentViewerFactory extends DocumentViewerFactory{
    private Type type;

    public enum Type {
        Workflow,
        Plate
    }

    public MultiPartDocumentViewerFactory(Type type) {
        this.type = type;
    }


    public String getName() {
        return type == Type.Workflow ? "Workflow" : "Plate";
    }

    public String getDescription() {
        return "Provides a view of "+getName()+"s.";
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        switch(type) {
            case Workflow:
                return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class,1,1)};
            case Plate:
                return new DocumentSelectionSignature[] {new DocumentSelectionSignature(PlateDocument.class,1,1)};
        }
        return new DocumentSelectionSignature[0];
    }

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        return new DocumentViewer(){
            public JComponent getComponent() {
                MuitiPartDocument doc = (MuitiPartDocument)annotatedDocuments[0].getDocumentOrCrash();
                OptionsPanel panel = new OptionsPanel();
                panel.setOpaque(true);
                panel.setBackground(Color.white);
                for(int i=0; i < doc.getNumberOfParts(); i++) {
                    MuitiPartDocument.Part part = doc.getPart(i);
                    JPanel holderPanel = new JPanel(new BorderLayout());
                    holderPanel.setOpaque(false);
                    holderPanel.setBorder(new OptionsPanel.RoundedLineBorder(part.getName(), false));
                    holderPanel.add(part, BorderLayout.CENTER);
                    panel.addSpanningComponent(holderPanel);
                }
                return panel;
            }

            
        };
    }
}
