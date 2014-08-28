package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;

import java.util.Set;

/**
* @author Matthew Cheung
*         Created on 27/08/14 4:38 PM
*/
class MissingFieldValueException extends Exception {
    private DocumentField field;
    private Set<String> namesOfDocsWithMissing;

    MissingFieldValueException(DocumentField field, Set<String> namesOfDocsWithMissing) {
        super(BiocodeUtilities.getCountString("document", namesOfDocsWithMissing.size()) +
                                    " did not have an annotated " + field.getName() + ".  \n\n" +
                                    "Make sure to run Biocode -> Annotate from FIMS/LIMS Data first.");
        this.field = field;
        this.namesOfDocsWithMissing = namesOfDocsWithMissing;
    }

    /**
     * Displays a Dialog to the user explaining the problem and listing the documents that are missing a value for the field.
     */
    void showDialog() {
        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_ONLY, field.getName() + "s Missing");
        dialogOptions.setMoreOptionsButtonText("Show Document List", "Hide Document List");
        Dialogs.showMoreOptionsDialog(dialogOptions,
                BiocodeUtilities.getCountString("document", namesOfDocsWithMissing.size()) +
                        " did not have an annotated " + field.getName() + ".  \n\n" +
                        "Make sure to run Biocode -> Annotate from FIMS/LIMS Data first.", StringUtilities.join("\n", namesOfDocsWithMissing));
    }
}
