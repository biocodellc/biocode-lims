package com.biomatters.plugins.moorea.reaction;

import java.util.ArrayList;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;

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
    private String name;
    private int id;

    public Thermocycle() {

    }

    public Thermocycle(String name, int id) {
        this.name = name;
        this.id = id;
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

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public String toString() {
        return getName();
    }

    public static Thermocycle fromSQL(ResultSet resultSet) throws SQLException{
        int id = resultSet.getInt("thermocycle.id");
        String name = resultSet.getString("thermocycle.name");
        Thermocycle tCycle = new Thermocycle(name, id);
        Cycle currentCycle = null;
        while(resultSet.getInt("thermocycle.id") == id) {
            int cycleId = resultSet.getInt("cycle.id");
            if(currentCycle == null || currentCycle.getId() != cycleId) {
                int repeats = resultSet.getInt("cycle.repeats");
                currentCycle = new Cycle(cycleId, repeats);
                tCycle.addCycle(currentCycle);
            }
            int stateId = resultSet.getInt("state.id");
            int stateTemp = resultSet.getInt("state.temp");
            int stateLength = resultSet.getInt("state.length");
            State state = new State(stateId, stateTemp, stateLength);
            currentCycle.addState(state);
            resultSet.next();
        }
        return tCycle;
    }

    public static String toSQL(Thermocycle tCycle) {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("INSERT INTO thermocycle(name) VALUES(\""+tCycle.getName()+"\");\n");
        for(Cycle cycle : tCycle.getCycles()) {
            sqlBuilder.append("INSERT INTO cycle(thermocycleid, repeats) VALUES ((SELECT id FROM thermocycle ORDER BY id DESC LIMIT 1), "+cycle.getRepeats()+");\n");
            for(State state : cycle.getStates()) {
                sqlBuilder.append("INSERT INTO state(cycleid, temp, length) VALUES ((SELECT id FROM cycle ORDER BY id DESC LIMIT 1), "+state.getTemp()+", "+state.getTime()+");\n");
            }
        }
        return sqlBuilder.toString();
    }


    public static class Cycle {
        private List<State> states = new ArrayList<State>();
        private int repeats = 1;
        private int id = 0;

        public Cycle(int id, int repeats) {
            this.repeats = repeats;
            this.id = id;
        }

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

        public int getId() {
            return id;
        }
    }


    public static class State {
        private int temp;
        private int time;
        private int id = 0;

        public State(int id, int temp, int time) {
            this.temp = temp;
            this.time = time;
            this.id = id;
        }

        public State(int temp, int time) {
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
