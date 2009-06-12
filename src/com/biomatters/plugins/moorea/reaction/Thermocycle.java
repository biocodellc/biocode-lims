package com.biomatters.plugins.moorea.reaction;

import com.biomatters.plugins.moorea.TransactionException;

import java.util.ArrayList;
import java.util.List;
import java.sql.*;

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
        String notes = resultSet.getString("thermocycle.notes");
        Thermocycle tCycle = new Thermocycle(name, id);
        tCycle.setNotes(notes);
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
            if(resultSet.next() == false) {
                break;
            }
        }
        return tCycle;
    }

    /**
     * 
     * @param conn
     * @return the id of the newly created thermocycle record...
     * @throws SQLException
     */
    public int toSQL(Connection conn) throws SQLException{
        //create the thermocycle record
        PreparedStatement statement1 = conn.prepareStatement("INSERT INTO thermocycle (name, notes) VALUES (?, ?);\n");
        statement1.setString(1, getName());
        statement1.setString(2, getNotes());
        statement1.execute();

        //get the id of the thermocycle record
        PreparedStatement statement = conn.prepareStatement("SELECT last_insert_id() AS new_id;\n");
        ResultSet resultSet = statement.executeQuery();
        resultSet.next();
        int thermoId = resultSet.getInt("new_id");

        for(Cycle cycle : getCycles()) {
            //create a cycle record
            conn.prepareStatement("INSERT INTO cycle (thermocycleid, repeats) VALUES (" + thermoId + ", " + cycle.getRepeats() + ");\n").execute();

            //get the id of the cycle record
            statement = conn.prepareStatement("SELECT last_insert_id() AS new_cycle_id;\n");
            resultSet = statement.executeQuery();
            resultSet.next();
            int cycleId = resultSet.getInt("new_cycle_id");


            for(State state : cycle.getStates()) {
                //create the state record
                conn.prepareStatement("INSERT INTO state (cycleid, temp, length) VALUES (" + cycleId + ", " + state.getTemp() + ", " + state.getTime() + ");\n").execute();
            }
        }
        return thermoId;
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
