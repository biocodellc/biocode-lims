package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import javax.swing.*;

/**
 * @author steve
 */
public class AdvancedAndNormalPanelsSwappedOptions extends Options {

    AdvancedAndNormalPanelsSwappedOptions() {
        super();
    }

    AdvancedAndNormalPanelsSwappedOptions(Class tclass) {
        super(tclass);
    }

    public AdvancedAndNormalPanelsSwappedOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
    }

    protected AdvancedAndNormalPanelsSwappedOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    @Override
    protected JPanel createPanel() {
        return super.createAdvancedPanel();
    }

    @Override
    protected JPanel createAdvancedPanel() {
        return super.createPanel();
    }
}
