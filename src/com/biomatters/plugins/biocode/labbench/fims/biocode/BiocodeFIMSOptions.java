package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;

import javax.ws.rs.client.WebTarget;
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
        return false;
    }

    @Override
    public boolean linkPhotos() {
        return super.linkPhotos();
    }

    public WebTarget getWebTarget() {
        return connectionOptions.getWebTarget();
    }
}
