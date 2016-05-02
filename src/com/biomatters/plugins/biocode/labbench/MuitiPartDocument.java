package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.sql.SQLException;

/**
 * @author Steven Stones-Havas
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

        public void addModifiedStateChangedListener(SimpleListener sl){}

        public void removeModifiedStateChangedListener(SimpleListener sl){}

        public abstract void saveChangesToDatabase(ProgressListener progress, LIMSConnection connection) throws DatabaseServiceException;
        
    }
}
