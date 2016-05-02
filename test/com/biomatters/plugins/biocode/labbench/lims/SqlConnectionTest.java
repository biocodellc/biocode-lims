package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 1/05/14 10:42 AM
 */
public class SqlConnectionTest extends Assert {
    @Test
    public void testDoesNotGetStuck() throws SQLException, IOException, ConnectionException, InterruptedException {

        String databaseName = "test";
        File temp = FileUtilities.createTempDir(true);
        BiocodeService biocodeeService = BiocodeService.getInstance();
        biocodeeService.setDataDirectory(temp);
        LocalLIMSConnectionOptions.createDatabase(databaseName);

        LocalLIMSConnection sql = new LocalLIMSConnection();
        PasswordOptions options = LIMSConnection.createConnectionOptions();
        options.setValue(LIMSConnection.AvailableLimsTypes.local.name() + "." + LocalLIMSConnectionOptions.DATABASE, databaseName);
        sql._connect(options);

        List<TestConnectionThread> threads = new ArrayList<TestConnectionThread>();
        for (int i = 0; i < 50; i++) {
            TestConnectionThread thread = new TestConnectionThread(sql);
            thread.start();
            threads.add(thread);
        }
        for (TestConnectionThread thread : threads) {
            thread.join();
            thread.throwExceptionIfExists();
        }
    }

    private class TestConnectionThread extends Thread {
        private SqlLimsConnection sql;
        private SQLException exception;

        private TestConnectionThread(SqlLimsConnection sql) {
            this.sql = sql;
        }

        public void run() {
            try {
                SqlLimsConnection.ConnectionWrapper conn = null;

                for(int i=1; i<5; i++) {
                    for(int j=0; j<i; j++) {
                        conn = sql.getConnection();
                    }
                    for(int j=0; j<i; j++) {
                        sql.returnConnection(conn);
                    }
                }
            } catch (SQLException e) {
                exception = e;
                e.printStackTrace();
            }
        }

        public void throwExceptionIfExists() throws SQLException {
            if(exception != null) {
                throw exception;
            }
        }
    }
}
