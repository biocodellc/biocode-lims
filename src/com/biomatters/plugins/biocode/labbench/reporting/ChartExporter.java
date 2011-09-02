package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.PluginDocument;

import java.io.File;
import java.io.IOException;

import jebl.util.ProgressListener;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 2/09/2011 10:12:20 AM
 */


public interface ChartExporter {

    public String getFileTypeDescription();

    public String getDefaultExtension();

    public void export(File file, ProgressListener progressListener) throws IOException;

}
