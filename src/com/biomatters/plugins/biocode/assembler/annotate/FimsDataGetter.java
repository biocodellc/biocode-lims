package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;

/**
 * @author Steve
 * @version $Id: 1/09/2010 4:03:53 PM steve $
 */
public interface FimsDataGetter {
    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException;
}
