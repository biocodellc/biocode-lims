package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;

/**
 * @version $Id: MooreaLabBenchPlugin.java 22212 2008-09-17 02:57:52Z richard $
 */
public class MooreaLabBenchPlugin extends GeneiousPlugin {
    public String getName() {
        return "Moorea Lab Bench Plugin";
    }

    public String getDescription() {
        return "";
    }

    public String getHelp() {
        return "";
    }

    public String getAuthors() {
        return "Biomatters Ltd.";
    }

    public String getVersion() {
        return "1.0";
    }

    public String getMinimumApiVersion() {
        return "4.0";
    }

    public int getMaximumApiVersion() {
        return 4;
    }

    @Override
    public GeneiousService[] getServices() {
        return new GeneiousService[] {MooreaLabBenchService.getInstance()};
    }

    @Override
    public DocumentViewerFactory[] getDocumentViewerFactories() {
        return new DocumentViewerFactory[] {
                new TissueImagesViewerFactory(),
                new TissueSampleViewerFactory(true),
                new TissueSampleViewerFactory(false)
        };
    }

    @Override
    public DocumentOperation[] getDocumentOperations() {
        return new DocumentOperation[] {
                new NewPlateDocumentOperation()
        };
    }

    @Override
    public DocumentType[] getDocumentTypes() {
        return new DocumentType[] {
                new DocumentType("Tissue Sample", TissueDocument.class, IconUtilities.getIcons("specimenDocument.png"))
        };
    }
}
