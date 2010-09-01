package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 1/09/2010
 * Time: 4:03:53 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FimsDataGetter {
    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException;
}
