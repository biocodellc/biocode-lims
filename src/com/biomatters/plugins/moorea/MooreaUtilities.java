package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;

/**
 * @author Richard
 * @version $Id$
 */
public class MooreaUtilities {

    public static final String NOT_CONNECTED_ERROR_MESSAGE = "<html><b>You must connect to the lab bench service first.</b><br><br>" +
            "To connect, right-click on the Moorea Biocode service in the Sources panel to the left.";

    public static Options getConsensusOptions(AnnotatedPluginDocument[] selectedDocuments) throws DocumentOperationException {
        DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
        if (consensusOperation == null) {
            return null;
        }
        return consensusOperation.getOptions(selectedDocuments);
    }

    public static final class Well {
        public final char letter;
        public final int number;

        public Well(char letter, int number) {
            this.letter = letter;
            this.number = number;
        }

        public Well(String wellName) {
            if(wellName == null || wellName.length() < 2) {
                throw new IllegalArgumentException("wellName must be in the form A1, or A01");
            }
            wellName = wellName.toUpperCase();
            char letter = wellName.toCharArray()[0];
            if(letter < 65 || letter > 90) {
                throw new IllegalArgumentException("wellName must be in the form A1, or A01");
            }
            this.letter = letter;

            int number;
            try {
                number = Integer.parseInt(wellName.substring(1));
            }
            catch(NumberFormatException ex) {
                throw new IllegalArgumentException("wellName must be in the form A1, or A01");
            }
            this.number = number;
        }

        /**
         *
         * @return eg. "A1"
         */
        @Override
        public String toString() {
            return "" + letter + number;
        }

        /**
         *
         * @return eg. "A01"
         */
        public String toPaddedString() {
            String number = "" + this.number;
            return "" + letter + (number.length() < 2 ? "0" : "") + number;
        }
    }

    /**
     * Take an ab1 filename and attempt to pull out the well name
     *
     * @param fileName
     * @param separator
     * @param partNumber
     * @return well or null if couldn't parse out well name
     */
    public static Well getWellFromFileName(String fileName, String separator, int partNumber) {
        String[] nameParts = fileName.split(separator);
        if(partNumber >= nameParts.length) {
            return null;
        }

        String wellStringBig = nameParts[partNumber];
        int count = 1;
        int wellNumber = -1;
        String wellNumberString = "";
        while(true) {
            if(count >= wellStringBig.length()) {
                break;
            }
            char numberChar = wellStringBig.charAt(count);
            try{
                wellNumber = Integer.parseInt(wellNumberString+numberChar);
            }
            catch(NumberFormatException ex) {
                break;
            }
            wellNumberString += numberChar;
            count++;
        }
        return new Well(wellStringBig.toUpperCase().charAt(0), wellNumber);
    }

    public static String getBarcodeFromFileName(String fileName, String separator, int partNumber) {
        String[] nameParts = fileName.split(separator);
        if(partNumber >= nameParts.length) {
            return null;
        }
        return nameParts[partNumber];
    }
}
