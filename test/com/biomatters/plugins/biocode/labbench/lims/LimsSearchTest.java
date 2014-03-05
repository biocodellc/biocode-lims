package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.ProgressListener;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/03/14 3:36 PM
 */
public class LimsSearchTest extends Assert {

    private static final String DATABASE_NAME = "testLimsForSearchTest";

    @Test
    public void basicSearch() throws IOException, BadDataException, SQLException {
        TestGeneious.initialize();

        File temp = FileUtilities.createTempDir(true);
        BiocodeService biocodeeService = BiocodeService.getInstance();
        biocodeeService.setDataDirectory(temp);
        LocalLIMSConnectionOptions.createDatabase(DATABASE_NAME);

        ConnectionManager.Connection connectionConfig = new ConnectionManager.Connection("forTests");
        PasswordOptions _fimsOptions = connectionConfig.getFimsOptions();
        assertTrue("First FIMS option has changed from Excel FIMS.  Test needs updating",
                _fimsOptions instanceof ExcelFimsConnectionOptions);
        ExcelFimsConnectionOptions fimsOptions = (ExcelFimsConnectionOptions) _fimsOptions;
        fimsOptions.setValue(ExcelFimsConnectionOptions.CONNECTION_OPTIONS_KEY + "." + ExcelFimsConnectionOptions.FILE_LOCATION, getPathToDemoFIMSExcel());
        fimsOptions.setValue(TableFimsConnectionOptions.TISSUE_ID, "tissue_id");
        fimsOptions.setValue(TableFimsConnectionOptions.SPECIMEN_ID, "Specimen No.");
        fimsOptions.setValue(TableFimsConnectionOptions.STORE_PLATES, Boolean.TRUE.toString());
        fimsOptions.setValue(TableFimsConnectionOptions.PLATE_NAME, "plate_name");
        fimsOptions.setValue(TableFimsConnectionOptions.PLATE_WELL, "well_number");
        fimsOptions.autodetectTaxonFields();

        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, LIMSConnection.AvailableLimsTypes.local.name());
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();
        assertTrue("Test needs updating.  Local LIMS not first option", _limsOptions instanceof LocalLIMSConnectionOptions);
        LocalLIMSConnectionOptions limsOptions = (LocalLIMSConnectionOptions) _limsOptions;
        limsOptions.setValue(LocalLIMSConnectionOptions.DATABASE, DATABASE_NAME);

        biocodeeService.connect(connectionConfig, false);

        Plate extractionPlate = new Plate(Plate.Size.w96, Reaction.Type.Extraction);
        extractionPlate.setName("Plate_M037");

        ExtractionReaction reaction = (ExtractionReaction)extractionPlate.getReaction(0, 0);
        reaction.setTissueId("MBIO24950.1");
        reaction.setExtractionId("MBIO24950.1.1");

        biocodeeService.saveExtractions(ProgressListener.EMPTY, extractionPlate);

        List<AnnotatedPluginDocument> searchResults = biocodeeService.retrieve("MBIO24950.1");
        for (AnnotatedPluginDocument searchResult : searchResults) {
            PluginDocument pluginDoc = searchResult.getDocumentOrNull();
            System.out.println(pluginDoc.getName());
        }
    }

    private String getPathToDemoFIMSExcel() {
        final URL resource = getClass().getResource("demo video FIMS.xls");
        if (resource == null) {
            throw new IllegalArgumentException("Couldn't find spreadsheet");
        }
        return resource.getFile().replace("%20", " ");
    }
}
