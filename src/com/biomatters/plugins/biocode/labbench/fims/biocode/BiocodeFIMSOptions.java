package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;

import java.io.IOException;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 8:13 PM
 */
public class BiocodeFIMSOptions extends TableFimsConnectionOptions {
    private BiocodeFIMSConnectionOptions connectionOptions;
    @Override
    protected PasswordOptions getConnectionOptions() {
        if(connectionOptions == null) {
            connectionOptions = new BiocodeFIMSConnectionOptions();
        }
        return connectionOptions;
    }

    @Override
    protected List<OptionValue> _getTableColumns() throws IOException {
        try {
            return connectionOptions.getFieldsAsOptionValues();
        } catch (DatabaseServiceException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    protected boolean updateAutomatically() {
        return true;
    }

    @Override
    public boolean linkPhotos() {
        return super.linkPhotos();
    }

    public Project getProject() {
        return connectionOptions.getProject();
    }

    public String getHost() {
        return connectionOptions.getHost();
    }
}
