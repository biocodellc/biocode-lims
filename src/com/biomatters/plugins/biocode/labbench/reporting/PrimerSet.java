package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.utilities.StringUtilities;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 7/11/2011 12:13:12 PM
 */


public class PrimerSet {

    private List<Primer> primers;


    public PrimerSet(Primer p) {
        primers = new ArrayList<Primer>(Arrays.asList(p));
    }

    public boolean contains(Primer p) {
        for(Primer primer : primers) {
            if(primer.isEquivalentTo(p)) {
                return true;
            }
        }
        return false;
    }

    public void addPrimer(Primer primer) {
        primers.add(primer);
    }

    public List<Primer> getPrimers() {
        return new ArrayList<Primer>(primers);
    }

    public String toString() {
        return StringUtilities.humanJoin(primers);
    }



    public static class Primer{
        private String name;
        private String sequence;

        public Primer(String name, String sequence) {
            this.name = name;
            this.sequence = sequence;
        }

        public String getName() {
            return name;
        }

        public String getSequence() {
            return sequence;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Primer primer = (Primer) o;

            if (!name.equals(primer.name)) return false;
            if (!sequence.equals(primer.sequence)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + sequence.hashCode();
            return result;
        }

        public boolean isEquivalentTo(Primer primer) {
            return primer.getSequence().equalsIgnoreCase(sequence);
        }

        @Override
        public String toString() {
            return name;
        }
    }


}
