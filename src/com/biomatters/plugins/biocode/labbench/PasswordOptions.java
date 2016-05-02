package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

/**
 * @author Steve
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
     * Retrieves updated list of option values and then populates possible values for Options.  Option population is scheduled
     * on the dispatch thread if {@link com.biomatters.geneious.publicapi.plugin.Geneious#isHeadless()} returns false,
     * otherwise it is done immediately after retrieving the new values.
     *
     * <strong>Note</strong>: The retrieval may take some time, so it is best to call this method from a non UI thread.
     *
     * @throws ConnectionException if a problem occurs retrieving the column list from the FIMS
     */
    public void update() throws ConnectionException {}

    /**
     * Retrieves updated list of option values and then populates possible values for Options. This is for the options which are depend by others.
     *
     * @throws ConnectionException if a problem occurs
     */
    public void preUpdate() throws ConnectionException {}

    /**
     * Initialize the data which are time consuming, this method is supposed to be invoked outside swing thread.
     *
     * @throws ConnectionException if a problem occurs
     */
    public void prepare() throws ConnectionException {}
}
