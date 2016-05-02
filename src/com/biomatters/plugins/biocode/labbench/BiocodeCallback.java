package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.util.Map;
import java.util.List;
import java.awt.*;

import org.virion.jam.util.SimpleListener;
import jebl.util.ProgressListener;

/**
 * @author Steve
 *          <p/>
 *          Created on 23/01/2012 4:22:07 PM
 */


public class BiocodeCallback extends RetrieveCallback {
    private RetrieveCallback internalCallback;
    private ProgressListener internalListener;
    private boolean canceled = false;

    public BiocodeCallback(RetrieveCallback internalCallback) {
        this.internalCallback = internalCallback;
        this.internalListener = internalCallback;
    }

    public BiocodeCallback(ProgressListener progress) {
        internalListener = progress;
    }

    public void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.add(document, searchResultProperties);
    }

    public void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.add(document, searchResultProperties);
    }

    public void setPropertyFields(List<DocumentField> searchResultProperties, DocumentField defaultSortingField) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.setPropertyFields(searchResultProperties, defaultSortingField);
    }

    public void setFolderViewDocument(AnnotatedPluginDocument folderViewDocument) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.setFolderViewDocument(folderViewDocument);
    }

    public boolean setSearchResumptionIdentifier(String uniqueIdentifier) {
        if(internalCallback == null) {
            return false;
        }
        return internalCallback.setSearchResumptionIdentifier(uniqueIdentifier);
    }

    public void _setStatus(String message, long totalNumberOfDocuments, boolean approximated, long predictedTimeToRetrive, long numberOfBytesDownloaded, long totalNumberOfBytesToDownload) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.setStatus(message, totalNumberOfDocuments, approximated, predictedTimeToRetrive, numberOfBytesDownloaded, totalNumberOfBytesToDownload);
    }

    public void issueWarning(String message, String title) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.issueWarning(message, title);
    }

    public void setFinalStatus(String message, boolean appendToDefaultStatus) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.setFinalStatus(message, appendToDefaultStatus);
    }

    public void addSearchCancelledListener(SimpleListener cancelledListener) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.addSearchCancelledListener(cancelledListener);
    }

    public void removeSearchCancelledListener(SimpleListener cancelledListener) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.removeSearchCancelledListener(cancelledListener);
    }

    public void remove(AnnotatedPluginDocument document) {
        if(internalCallback == null) {
            return;
        }
        internalCallback.remove(document);
    }

    public boolean acceptsChangesAfterRetrieveCompletes(SimpleListener noLongerWantsChangesListener) {
        if(internalCallback == null) {
            return false;
        }
        return internalCallback.acceptsChangesAfterRetrieveCompletes(noLongerWantsChangesListener);
    }

    @Override
    public boolean _isCanceled() {
        return internalListener.isCanceled() || canceled;
    }

    @Override
    public void addFeedbackAction(String label, SimpleListener listener) {
        internalListener.addFeedbackAction(label, listener);
    }

    @Override
    public void addFeedbackAction(String label, String description, SimpleListener listener) {
        internalListener.addFeedbackAction(label, description, listener);
    }

    @Override
    public void removeFeedbackAction(String label) {
        internalListener.removeFeedbackAction(label);
    }

    @Override
    public void setTitle(String title) {
        internalListener.setTitle(title);
    }

    public void _setProgress(double fractionCompleted) {
        internalListener.setProgress(fractionCompleted);
    }

    public void _setProgress(int currentStep, int numberOfSteps) {
        internalListener.setProgress(currentStep, numberOfSteps);
    }

    public void _setProgress(long currentStep, long numberOfSteps) {
        internalListener.setProgress(currentStep, numberOfSteps);
    }

    public void _setIndeterminateProgress() {
        internalListener.setIndeterminateProgress();
    }

    public void _setMessage(String message) {
        internalListener.setMessage(message);
    }

    public void _setImage(Image image) {
        internalListener.setImage(image);
    }

    public void cancel() {
        canceled = true;
    }
}
