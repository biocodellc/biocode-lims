package com.biomatters.plugins.biocode.labbench.fims.geome;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.*;

public class geomeDataProcessor {

    public String sampleJson = "{\n" +
            "  \"page\": 2,\n" +
            "  \"limit\": 2,\n" +
            "  \"content\": {\n" +
            "    \"Tissue\": [\n" +
            "      {\n" +
            "        \"tissuePlate\": \"PACEH_001\",\n" +
            "        \"tissueWell\": \"B02\",\n" +
            "        \"tissueCatalogNumber\": \"http://n2t.net/ark:/21547/Q2INDO62340.1\",\n" +
            "        \"materialSampleID\": \"ACEH_1004\",\n" +
            "        \"tissueID\": \"ACEH_1004.1\",\n" +
            "        \"tissueType\": \"\",\n" +
            "        \"tissueBarcode\": \"\",\n" +
            "        \"tissueInstitution\": \"\",\n" +
            "        \"tissueOtherCatalogNumbers\": \"\",\n" +
            "        \"tissueSamplingYear\": \"\",\n" +
            "        \"tissueSamplingMonth\": \"\",\n" +
            "        \"tissueSamplingDay\": \"\",\n" +
            "        \"tissueRecordedBy\": \"\",\n" +
            "        \"tissueContainer\": \"\",\n" +
            "        \"tissuePreservative\": \"\",\n" +
            "        \"associatedSequences\": \"\",\n" +
            "        \"tissueRemarks\": \"\",\n" +
            "        \"fromTissue\": \"\",\n" +
            "        \"expeditionCode\": \"ACEH_2012\",\n" +
            "        \"projectId\": \"3\",\n" +
            "        \"bcid\": \"ark:/99999/fk4Ckh2ACEH_1004.1\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"tissuePlate\": \"PACEH_001\",\n" +
            "        \"tissueWell\": \"G01\",\n" +
            "        \"tissueCatalogNumber\": \"http://n2t.net/ark:/21547/Q2INDO62451.1\",\n" +
            "        \"materialSampleID\": \"ACEH_1005\",\n" +
            "        \"tissueID\": \"ACEH_1005.1\",\n" +
            "        \"tissueType\": \"\",\n" +
            "        \"tissueBarcode\": \"\",\n" +
            "        \"tissueInstitution\": \"\",\n" +
            "        \"tissueOtherCatalogNumbers\": \"\",\n" +
            "        \"tissueSamplingYear\": \"\",\n" +
            "        \"tissueSamplingMonth\": \"\",\n" +
            "        \"tissueSamplingDay\": \"\",\n" +
            "        \"tissueRecordedBy\": \"\",\n" +
            "        \"tissueContainer\": \"\",\n" +
            "        \"tissuePreservative\": \"\",\n" +
            "        \"associatedSequences\": \"\",\n" +
            "        \"tissueRemarks\": \"\",\n" +
            "        \"fromTissue\": \"\",\n" +
            "        \"expeditionCode\": \"ACEH_2012\",\n" +
            "        \"projectId\": \"3\",\n" +
            "        \"bcid\": \"ark:/99999/fk4Ckh2ACEH_1005.1\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Sample\": [\n" +
            "      {\n" +
            "        \"family\": \"Hippolytidae\",\n" +
            "        \"order\": \"Decapoda\",\n" +
            "        \"phylum\": \"Arthropoda\",\n" +
            "        \"kingdom\": \"Animalia\",\n" +
            "        \"taxonRank\": \"genus\",\n" +
            "        \"genus\": \"Thor\",\n" +
            "        \"superOrder\": \"Eucarida\",\n" +
            "        \"occurrenceRemarks\": \"100% DNA match to BALI_2324 & BALI_2491\",\n" +
            "        \"relaxant\": \"clove oil\",\n" +
            "        \"institutionCode\": \"Smithsonian\",\n" +
            "        \"infraOrder\": \"Caridea\",\n" +
            "        \"subOrder\": \"Pleocyemata\",\n" +
            "        \"eventID\": \"INDO_068\",\n" +
            "        \"catalogNumber\": \"http://n2t.net/ark:/21547/R2INDO62340\",\n" +
            "        \"class\": \"Malacostraca\",\n" +
            "        \"scientificName\": \"Thor\",\n" +
            "        \"subClass\": \"Eumalacostraca\",\n" +
            "        \"superFamily\": \"Alpheoidea\",\n" +
            "        \"subPhylum\": \"Crustacea\",\n" +
            "        \"preservative\": \"95% ethanol\",\n" +
            "        \"materialSampleID\": \"ACEH_1004\",\n" +
            "        \"enteredBy\": \"Amanda Windsor\",\n" +
            "        \"modifiedBy\": \"Amanda Windsor\",\n" +
            "        \"length\": \"\",\n" +
            "        \"lengthUnits\": \"\",\n" +
            "        \"weight\": \"\",\n" +
            "        \"weightUnits\": \"\",\n" +
            "        \"fixative\": \"\",\n" +
            "        \"individualCount\": \"\",\n" +
            "        \"preparationType\": \"\",\n" +
            "        \"typeStatus\": \"\",\n" +
            "        \"modifiedReason\": \"\",\n" +
            "        \"otherCatalogNumbers\": \"\",\n" +
            "        \"occurrenceID\": \"\",\n" +
            "        \"subProject\": \"\",\n" +
            "        \"subSubProject\": \"\",\n" +
            "        \"voucherURI\": \"\",\n" +
            "        \"voucherCatalogNumber\": \"\",\n" +
            "        \"associatedTaxa\": \"\",\n" +
            "        \"organismRemarks\": \"\",\n" +
            "        \"identificationRemarks\": \"\",\n" +
            "        \"colloquialName\": \"\",\n" +
            "        \"dayIdentified\": \"\",\n" +
            "        \"identifiedBy\": \"\",\n" +
            "        \"infraClass\": \"\",\n" +
            "        \"lifeStage\": \"\",\n" +
            "        \"monthIdentified\": \"\",\n" +
            "        \"morphospeciesDescription\": \"\",\n" +
            "        \"morphospeciesMatch\": \"\",\n" +
            "        \"previousIdentifications\": \"\",\n" +
            "        \"sex\": \"\",\n" +
            "        \"specificEpithet\": \"\",\n" +
            "        \"subFamily\": \"\",\n" +
            "        \"subGenus\": \"\",\n" +
            "        \"infraspecificEpithet\": \"\",\n" +
            "        \"subTribe\": \"\",\n" +
            "        \"superClass\": \"\",\n" +
            "        \"taxonCertainty\": \"\",\n" +
            "        \"tribe\": \"\",\n" +
            "        \"yearIdentified\": \"\",\n" +
            "        \"expeditionCode\": \"ACEH_2012\",\n" +
            "        \"projectId\": \"3\",\n" +
            "        \"bcid\": \"ark:/99999/fk4Ckf2ACEH_1004\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"family\": \"Alpheidae\",\n" +
            "        \"order\": \"Decapoda\",\n" +
            "        \"phylum\": \"Arthropoda\",\n" +
            "        \"kingdom\": \"Animalia\",\n" +
            "        \"taxonRank\": \"genus\",\n" +
            "        \"genus\": \"Alpheopsis\",\n" +
            "        \"superOrder\": \"Eucarida\",\n" +
            "        \"occurrenceRemarks\": \"dead\",\n" +
            "        \"relaxant\": \"clove oil\",\n" +
            "        \"institutionCode\": \"Smithsonian\",\n" +
            "        \"infraOrder\": \"Caridea\",\n" +
            "        \"subOrder\": \"Pleocyemata\",\n" +
            "        \"eventID\": \"INDO_068\",\n" +
            "        \"catalogNumber\": \"http://n2t.net/ark:/21547/R2INDO62451\",\n" +
            "        \"class\": \"Malacostraca\",\n" +
            "        \"scientificName\": \"Alpheopsis\",\n" +
            "        \"subClass\": \"Eumalacostraca\",\n" +
            "        \"superFamily\": \"Alpheoidea\",\n" +
            "        \"subPhylum\": \"Crustacea\",\n" +
            "        \"preservative\": \"95% ethanol\",\n" +
            "        \"materialSampleID\": \"ACEH_1005\",\n" +
            "        \"enteredBy\": \"Amanda Windsor\",\n" +
            "        \"length\": \"\",\n" +
            "        \"lengthUnits\": \"\",\n" +
            "        \"weight\": \"\",\n" +
            "        \"weightUnits\": \"\",\n" +
            "        \"fixative\": \"\",\n" +
            "        \"individualCount\": \"\",\n" +
            "        \"preparationType\": \"\",\n" +
            "        \"typeStatus\": \"\",\n" +
            "        \"modifiedBy\": \"\",\n" +
            "        \"modifiedReason\": \"\",\n" +
            "        \"otherCatalogNumbers\": \"\",\n" +
            "        \"occurrenceID\": \"\",\n" +
            "        \"subProject\": \"\",\n" +
            "        \"subSubProject\": \"\",\n" +
            "        \"voucherURI\": \"\",\n" +
            "        \"voucherCatalogNumber\": \"\",\n" +
            "        \"associatedTaxa\": \"\",\n" +
            "        \"organismRemarks\": \"\",\n" +
            "        \"identificationRemarks\": \"\",\n" +
            "        \"colloquialName\": \"\",\n" +
            "        \"dayIdentified\": \"\",\n" +
            "        \"identifiedBy\": \"\",\n" +
            "        \"infraClass\": \"\",\n" +
            "        \"lifeStage\": \"\",\n" +
            "        \"monthIdentified\": \"\",\n" +
            "        \"morphospeciesDescription\": \"\",\n" +
            "        \"morphospeciesMatch\": \"\",\n" +
            "        \"previousIdentifications\": \"\",\n" +
            "        \"sex\": \"\",\n" +
            "        \"specificEpithet\": \"\",\n" +
            "        \"subFamily\": \"\",\n" +
            "        \"subGenus\": \"\",\n" +
            "        \"infraspecificEpithet\": \"\",\n" +
            "        \"subTribe\": \"\",\n" +
            "        \"superClass\": \"\",\n" +
            "        \"taxonCertainty\": \"\",\n" +
            "        \"tribe\": \"\",\n" +
            "        \"yearIdentified\": \"\",\n" +
            "        \"expeditionCode\": \"ACEH_2012\",\n" +
            "        \"projectId\": \"3\",\n" +
            "        \"bcid\": \"ark:/99999/fk4Ckf2ACEH_1005\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"Event\": [\n" +
            "      {\n" +
            "        \"collectorList\": \"Amanda Windsor, Matthieu Leray\",\n" +
            "        \"microHabitat\": \"dead coral head\",\n" +
            "        \"collectionMethod\": \"bag and bucket\",\n" +
            "        \"islandGroup\": \"Palau Weh\",\n" +
            "        \"dayCollected\": \"1\",\n" +
            "        \"decimalLatitude\": \"5.88925\",\n" +
            "        \"monthCollected\": \"7\",\n" +
            "        \"enteredBy\": \"Amanda Windsor\",\n" +
            "        \"maximumDepthInMeters\": \"5\",\n" +
            "        \"locality\": \"Sumur Tiga\",\n" +
            "        \"country\": \"Indonesia\",\n" +
            "        \"island\": \"Sumatra\",\n" +
            "        \"eventID\": \"INDO_068\",\n" +
            "        \"yearCollected\": \"2012\",\n" +
            "        \"continentOcean\": \"Asia\",\n" +
            "        \"timeOfDay\": \"1330\",\n" +
            "        \"minimumDepthInMeters\": \"4\",\n" +
            "        \"taxTeam\": \"MINV\",\n" +
            "        \"stateProvince\": \"Aceh\",\n" +
            "        \"decimalLongitude\": \"95.34360\",\n" +
            "        \"county\": \"\",\n" +
            "        \"depthOfBottomInMeters\": \"\",\n" +
            "        \"habitat\": \"\",\n" +
            "        \"horizontalDatum\": \"\",\n" +
            "        \"landowner\": \"\",\n" +
            "        \"maximumElevationInMeters\": \"\",\n" +
            "        \"coordinateUncertaintyInMeters\": \"\",\n" +
            "        \"minimumElevationInMeters\": \"\",\n" +
            "        \"permitInformation\": \"\",\n" +
            "        \"eventRemarks\": \"\",\n" +
            "        \"verbatimLatitude\": \"\",\n" +
            "        \"verbatimLongitude\": \"\",\n" +
            "        \"expeditionCode\": \"ACEH_2012\",\n" +
            "        \"projectId\": \"3\",\n" +
            "        \"bcid\": \"ark:/99999/fk4Ckg2INDO_068\"\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";


    public geomeDataProcessor() {
    }

    public static class Tissue {
        public String tissueID;
        public String tissuePlate;
        public String tissueWell;
    }

    public static class Sample {
        public String family;
        public String phylum;
       // public String materialSampleID;
       // public String eventID;
    }

    public static class Event {
        public String country;
        public String eventID;
        public String yearCollected;
    }

    public static  class content {
        public Tissue[] Tissue;
        public Sample[] Sample;
        public Event[] Event;
    }

    public static class Item {
        public int page;
        public int limit;
        public content content;
    }

    public List<Row> getData(String json) {

        // Use Jackson to deserialize JSON response
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(
                DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        List<Row> rows = new ArrayList<Row>();

        try {

            Item item = objectMapper.readValue(json, Item.class);

            int limit = item.limit;

            /*
            //NOTE: i tried using Java 8 streams to join results.  First converted arrays to
            //lists, then used streams to try some map and filter magic.  I think its possible
            // but pretty complex
            List<Tissue> tissueList = Arrays.asList(item.content.Tissue);
            List<Sample> sampleList =  Arrays.asList(item.content.Sample);
            List<Event> eventList = Arrays.asList(item.content.Event);


            Set<Long> eventIDs = eventList.stream()
                .map(Event::getEventID)
                .collect(toSet());

            sampleList.stream()
                .filter(sample -> eventList.contains(Sample.eventID));
                 */


            for (int i = 0; i < limit; i++) {
                Row row = new Row();

                // Tissue Data
                row.tissueID = item.content.Tissue[i].tissueID;
                row.tissuePlate = item.content.Tissue[i].tissuePlate;
                row.tissueWell = item.content.Tissue[i].tissueWell;

                // Sample Data
                row.family = item.content.Sample[i].family;
                row.phylum = item.content.Sample[i].phylum;

                // Event Data

                rows.add(row);
            }

        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return rows;
    }

    public static void main(String[] args) {
        geomeDataProcessor stub = new geomeDataProcessor();
        List<Row> rows = stub.getData(stub.sampleJson);
        Iterator it = rows.iterator();

        while (it.hasNext()) {
            Row row = (Row) it.next();
            System.out.println(row.family);
        }
    }
}
