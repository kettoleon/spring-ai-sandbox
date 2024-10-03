package com.example.demo.helgar;

import java.util.ArrayList;
import java.util.List;

public class Character {

    private static Character helgar;
    private String name;

    private String description;

    private String intention;

    private List<String> log = new ArrayList<>();
    private LocationNode location;

    public static Character getHelgar(){
        if(helgar == null) {
            helgar = new Character();
            helgar.name = "Helgar";
            helgar.description = "You are a lonely hunter in the forest. You live in you hunter's hut deep in the forest.";
            helgar.log.add("You wake up in your bedroom");
            helgar.location = WorldBuilder.getWorld().findChild("Bedroom").orElseThrow();
        }
        return helgar;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIntention() {
        return intention;
    }

    public List<String> getLog() {
        return log;
    }

    public LocationNode getLocation() {
        return location;
    }

    public void setIntention(String description) {
        intention = description;
    }

    public void setLocation(LocationNode loc) {
        location = loc;
    }
}
