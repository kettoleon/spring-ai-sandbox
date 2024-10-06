package com.github.kettoleon.llm.sandbox.helgar;

public class WorldBuilder {

    private static LocationNode world = null;

    public static LocationNode getWorld() {
        if (world == null) {
            world = new LocationNode("World");
            world.addChild(getForest());
            world.addChild(new LocationNode("Town"));
        }
        return world;
    }

    private static LocationNode getForest() {
        LocationNode forest = new LocationNode("Forest");
        forest.addChild(getHunterCabin());
        forest.addChild(new LocationNode("Hunting area North"));
        forest.addChild(new LocationNode("Hunting area South"));
        return forest;
    }

    private static LocationNode getHunterCabin() {
        LocationNode hunterCabin = new LocationNode("Hunter Cabin");
        hunterCabin.addChild(new LocationNode("Bedroom"));
        hunterCabin.addChild(new LocationNode("Toilet"));
        hunterCabin.addChild(new LocationNode("Storage"));
        hunterCabin.addChild(getLivingRoom());
        return hunterCabin;
    }

    private static LocationNode getLivingRoom() {
        LocationNode livingRoom = new LocationNode("Living Room");
        livingRoom.addChild(getKitchen());
        livingRoom.addChild(new LocationNode("Fireplace"));
        livingRoom.addChild(new LocationNode("Table"));
        return livingRoom;
    }

    private static LocationNode getKitchen() {
        LocationNode kitchen = new LocationNode("Kitchen");
        kitchen.addAvailableFunction("cook");
        return kitchen;
    }
}
