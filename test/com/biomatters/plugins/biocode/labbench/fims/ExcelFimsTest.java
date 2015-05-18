package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.TestUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LimsTestCase;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created Gen Li on 28/04/14.
 */
public class ExcelFimsTest extends Assert {

    @Test
    public void successOnNormalConnectionAttempt() throws ConnectionException {
        TestGeneious.initialize();
        ExcelFimsConnection connection = new ExcelFimsConnection();
        TableFimsConnectionOptions options = connection._getConnectionOptions();
        LimsTestCase.waitForTissueColumnInitialization(TestUtilities.getResourcePath(ExcelFimsTest.class, "demo video FIMS.xls"), (ExcelFimsConnectionOptions)options);
        options.setValue(ExcelFimsConnectionOptions.TISSUE_ID, "tissue_id");
        connection._connect(options);
    }

    @Test(expected = ConnectionException.class)
    public void failsOnDuplicateKeys() throws ConnectionException {
        TestGeneious.initialize();
        ExcelFimsConnection connection = new ExcelFimsConnection();
        TableFimsConnectionOptions options = connection._getConnectionOptions();
        LimsTestCase.waitForTissueColumnInitialization(TestUtilities.getResourcePath(ExcelFimsTest.class, "demo video FIMS with duplicate tissue ids.xls"), (ExcelFimsConnectionOptions)options);
        options.setValue(ExcelFimsConnectionOptions.TISSUE_ID, "tissue_id");
        connection._connect(options);
    }
}
