package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 2/07/2009 11:21:20 AM
 */
public class PlateDocumentViewerFactory extends MultiPartDocumentViewerFactory{
    public String getName() {
        return "Plate";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(PlateDocument.class,1,1)};
    }

    @Override
    public DocumentViewer createViewer(AnnotatedPluginDocument[] annotatedDocuments) {
        PlateDocument pdoc = (PlateDocument)annotatedDocuments[0].getDocumentOrCrash();

        return new PlateDocumentViewer(pdoc, annotatedDocuments[0], annotatedDocuments[0].isInLocalRepository());
    }
}
