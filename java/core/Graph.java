package core;

import java.util.*;
import java.io.*;

/**
 * Core Graph class for Metro Network
 * Refactored from Graph_M with improved modularity and separation of concerns
 */
public class Graph {
    
    /**
     * Vertex class representing a metro station
     */
    public class Vertex {
        private String name;
        private Map<String, Edge> neighbors;
        private String line;
        private int interchangePenalty;
        
        public Vertex(String name, String line) {
            this.name = name;
            this.line = line;
            this.neighbors = new HashMap<>();
            this.interchangePenalty = 0;
        }
        
        public void addEdge(String destination, int weight) {
            neighbors.put(destination, new Edge(destination, weight));
        }
        
        public void removeEdge(String destination) {
            neighbors.remove(destination);
        }
        
        // Getters
        public String getName() { return name; }
        public Map<String, Edge> getNeighbors() { return neighbors; }
        public String getLine() { return line; }
        public int getInterchangePenalty() { return interchangePenalty; }
        
        public void setInterchangePenalty(int penalty) {
            this.interchangePenalty = penalty;
        }
    }
    
    /**
     * Edge class representing connection between stations
     */
    public class Edge {
        private String destination;
        private int weight;
        private double congestionFactor;
        
        public Edge(String destination, int weight) {
            this.destination = destination;
            this.weight = weight;
            this.congestionFactor = 1.0;
        }
        
        public int getEffectiveWeight() {
            return (int) (weight * congestionFactor);
        }
        
        // Getters and setters
        public String getDestination() { return destination; }
        public int getWeight() { return weight; }
        public double getCongestionFactor() { return congestionFactor; }
        public void setCongestionFactor(double factor) { this.congestionFactor = factor; }
    }
    
    private Map<String, Vertex> vertices;
    
    public Graph() {
        this.vertices = new HashMap<>();
    }
    
    // Basic graph operations
    public void addVertex(String name, String line) {
        if (!vertices.containsKey(name)) {
            vertices.put(name, new Vertex(name, line));
        }
    }
    
    public void removeVertex(String name) {
        Vertex vertex = vertices.get(name);
        if (vertex != null) {
            // Remove all edges to this vertex
            for (Vertex v : vertices.values()) {
                v.removeEdge(name);
            }
            vertices.remove(name);
        }
    }
    
    public void addEdge(String v1, String v2, int weight) {
        Vertex vertex1 = vertices.get(v1);
        Vertex vertex2 = vertices.get(v2);
        
        if (vertex1 != null && vertex2 != null) {
            vertex1.addEdge(v2, weight);
            vertex2.addEdge(v1, weight);
        }
    }
    
    public void removeEdge(String v1, String v2) {
        Vertex vertex1 = vertices.get(v1);
        Vertex vertex2 = vertices.get(v2);
        
        if (vertex1 != null) vertex1.removeEdge(v2);
        if (vertex2 != null) vertex2.removeEdge(v1);
    }
    
    // Query operations
    public boolean containsVertex(String name) {
        return vertices.containsKey(name);
    }
    
    public boolean containsEdge(String v1, String v2) {
        Vertex vertex1 = vertices.get(v1);
        return vertex1 != null && vertex1.getNeighbors().containsKey(v2);
    }
    
    public Vertex getVertex(String name) {
        return vertices.get(name);
    }
    
    public int getVertexCount() {
        return vertices.size();
    }
    
    public int getEdgeCount() {
        int count = 0;
        for (Vertex v : vertices.values()) {
            count += v.getNeighbors().size();
        }
        return count / 2; // Each edge counted twice
    }
    
    // Display methods
    public void displayGraph() {
        System.out.println("\n=== Metro Network Map ===");
        for (String name : vertices.keySet()) {
            Vertex v = vertices.get(name);
            System.out.print(name + " (" + v.getLine() + ") -> ");
            for (Edge edge : v.getNeighbors().values()) {
                System.out.print(edge.getDestination() + "(" + edge.getWeight() + "km) ");
            }
            System.out.println();
        }
        System.out.println("========================\n");
    }
    
    public void displayStations() {
        System.out.println("\n=== Metro Stations ===");
        int i = 1;
        for (String name : vertices.keySet()) {
            Vertex v = vertices.get(name);
            System.out.println(i + ". " + name + " (" + v.getLine() + ")");
            i++;
        }
        System.out.println("=====================\n");
    }
    
    // Path validation
    public boolean hasPath(String source, String destination, Set<String> visited) {
        if (source.equals(destination)) return true;
        if (visited.contains(source)) return false;
        
        visited.add(source);
        Vertex v = vertices.get(source);
        if (v == null) return false;
        
        for (Edge edge : v.getNeighbors().values()) {
            if (hasPath(edge.getDestination(), destination, visited)) {
                return true;
            }
        }
        
        return false;
    }
    
    // Congestion management
    public void updateCongestion(String station, double factor) {
        Vertex v = vertices.get(station);
        if (v != null) {
            for (Edge edge : v.getNeighbors().values()) {
                edge.setCongestionFactor(factor);
            }
        }
    }
    
    public void resetCongestion() {
        for (Vertex v : vertices.values()) {
            for (Edge edge : v.getNeighbors().values()) {
                edge.setCongestionFactor(1.0);
            }
        }
    }
    
    // Get all vertices for iteration
    public Collection<Vertex> getAllVertices() {
        return vertices.values();
    }
    
    public Set<String> getAllStationNames() {
        return vertices.keySet();
    }
}
