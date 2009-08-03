package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 2/07/2009 11:20:31 AM
 */
public class WorkflowDocumentViewerFactory extends MultiPartDocumentViewerFactory{
    public String getName() {
        return "Workflow";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class,1,1)};
    }
}
