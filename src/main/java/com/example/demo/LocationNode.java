package com.example.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LocationNode {

    private String name;

    private LocationNode parent;

    private List<LocationNode> children = new ArrayList<>();

    private List<String> availableFunctions = new ArrayList<>();

    public LocationNode(String name) {
        this.name = name;
    }

    public String getFullName(){
        StringBuffer sb = new StringBuffer();
        sb.insert(0, name);
        if(parent != null){
            sb.insert(0, " > ");
            sb.insert(0, parent.getFullName());
        }
        return sb.toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocationNode getParent() {
        return parent;
    }

    public void setParent(LocationNode parent) {
        this.parent = parent;
    }

    public void addChild(LocationNode child){
        child.setParent(this);
        children.add(child);
    }

    public List<LocationNode> getChildren() {
        return children;
    }

    public boolean hasChildren(){
        return !children.isEmpty();
    }

    public List<LocationNode> getLeaves(){
        ArrayList<LocationNode> leaves = new ArrayList<>();
        for(LocationNode child : children){
            if(child.hasChildren()){
                leaves.addAll(child.getLeaves());
            }else{
                leaves.add(child);
            }
        }
        return leaves;
    }

    public Optional<LocationNode> findChild(String name) {
        for(LocationNode child : children){
            if(child.getName().equalsIgnoreCase(name)){
                return Optional.of(child);
            }else if(child.hasChildren()){
                Optional<LocationNode> found = child.findChild(name);
                if(found.isPresent()){
                    return found;
                }
            }
        }

        return Optional.empty();
    }

    public LocationNode findChildByFullName(String destination) {
        LocationNode current = this;
        for(String name : destination.split(">")){
            String tname = name.trim();
            if(tname.equalsIgnoreCase(current.name)){
                current = current;
            }else{
                for(LocationNode child : current.getChildren()){
                    if(tname.equalsIgnoreCase(child.name)){
                        current = child;
                        break;
                    }
                }
            }
        }
        return current;
    }

    public void addAvailableFunction(String function) {
        availableFunctions.add(function);
    }

    public List<String> getAvailableFunctions() {
        return availableFunctions;
    }
}
