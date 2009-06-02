package com.biomatters.plugins.moorea;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 14/05/2009
 * Time: 12:58:02 PM
 * To change this template use File | Settings | File Templates.
 */
public class Thermocycle {

    private List<Cycle> cycles = new ArrayList<Cycle>();
    private String notes = "";

    public Thermocycle() {

    }

    public void addCycle(Cycle c) {
        cycles.add(c);
    }

    public List<Cycle> getCycles() {
        return new ArrayList<Cycle>(cycles);
    }

    public void setCycles(List<Cycle> cycles) {
        this.cycles = cycles;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    static class Cycle {
        private List<State> states = new ArrayList<State>();
        private int repeats = 1;

        public Cycle(int repeats) {
            this.repeats = repeats;    
        }

        public void addState(State s) {
           states.add(s);
        }

        public List<State> getStates() {
            return new ArrayList<State>(states);
        }

        public int getRepeats() {
            return repeats;
        }
    }


    static class State {
        private int temp;
        private int time;

        State(int temp, int time) {
            this.temp = temp;
            this.time = time;
        }

        public int getTemp() {
            return temp;
        }

        public int getTime() {
            return time;
        }

        public void setTemp(int temp) {
            this.temp = temp;
        }

        public void setTime(int time) {
            this.time = time;
        }
    }


}
