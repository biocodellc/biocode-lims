package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import java.sql.Driver;

/**
 * An SQL based {@link LIMSConnection}
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 1/04/14 4:45 PM
 */
public abstract class SqlLimsConnection extends LIMSConnection {

    @Override
    public abstract boolean requiresMySql();

    @Override
    public PasswordOptions getConnectionOptions() {
        return null;
    }

    @Override
    public abstract boolean isLocal();

    @Override
    public abstract String getUsername();

    @Override
    public abstract String getSchema();

    @Override
    protected abstract boolean canUpgradeDatabase();

    @Override
    public abstract Driver getDriver() throws ConnectionException;

    @Override
    abstract void connectToDb(Options connectionOptions) throws ConnectionException;
}
