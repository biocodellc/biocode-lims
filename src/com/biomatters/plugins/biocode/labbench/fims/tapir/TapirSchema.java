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
                "http://rs.tdwg.org/dwc/dwcore/Species",
                "http://rs.tdwg.org/dwc/dwcore/SpecificEpithet",
                "http://rs.tdwg.org/dwc/dwcore/Subspecies"
            };
        }

        @Override
        public String[] getFields() {
            return new String[0];
        }
    };

    public static TapirSchema ABCD = new TapirSchema() {
        @Override
        public String getSpecimenIdField() {
            return "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/UnitID";
        }

        @Override
        public String[] getTaxonomyCodes() {
            return new String[] {
                    // Can be either Kingdom, Phylum, CLass, Order or Family depending on the value of HigherTaxonRank
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/Result/TaxonIdentified/HigherTaxa/HigherTaxon/HigherTaxonName",
                    // Genus
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/TaxonIdentified/ScientificName/NameAtomised/Botanical/Genus",
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/TaxonIdentified/ScientificName/NameAtomised/Bacterial/GenusOrMonomial",
                    // Species
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/TaxonIdentified/ScientificName/NameAtomised/Bacterial/SpeciesEpithet",
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/TaxonIdentified/ScientificName/NameAtomised/Botanical/FirstE",
                    // Subspecies
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/TaxonIdentified/ScientificName/NameAtomised/Bacterial/SubspeciesEpithet",
                    "http://www.tdwg.org/schemas/abcd/2.06/DataSets/DataSet/Units/Unit/Identifications/Identification/TaxonIdentified/ScientificName/NameAtomised/Botanical/Sec"
            };
        }

        @Override
        public String[] getFields() {
            return new String[0];
        }
    };
}
