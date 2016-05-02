package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;

/**
 * @author Steven Stones-Havas
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

    @Override
    public String getHelp() {
        return "<p>The plate viewer shows a representation of your plate (or set of reactions).  The main view contains the plate itself, " +
                "and for PCR and Cycle Sequencing plates, a list of all cocktails used on the palte, the thermocycle used, and GEL images " +
                "if they exist.  GEL images are not downloaded automatically, so click the download button to view them.  If you double click " +
                "on a GEL image, it will pop out into a separate window.</p><br>" +
                "<h2>Actions</h2>" +
                "<p><b>Thermocycle</b><br>" +
                "<i>Not available on extraction plates</i><br><br>" +
                "You can change the thermocycle for the plate by selecting one from the drop down menu.  Click <i>View/Add Thermocycles</i> to create a new one.</p><br>" +
                "<p><b>GEL Images</b><br>" +
                "<i>Not available on extraction plates</i><br><br>" +
                "Click this button to view and add/remove GEL images.</p><br>" +
                "<p><b>Edit wells</b><br><br>" +
                "You can select wells on your plate by clicking, dragging, shift clicking or "+(SystemUtilities.isMac() ? "command" : "ctrl")+" clicking on the plate in the viewer.  Clicking <i>Edit selected wells</i> will then edit all selected wells at once.  There is a column of checkboxes on the left hand side of the edit window.  Only fields which are checked will be changed by the editor.  <br>The editor also allows you to edit the fields that are displayed in each well, as well as the plate color scheme.</p>" +
                "<p><b>Bulk Edit Wells</b><br><br>" +
                "This editor displays key fields of the plate in a column form, making it easy to paste in data from spreadsheet programs.";
    }
}
