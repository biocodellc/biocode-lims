package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;

import javax.ws.rs.*;
import java.util.Arrays;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 7/04/14 1:16 AM
 */
@Path("extractions")
public class Extractions {

    @GET
    @Produces("application/xml")
    public XMLSerializableList<ExtractionReaction> getForBarcodes(@QueryParam("barcodes")String barcodes) {
        if(barcodes == null || barcodes.trim().isEmpty()) {
            throw new BadRequestException("Must specify barcodes");
        }

        try {
            return new XMLSerializableList<ExtractionReaction>(ExtractionReaction.class,
                    BiocodeService.getInstance().getActiveLIMSConnection().getExtractionsFromBarcodes(Arrays.asList(barcodes.split(","))));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
