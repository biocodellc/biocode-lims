package com.biomatters.plugins.biocode.labbench;

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

    @Override
    public String getHelp() {
        return "Workflows list the path that a single extraction has taken through the lab.  All reactions that are part of the workflow are listed in order in the view.  <br><br>Select two or more workflow documents at once to see a summary view of all reactions performed.";
    }
}
