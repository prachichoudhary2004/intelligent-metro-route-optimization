package core;

import utils.JSONLoader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic graph that can be loaded from JSON data
 */
public class DynamicGraph {
    
    private Map<String, MetroStation> stations;
    private Map<String, List<MetroEdge>> adjacencyList;
    private Map<String, MetroLine> lines;
    private String currentCity;
    private double centerLat;
    private double centerLon;
    
    public DynamicGraph() {
        this.stations = new HashMap<>();
        this.adjacencyList = new HashMap<>();
        this.lines = new HashMap<>();
    }
    
    /**
     * Load graph from JSON file for a specific city
     */
    public void loadFromJSON(String city) throws Exception {
        JSONLoader.MetroNetworkData data = JSONLoader.loadMetroNetwork(city);
        
        // Clear existing data
        clear();
        
        // Load stations
        for (MetroStation station : data.getStations()) {
            addStation(station);
        }
        
        // Load edges
        for (MetroEdge edge : data.getEdges()) {
            addEdge(edge);
        }
        
        // Load lines
        for (MetroLine line : data.getLines()) {
            addLine(line);
        }
        
        // Set metadata
        this.currentCity = data.getCityName();
        this.centerLat = data.getCenterLat();
        this.centerLon = data.getCenterLon();
        
        System.out.println("Loaded " + city + " metro network: " + 
                          stations.size() + " stations, " + 
                          getEdgeCount() + " edges");
    }
    
    /**
     * Clear all graph data
     */
    public void clear() {
        stations.clear();
        adjacencyList.clear();
        lines.clear();
        currentCity = null;
    }
    
    /**
     * Add a station to the graph
     */
    public void addStation(MetroStation station) {
        stations.put(station.getId(), station);
        adjacencyList.putIfAbsent(station.getId(), new ArrayList<>());
    }
    
    /**
     * Add an edge to the graph (bidirectional)
     */
    public void addEdge(MetroEdge edge) {
        // Add forward edge
        adjacencyList.getOrDefault(edge.getFrom(), new ArrayList<>()).add(edge);
        
        // Add reverse edge
        MetroEdge reverseEdge = new MetroEdge(
            edge.getTo(), edge.getFrom(), 
            edge.getDistance(), edge.getTime(), edge.getLine()
        );
        reverseEdge.setCongestionFactor(edge.getCongestionFactor());
        adjacencyList.getOrDefault(edge.getTo(), new ArrayList<>()).add(reverseEdge);
    }
    
    /**
     * Add a metro line
     */
    public void addLine(MetroLine line) {
        lines.put(line.getId(), line);
    }
    
    /**
     * Get station by ID
     */
    public MetroStation getStation(String id) {
        return stations.get(id);
    }
    
    /**
     * Get neighbors of a station
     */
    public List<MetroEdge> getNeighbors(String stationId) {
        return adjacencyList.getOrDefault(stationId, new ArrayList<>());
    }

    /**
     * Get edge between two specific stations
     */
    public MetroEdge getEdge(String from, String to) {
        List<MetroEdge> edges = adjacencyList.get(from);
        if (edges == null) return null;
        for (MetroEdge edge : edges) {
            if (edge.getTo().equals(to)) return edge;
        }
        return null;
    }
    
    /**
     * Check if station exists
     */
    public boolean containsStation(String stationId) {
        return stations.containsKey(stationId);
    }
    
    /**
     * Get all station IDs
     */
    public Set<String> getAllStationIds() {
        return stations.keySet();
    }
    
    /**
     * Get all stations
     */
    public Collection<MetroStation> getAllStations() {
        return stations.values();
    }
    
    /**
     * Get all stations as list for API response
     */
    public List<Map<String, Object>> getAllStationsData() {
        return stations.values().stream()
            .map(station -> {
                Map<String, Object> stationData = new HashMap<>();
                stationData.put("id", station.getId());
                stationData.put("name", station.getName());
                stationData.put("line", station.getLine());
                stationData.put("latitude", station.getLatitude());
                stationData.put("longitude", station.getLongitude());
                return stationData;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get all lines
     */
    public Collection<MetroLine> getAllLines() {
        return lines.values();
    }
    
    /**
     * Get all lines as list for API response
     */
    public List<Map<String, Object>> getAllLinesData() {
        return lines.values().stream()
            .map(line -> {
                Map<String, Object> lineData = new HashMap<>();
                lineData.put("id", line.getId());
                lineData.put("name", line.getName());
                lineData.put("color", line.getColor());
                return lineData;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get line by ID
     */
    public MetroLine getLine(String lineId) {
        return lines.get(lineId);
    }
    
    /**
     * Update congestion factor for all edges connected to a station
     */
    public void updateStationCongestion(String stationId, double congestionFactor) {
        List<MetroEdge> edges = adjacencyList.get(stationId);
        if (edges != null) {
            for (MetroEdge edge : edges) {
                edge.setCongestionFactor(congestionFactor);
            }
        }
    }
    
    /**
     * Update congestion factor for all edges on a specific line
     */
    public void updateLineCongestion(String lineId, double congestionFactor) {
        for (List<MetroEdge> edges : adjacencyList.values()) {
            for (MetroEdge edge : edges) {
                if (lineId.equals(edge.getLine())) {
                    edge.setCongestionFactor(congestionFactor);
                }
            }
        }
    }
    
    /**
     * Reset all congestion factors to 1.0
     */
    public void resetCongestion() {
        for (List<MetroEdge> edges : adjacencyList.values()) {
            for (MetroEdge edge : edges) {
                edge.setCongestionFactor(1.0);
            }
        }
    }
    
    /**
     * Get graph statistics
     */
    public GraphStats getStats() {
        return new GraphStats(
            stations.size(),
            getEdgeCount(),
            lines.size(),
            currentCity,
            centerLat,
            centerLon
        );
    }
    
    /**
     * Get total number of edges (counting each direction once)
     */
    private int getEdgeCount() {
        int count = 0;
        for (List<MetroEdge> edges : adjacencyList.values()) {
            count += edges.size();
        }
        return count / 2; // Divide by 2 since edges are bidirectional
    }
    
    /**
     * Data class for graph statistics
     */
    public static class GraphStats {
        private final int stationCount;
        private final int edgeCount;
        private final int lineCount;
        private final String cityName;
        private final double centerLat;
        private final double centerLon;
        
        public GraphStats(int stationCount, int edgeCount, int lineCount, 
                         String cityName, double centerLat, double centerLon) {
            this.stationCount = stationCount;
            this.edgeCount = edgeCount;
            this.lineCount = lineCount;
            this.cityName = cityName;
            this.centerLat = centerLat;
            this.centerLon = centerLon;
        }
        
        // Getters
        public int getStationCount() { return stationCount; }
        public int getEdgeCount() { return edgeCount; }
        public int getLineCount() { return lineCount; }
        public String getCityName() { return cityName; }
        public double getCenterLat() { return centerLat; }
        public double getCenterLon() { return centerLon; }
        
        @Override
        public String toString() {
            return String.format("%s Metro: %d stations, %d edges, %d lines", 
                               cityName, stationCount, edgeCount, lineCount);
        }
    }
}
