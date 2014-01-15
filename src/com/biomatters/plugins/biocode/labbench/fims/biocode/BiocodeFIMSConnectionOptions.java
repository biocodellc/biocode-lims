package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by matthew on 1/02/14.
 */
public class BiocodeFIMSConnectionOptions extends PasswordOptions {

    ComboBoxOption<OptionValue> expeditionOption;

    public BiocodeFIMSConnectionOptions() {
        super(BiocodePlugin.class);

//        addLabel("<html><i>The Biocode-FIMS is still under active development.<br>" +
//                "There is currently a limitation that prevents Geneious from retrieving fields on the fly.  Due to this the whole FIMS is downloaded and cached when" +
//                "the connection is made.</i></html>");

        // These are hard coded because there is currently no way to query for available expeditions
        final List<OptionValue> expeditionOptions = new ArrayList<OptionValue>();
        expeditionOptions.add(new OptionValue("1", "IndoPacific Database"));
        expeditionOptions.add(new OptionValue("2", "Smithsonian LAB"));
        expeditionOptions.add(new OptionValue("3", "Hawaii Dimensions"));
        expeditionOptions.add(new OptionValue("5", "Barcode of Wildlife Training"));
        expeditionOption = addComboBoxOption("expedition", "Expedition:", expeditionOptions, expeditionOptions.get(0));
    }


    public List<OptionValue> getFieldsAsOptionValues() throws DatabaseServiceException {
        String expedition = expeditionOption.getValue().getName();
        List<Graph> graphs = BiocodeFIMSUtils.getGraphsForExpedition(expedition);
        if(graphs.isEmpty()) {
            return Collections.singletonList(new OptionValue("none", "No Fields"));
        }

        List<OptionValue> fields = new ArrayList<OptionValue>();
        BiocodeFimsData data = BiocodeFIMSUtils.getData(expedition, graphs.get(0), null);
        for (String fieldName : data.header) {
            fields.add(new OptionValue(TableFimsConnection.CODE_PREFIX + fieldName, fieldName));
        }
        return fields;
    }

    public String getExpedition() {
        return expeditionOption.getValue().getName();
    }
}
