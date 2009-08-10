package com.biomatters.plugins.moorea.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.Options;

/**
 * @author Richard
 * @version $Id$
 */
public class AddAssemblyResultsToLimsOptions extends Options {

    public AddAssemblyResultsToLimsOptions(AnnotatedPluginDocument[] documents) {
        //todo offer to add chromatograms to lims as well (they will be the reference documents for assemblies)?
        //todo update taxonomy?
    }
}
