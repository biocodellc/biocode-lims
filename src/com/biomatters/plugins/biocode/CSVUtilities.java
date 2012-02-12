package com.biomatters.plugins.biocode;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 13/02/2012 10:23:04 AM
 */


public class CSVUtilities {
    /**
     * splits a line into tokens on commas, but keeps text inside quotes together
     * @param line the line to tokenize
     * @return the line tokenized as described above
     */
     public static String[] tokenizeLine(String line) {
        List<String> tokens = new ArrayList<String>();
        int previousSplitIndex = 0;
        boolean inAQuote = false;
        for(int i=0; i < line.length(); i++) {
            char c = line.charAt(i);
            if(c == '\"') {
                inAQuote = !inAQuote;
            }
            if(!inAQuote){
                if(c == ',' || i == line.length()-1) {
                    int splitIndex = i == line.length() -1 ? i+1 : i;
                    String token = line.substring(previousSplitIndex, splitIndex);
                    tokens.add(token.replace("\"", "").trim());
                    previousSplitIndex = i+1;
                }
            }
        }
        return tokens.toArray(new String[tokens.size()]);
    }
}
