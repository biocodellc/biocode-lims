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

    public boolean hasChanges() {
        for(int i=0; i < getNumberOfParts(); i++) {
            if(getPart(i).hasChanges()) {
                return true;
            }
        }
        return false;
    }


    public static abstract class Part {

        public abstract String getName();

        public abstract ExtendedPrintable getExtendedPrintable();

        public abstract JPanel getPanel();

        public abstract boolean hasChanges();
        
    }
}
