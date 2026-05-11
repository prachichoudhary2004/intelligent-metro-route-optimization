package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import core.MetroStation;
import core.DynamicGraph;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Persistence Layer mimicking a Graph Database Connector.
 * Handles the storage and retrieval of the metro network from a persistent JSON store.
 */
public class GraphRepository {
    private static final String DATA_DIR = "../data/";
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Persists a station update to the "database".
     */
    public static synchronized void updateStationMetadata(String city, MetroStation station) throws IOException {
        String path = DATA_DIR + city.toLowerCase() + ".json";
        JsonNode root = mapper.readTree(new File(path));
        
        JsonNode stations = root.get("stations");
        if (stations.isArray()) {
            for (JsonNode s : stations) {
                if (s.get("id").asText().equals(station.getId())) {
                    ((ObjectNode) s).put("name", station.getName());
                    ((ObjectNode) s).put("lat", station.getLatitude());
                    ((ObjectNode) s).put("lon", station.getLongitude());
                    break;
                }
            }
        }
        
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), root);
        Logger.info("REPO", "Persisted changes for station: " + station.getId());
    }

    /**
     * Retrieves the entire graph structure as a JSON string for API consumers.
     */
    public static String getRawNetwork(String city) throws IOException {
        return new String(Files.readAllBytes(Paths.get(DATA_DIR + city.toLowerCase() + ".json")));
    }
}
