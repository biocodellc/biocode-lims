package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import org.jdom.JDOMException;
import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import java.io.FileInputStream;

/**
 * Created by Gen Li on 6/05/14.
 */
public class TestDuplicateURIs extends Assert {
    @Test
    public void noDuplicateURIs() throws IOException, DatabaseServiceException, JDOMException {
        FileInputStream stream = new FileInputStream("/Users/genli/geneious src/biocode-lims/testdata/com/biomatters/plugins/biocode/labbench/fims/bwp_config_with_duplicate_uris.xml");
        List<Project.Field> fields = Project.getProjectFieldsFromXmlElement("DuplicateURIs", stream);
        Set<String> urisSeen = new HashSet<String>();
        for (Project.Field field : fields) {
            assertFalse(urisSeen.contains(field.getURI()));
            urisSeen.add(field.getURI());
        }
        stream.close();
    }
}

