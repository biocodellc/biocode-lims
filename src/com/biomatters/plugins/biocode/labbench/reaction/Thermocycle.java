package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;

import java.util.ArrayList;
import java.util.List;
import java.sql.*;

import org.jdom.Element;

/**
 * @author steve
 * @version $Id: 14/05/2009 12:58:02 PM steve $
 */
public class Thermocycle implements XMLSerializable {

    private List<Cycle> cycles = new ArrayList<Cycle>();
    private String notes = "";
    private String name;
    private int id;

    public Thermocycle() {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Thermocycle that = (Thermocycle) o;

        if (id != that.id) return false;
        if (!GeneralUtilities.safeEquals(name, that.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + id;
        return result;
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
        return name == null ? "Untitled" : name;
    }

    public int getId() {
        return id;
    }

    public String toString() {
        return getName();
    }

    public static Thermocycle fromSQL(ResultSet resultSet) throws SQLException{
        int id = resultSet.getInt("cycle");
        String name = resultSet.getString("thermocycle.name");
        String notes = resultSet.getString("thermocycle.notes");
        Thermocycle tCycle = new Thermocycle(name, id);
        tCycle.setNotes(notes);
        Cycle currentCycle = null;
        int count = 0;
        while(resultSet.getInt("cycle") == id) {
            count++;
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
            if(!resultSet.next()) {
                break;
            }
        }
        return count > 0 ? tCycle : null;
    }

    /**
     * 
     * @param conn
     * @return the id of the newly created thermocycle record...
     * @throws SQLException
     */
    public int toSQL(LIMSConnection conn) throws SQLException{
        //create the thermocycle record
        PreparedStatement statement1 = conn.createStatement("INSERT INTO thermocycle (name, notes) VALUES (?, ?);\n");
        statement1.setString(1, getName());
        statement1.setString(2, getNotes());
        statement1.execute();
        statement1.close();

        //get the id of the thermocycle record
        PreparedStatement statement = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ? conn.createStatement("CALL IDENTITY();") : conn.createStatement("SELECT last_insert_id()");
        ResultSet resultSet = statement.executeQuery();
        resultSet.next();
        int thermoId = resultSet.getInt(1);
        statement.close();

        for(Cycle cycle : getCycles()) {
            //create a cycle record
            PreparedStatement statement2 = conn.createStatement("INSERT INTO cycle (thermocycleid, repeats) VALUES (" + thermoId + ", " + cycle.getRepeats() + ");\n");
            statement2.execute();
            statement2.close();

            //get the id of the cycle record
            statement = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ? conn.createStatement("CALL IDENTITY();") : conn.createStatement("SELECT last_insert_id()");
            resultSet = statement.executeQuery();
            resultSet.next();
            int cycleId = resultSet.getInt(1);
            statement.close();

            for(State state : cycle.getStates()) {
                //create the state record
                PreparedStatement statement3 = conn.createStatement("INSERT INTO state (cycleid, temp, length) VALUES (" + cycleId + ", " + state.getTemp() + ", " + state.getTime() + ");\n");
                statement3.execute();
                statement3.close();
            }
        }
        return thermoId;
    }

    public Element toXML() {
        Element e = new Element("thermocycle");
        e.addContent(new Element("id").setText(""+id));
        e.addContent(new Element("name").setText(name));
        e.addContent(new Element("notes").setText(notes));
        for(Cycle c : cycles) {
            e.addContent(c.toXML());
        }
        return e;
    }

    public void fromXML(Element e) throws XMLSerializationException {
        id = Integer.parseInt(e.getChildText("id"));
        name = e.getChildText("name");
        notes = e.getChildText("notes");
        cycles = new ArrayList<Cycle>();
        for(Element el : e.getChildren("cycle")) {
            cycles.add(new Cycle(el));
        }
    }

    public static final class Cycle implements XMLSerializable{
        private List<State> states = new ArrayList<State>();
        private int repeats = 1;
        private int id = 0;

        public Cycle(Element e) throws XMLSerializationException {
            fromXML(e);
        }

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

        public Element toXML() {
            Element e = new Element("cycle");
            e.addContent(new Element("repeats").setText(""+repeats));
            e.addContent(new Element("id").setText(""+id));
            for(State s : states) {
                e.addContent(s.toXML());
            }
            return e;
        }

        public void fromXML(Element e) throws XMLSerializationException {
            repeats = Integer.parseInt(e.getChildText("repeats"));
            id = Integer.parseInt(e.getChildText("id"));
            states = new ArrayList<State>();
            for(Element el : e.getChildren("state")) {
                states.add(new State(el));
            }
        }
    }


    public static final class State implements XMLSerializable{
        private int temp;
        private int time;
        private int id = -1;

        public State(Element e) throws XMLSerializationException {
            fromXML(e);
        }

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

        public int getId() {
            return id;
        }

        public Element toXML() {
            Element e = new Element("state");
            e.addContent(new Element("id").setText(""+id));
            e.addContent(new Element("temp").setText(""+temp));
            e.addContent(new Element("time").setText(""+time));
            return e;
        }

        public void fromXML(Element e) throws XMLSerializationException {
            id = Integer.parseInt(e.getChildText("id"));
            temp = Integer.parseInt(e.getChildText("temp"));
            time = Integer.parseInt(e.getChildText("time"));
        }
    }


}
