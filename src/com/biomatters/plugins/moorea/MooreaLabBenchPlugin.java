package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.GeneiousPlugin;
import com.biomatters.geneious.publicapi.plugin.GeneiousService;

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
        return new GeneiousService[] {new MooreaLabBenchService()};
    }
}
