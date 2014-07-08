package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

/**
 * @author Steve
 * @version $Id$
 */
public class PasswordOptions extends Options{

    public PasswordOptions() {
    }

    public PasswordOptions(Class cl) {
        super(cl);
    }

    public PasswordOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
    }

    public PasswordOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public Options getEnterPasswordOptions() {
        return null;
    }

    public void setPasswordsFromOptions(Options enterPasswordOptions) {}

    /**
     * Retrieves column list from FIMS and then populates possible values for Options on the dispatch thread.
     *
     * @throws ConnectionException if a problem occurs retrieving the column list from the FIMS
     */
    public void update() throws ConnectionException {}

}
