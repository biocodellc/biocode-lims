package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsTest;
import org.jdom.JDOMException;
import org.junit.Test;
import org.junit.Assert;
import java.io.IOException;
import java.util.List;
import java.io.FileInputStream;

/**
 * @author Gen Li
 * Created on 8/05/14.
 */
public class DuplicateURIsTest extends Assert {
    String configurationFileNormalPath = ExcelFimsTest.class.getResource("bwp_config.xml").getFile().replace("%20", " ");
    String configurationFileWithDuplicateURIPath = ExcelFimsTest.class.getResource("bwp_config_with_duplicate_uris.xml").getFile().replace("%20", " ");

    /**
     * bwp_config.xml and bwp_config_with_duplicate_uris.xml are identical except latter contains extra copy of last
     * attribute.
     * @throws IOException
     * @throws DatabaseServiceException
     * @throws JDOMException
     */
    @Test
    public void duplicateURIs() throws IOException, DatabaseServiceException, JDOMException {
        FileInputStream configurationFileNormalStream = null;
        FileInputStream configurationFileWithDuplicateURIStream = null;
        try {
            configurationFileNormalStream = new FileInputStream(configurationFileNormalPath);
            configurationFileWithDuplicateURIStream = new FileInputStream(configurationFileWithDuplicateURIPath);
            List<Project.Field> fieldsFromConfigurationFileNormal = Project.getProjectFieldsFromXmlElement(null, configurationFileNormalStream);
            List<Project.Field> fieldsFromConfigurationFileDuplicateURIStream = Project.getProjectFieldsFromXmlElement(null, configurationFileWithDuplicateURIStream);
            assertEquals(fieldsFromConfigurationFileNormal.size(), fieldsFromConfigurationFileDuplicateURIStream.size());
            for (int i = 0; i != fieldsFromConfigurationFileNormal.size(); i++) {
                assertEquals(fieldsFromConfigurationFileNormal.get(i).getURI(), fieldsFromConfigurationFileDuplicateURIStream.get(i).getURI());
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (configurationFileNormalStream != null) {
                configurationFileNormalStream.close();
            }
            if (configurationFileWithDuplicateURIStream != null) {
                configurationFileWithDuplicateURIStream.close();
            }
        }
    }
}