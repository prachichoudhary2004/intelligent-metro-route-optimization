package utils;

import core.MetroStation;
import core.MetroEdge;
import core.MetroLine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for loading metro network data from JSON files
 */
public class JSONLoader {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Load metro network data from JSON file
     */
    public static MetroNetworkData loadMetroNetwork(String city) throws IOException {
        String jsonPath = "../data/" + city + ".json";
        String jsonContent = new String(Files.readAllBytes(Paths.get(jsonPath)));
        
        JsonNode rootNode = objectMapper.readTree(jsonContent);
        
        // Parse metadata
        JsonNode metadata = rootNode.get("metadata");
        String cityName = metadata.get("city").asText();
        double centerLat = metadata.get("center_lat").asDouble();
        double centerLon = metadata.get("center_lon").asDouble();
        
        // Parse stations
        List<MetroStation> stations = new ArrayList<>();
        JsonNode stationsNode = rootNode.get("stations");
        for (JsonNode stationNode : stationsNode) {
            MetroStation station = new MetroStation(
                stationNode.get("id").asText(),
                stationNode.get("name").asText(),
                stationNode.get("lat").asDouble(),
                stationNode.get("lon").asDouble(),
                stationNode.get("line").asText()
            );
            stations.add(station);
        }
        
        // Parse edges
        List<MetroEdge> edges = new ArrayList<>();
        JsonNode edgesNode = rootNode.get("edges");
        for (JsonNode edgeNode : edgesNode) {
            MetroEdge edge = new MetroEdge(
                edgeNode.get("from").asText(),
                edgeNode.get("to").asText(),
                edgeNode.get("distance").asDouble(),
                edgeNode.get("time").asInt(),
                edgeNode.has("line") ? edgeNode.get("line").asText() : null
            );
            edges.add(edge);
        }
        
        // Parse lines
        List<MetroLine> lines = new ArrayList<>();
        JsonNode linesNode = rootNode.get("lines");
        for (JsonNode lineNode : linesNode) {
            MetroLine line = new MetroLine(
                lineNode.get("id").asText(),
                lineNode.get("name").asText(),
                lineNode.get("color").asText()
            );
            lines.add(line);
        }
        
        return new MetroNetworkData(cityName, centerLat, centerLon, stations, edges, lines);
    }
    
    /**
     * Data class to hold loaded metro network information
     */
    public static class MetroNetworkData {
        private final String cityName;
        private final double centerLat;
        private final double centerLon;
        private final List<MetroStation> stations;
        private final List<MetroEdge> edges;
        private final List<MetroLine> lines;
        
        public MetroNetworkData(String cityName, double centerLat, double centerLon, 
                               List<MetroStation> stations, List<MetroEdge> edges, List<MetroLine> lines) {
            this.cityName = cityName;
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.stations = stations;
            this.edges = edges;
            this.lines = lines;
        }
        
        // Getters
        public String getCityName() { return cityName; }
        public double getCenterLat() { return centerLat; }
        public double getCenterLon() { return centerLon; }
        public List<MetroStation> getStations() { return stations; }
        public List<MetroEdge> getEdges() { return edges; }
        public List<MetroLine> getLines() { return lines; }
    }
}
