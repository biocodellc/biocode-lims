package com.biomatters.plugins.biocode.labbench.fims.tapir;

/**
 * Represents a TAPIR schema.  Provides information about the mapping from Geneious concepts to TAPIR ones.
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 27/05/13 3:42 PM
 */
public abstract class TapirSchema {

    public abstract String getSpecimenIdField();
    public abstract String[] getTaxonomyCodes();
    public abstract String[] getFields();

    public static TapirSchema DarwinCore = new TapirSchema() {

        @Override
        public String getSpecimenIdField() {
            return "http://rs.tdwg.org/dwc/dwcore/CatalogNumber";
        }

        @Override
        public String[] getTaxonomyCodes() {
            return new String[] {
                "http://rs.tdwg.org/dwc/dwcore/Kingdom",
                "http://rs.tdwg.org/dwc/dwcore/Phylum",
                "http://rs.tdwg.org/dwc/dwcore/Class",
                "http://rs.tdwg.org/dwc/dwcore/Order",
                "http://rs.tdwg.org/dwc/dwcore/Family",
                "http://rs.tdwg.org/dwc/dwcore/Genus",
                "http://rs.tdwg.org/dwc/dwcore/SpecificEpithet"
            };
        }

        @Override
        public String[] getFields() {
            return new String[0];
        }
    };
}
