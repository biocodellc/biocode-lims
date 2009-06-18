package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 5:09:03 PM
 */
public abstract class MuitiPartDocument implements PluginDocument {


    public abstract int getNumberOfParts();

    public abstract Part getPart(int index);


    public static abstract class Part extends JPanel {

        public abstract String getName();

        public abstract ExtendedPrintable getExtendedPrintable();
        
    }
}
