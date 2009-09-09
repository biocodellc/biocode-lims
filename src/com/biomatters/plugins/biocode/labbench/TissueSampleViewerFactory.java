package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;

import javax.swing.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 4/06/2009 11:21:32 AM
 */
public class TissueSampleViewerFactory extends DocumentViewerFactory{
    private boolean taxonomy;

    public TissueSampleViewerFactory(boolean taxonomy) {
        this.taxonomy = taxonomy;
    }


    public String getName() {
        return taxonomy ? "Taxonomy" : "Tissue Sample Record";
    }

    public String getDescription() {
        return "";
    }

    public String getHelp() {
        return null;
    }

    @Override
    public ViewPrecedence getPrecedence() {
        return taxonomy ? ViewPrecedence.HIGH : ViewPrecedence.HIGHEST;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(TissueDocument.class,1,1)
        };
    }

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        return new DocumentViewer(){
            public JComponent getComponent() {
                TissueDocument doc = (TissueDocument)annotatedDocuments[0].getDocumentOrCrash();
                String html;
                if(taxonomy) {
                    html = doc.getTaxonomyHTML();
                }
                else {
                    html = doc.getTissueHTML();
                }
                if(html == null) {
                    return null;
                }
                StringBuilder htmlBuilder = new StringBuilder("<html>");
                htmlBuilder.append(GuiUtilities.getHtmlHead());
                htmlBuilder.append("<body>");
                htmlBuilder.append(html);
                htmlBuilder.append("</body></html>");
                JEditorPane editorPane = new JEditorPane("text/html", htmlBuilder.toString());
                editorPane.setEditable(false);
                JScrollPane scroller = new JScrollPane(editorPane);
                return scroller;
            }
        };
    }
}
