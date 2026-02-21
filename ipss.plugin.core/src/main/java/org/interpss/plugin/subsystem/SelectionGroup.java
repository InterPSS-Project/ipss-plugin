package org.interpss.plugin.subsystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects all selection criteria of a PSS/E subsystem block or JOIN group
 * into typed, flat lists (ranges are expanded into individual numbers).
 *
 * kvMin / kvMax are null when no KV filter is specified.
 * kvs holds explicit single KV values from "KV r" lines.
 */
public class SelectionGroup {

    private List<Integer> areas  = new ArrayList<>();
    private List<Integer> zones  = new ArrayList<>();
    private List<Integer> owners = new ArrayList<>();
    private List<Integer> buses  = new ArrayList<>();
    private List<Double>  kvs    = new ArrayList<>();
    private Double kvMin = null;
    private Double kvMax = null;

    // -----------------------------------------------------------------------
    // Builder-style adders used by the parser
    // -----------------------------------------------------------------------

    public void addArea(int area)  { areas.add(area); }
    public void addAreaRange(int from, int to) {
        for (int i = from; i <= to; i++) areas.add(i);
    }

    public void addZone(int zone)  { zones.add(zone); }
    public void addZoneRange(int from, int to) {
        for (int i = from; i <= to; i++) zones.add(i);
    }

    public void addOwner(int owner) { owners.add(owner); }
    public void addOwnerRange(int from, int to) {
        for (int i = from; i <= to; i++) owners.add(i);
    }

    public void addBus(int bus)  { buses.add(bus); }
    public void addBusRange(int from, int to) {
        for (int i = from; i <= to; i++) buses.add(i);
    }

    public void addKv(double kv) { kvs.add(kv); }

    public void setKvRange(double min, double max) {
        this.kvMin = min;
        this.kvMax = max;
    }

    public boolean isEmpty() {
        return areas.isEmpty() && zones.isEmpty() && owners.isEmpty()
                && buses.isEmpty() && kvs.isEmpty() && kvMin == null;
    }

    // -----------------------------------------------------------------------
    // Setters (for Gson deserialization)
    // -----------------------------------------------------------------------
    public void setAreas(List<Integer> areas)   { this.areas  = areas;  }
    public void setZones(List<Integer> zones)   { this.zones  = zones;  }
    public void setOwners(List<Integer> owners) { this.owners = owners; }
    public void setBuses(List<Integer> buses)   { this.buses  = buses;  }
    public void setKvs(List<Double> kvs)        { this.kvs    = kvs;    }
    public void setKvMin(Double kvMin)          { this.kvMin  = kvMin;  }
    public void setKvMax(Double kvMax)          { this.kvMax  = kvMax;  }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    public List<Integer> getAreas()  { return areas;  }
    public List<Integer> getZones()  { return zones;  }
    public List<Integer> getOwners() { return owners; }
    public List<Integer> getBuses()  { return buses;  }
    public List<Double>  getKvs()    { return kvs;    }
    public Double getKvMin()         { return kvMin;  }
    public Double getKvMax()         { return kvMax;  }
}
