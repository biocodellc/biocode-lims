package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;

/**
 * @author Steve
 */
public interface FimsDataGetter {
    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException;
}
