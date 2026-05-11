package api;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP Client for communicating with Flask ML services
 * Handles congestion, demand, and delay predictions
 */
public class FlaskClient {
    
    private static final String BASE_URL = "http://localhost:5000/api";
    private static final int TIMEOUT_MS = 5000;
    
    /**
     * Send HTTP POST request to Flask API
     */
    private static String sendPostRequest(String endpoint, String jsonBody) throws IOException {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            
            // Send request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = connection.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                ? connection.getInputStream() 
                : connection.getErrorStream();
            
            if (inputStream == null) {
                throw new IOException("No response from server");
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                if (responseCode >= 400) {
                    throw new IOException("HTTP " + responseCode + ": " + response.toString());
                }
                
                return response.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Send HTTP GET request to Flask API
     */
    private static String sendGetRequest(String endpoint) throws IOException {
        URL url = new URL(BASE_URL + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            
            int responseCode = connection.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                ? connection.getInputStream() 
                : connection.getErrorStream();
            
            if (inputStream == null) {
                throw new IOException("No response from server");
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                if (responseCode >= 400) {
                    throw new IOException("HTTP " + responseCode + ": " + response.toString());
                }
                
                return response.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * Predict congestion for stations
     */
    public static CongestionPrediction predictCongestion(List<String> stations, int timeWindow) {
        try {
            // Create JSON request body
            StringBuilder jsonBody = new StringBuilder();
            jsonBody.append("{\"stations\": [");
            for (int i = 0; i < stations.size(); i++) {
                if (i > 0) jsonBody.append(", ");
                jsonBody.append("\"").append(stations.get(i)).append("\"");
            }
            jsonBody.append("], \"time_window\": ").append(timeWindow).append("}");
            
            String response = sendPostRequest("/predict/congestion", jsonBody.toString());
            return parseCongestionResponse(response);
            
        } catch (IOException e) {
            System.err.println("Error predicting congestion: " + e.getMessage());
            return createFallbackCongestionPrediction(stations);
        }
    }
    
    /**
     * Predict passenger demand for stations
     */
    public static DemandPrediction predictDemand(List<String> stations, String timePeriod) {
        try {
            // Create JSON request body
            StringBuilder jsonBody = new StringBuilder();
            jsonBody.append("{\"stations\": [");
            for (int i = 0; i < stations.size(); i++) {
                if (i > 0) jsonBody.append(", ");
                jsonBody.append("\"").append(stations.get(i)).append("\"");
            }
            jsonBody.append("], \"time_period\": \"").append(timePeriod).append("\"}");
            
            String response = sendPostRequest("/predict/demand", jsonBody.toString());
            return parseDemandResponse(response);
            
        } catch (IOException e) {
            System.err.println("Error predicting demand: " + e.getMessage());
            return createFallbackDemandPrediction(stations);
        }
    }
    
    /**
     * Predict delays for route segments
     */
    public static DelayPrediction predictDelays(List<String> route) {
        try {
            // Create JSON request body
            StringBuilder jsonBody = new StringBuilder();
            jsonBody.append("{\"route\": [");
            for (int i = 0; i < route.size(); i++) {
                if (i > 0) jsonBody.append(", ");
                jsonBody.append("\"").append(route.get(i)).append("\"");
            }
            jsonBody.append("]}");
            
            String response = sendPostRequest("/predict/delays", jsonBody.toString());
            return parseDelayResponse(response);
            
        } catch (IOException e) {
            System.err.println("Error predicting delays: " + e.getMessage());
            return createFallbackDelayPrediction(route);
        }
    }
    
    /**
     * Get list of available stations
     */
    public static List<String> getStations() {
        try {
            String response = sendGetRequest("/stations");
            return parseStationsResponse(response);
        } catch (IOException e) {
            System.err.println("Error getting stations: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if ML service is healthy
     */
    public static boolean checkHealth() {
        try {
            String response = sendGetRequest("/health");
            return response.contains("\"status\": \"healthy\"");
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Parse congestion prediction response
     */
    private static CongestionPrediction parseCongestionResponse(String jsonResponse) {
        CongestionPrediction prediction = new CongestionPrediction();
        
        // Simple JSON parsing (in production, use proper JSON library)
        String[] parts = jsonResponse.split("\"predictions\":");
        if (parts.length > 1) {
            String predictionsPart = parts[1].split("}")[0] + "}";
            
            // Extract station predictions (simplified parsing)
            Map<String, CongestionInfo> predictions = new HashMap<>();
            String[] stations = predictionsPart.split("\"([^\"]+)\":");
            
            for (int i = 1; i < stations.length; i++) {
                String stationData = stations[i];
                String stationName = stationData.split("\"")[1];
                
                // Extract congestion value
                String congestionStr = extractJsonValue(stationData, "congestion");
                double congestion = Double.parseDouble(congestionStr);
                
                // Extract level
                String level = extractJsonValue(stationData, "level");
                
                predictions.put(stationName, new CongestionInfo(congestion, level));
            }
            
            prediction.setPredictions(predictions);
        }
        
        return prediction;
    }
    
    /**
     * Parse demand prediction response
     */
    private static DemandPrediction parseDemandResponse(String jsonResponse) {
        DemandPrediction prediction = new DemandPrediction();
        
        // Similar simplified parsing as congestion
        String[] parts = jsonResponse.split("\"predictions\":");
        if (parts.length > 1) {
            String predictionsPart = parts[1].split("}")[0] + "}";
            
            Map<String, DemandInfo> predictions = new HashMap<>();
            String[] stations = predictionsPart.split("\"([^\"]+)\":");
            
            for (int i = 1; i < stations.length; i++) {
                String stationData = stations[i];
                String stationName = stationData.split("\"")[1];
                
                String demandStr = extractJsonValue(stationData, "demand");
                int demand = Integer.parseInt(demandStr);
                
                String level = extractJsonValue(stationData, "level");
                
                predictions.put(stationName, new DemandInfo(demand, level));
            }
            
            prediction.setPredictions(predictions);
        }
        
        return prediction;
    }
    
    /**
     * Parse delay prediction response
     */
    private static DelayPrediction parseDelayResponse(String jsonResponse) {
        DelayPrediction prediction = new DelayPrediction();
        
        String[] parts = jsonResponse.split("\"delays\":");
        if (parts.length > 1) {
            String delaysPart = parts[1].split("}")[0] + "}";
            
            Map<String, DelayInfo> delays = new HashMap<>();
            String[] segments = delaysPart.split("\"([^\"]+)\":");
            
            for (int i = 1; i < segments.length; i++) {
                String segmentData = segments[i];
                String segmentName = segmentData.split("\"")[1];
                
                String delayStr = extractJsonValue(segmentData, "delay");
                double delay = Double.parseDouble(delayStr);
                
                String reason = extractJsonValue(segmentData, "reason");
                
                delays.put(segmentName, new DelayInfo(delay, reason));
            }
            
            prediction.setDelays(delays);
        }
        
        return prediction;
    }
    
    /**
     * Parse stations response
     */
    private static List<String> parseStationsResponse(String jsonResponse) {
        List<String> stations = new ArrayList<>();
        
        String[] parts = jsonResponse.split("\"stations\":");
        if (parts.length > 1) {
            String stationsPart = parts[1].split("]")[0] + "]";
            
            // Extract station names
            String[] stationArray = stationsPart.split("\"");
            for (int i = 1; i < stationArray.length; i += 2) {
                stations.add(stationArray[i]);
            }
        }
        
        return stations;
    }
    
    /**
     * Extract JSON value by key (simplified)
     */
    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":";
        int index = json.indexOf(pattern);
        if (index == -1) return "0";
        
        int start = index + pattern.length();
        if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end).trim();
        }
    }
    
    /**
     * Create fallback congestion prediction when service is unavailable
     */
    private static CongestionPrediction createFallbackCongestionPrediction(List<String> stations) {
        CongestionPrediction prediction = new CongestionPrediction();
        Map<String, CongestionInfo> predictions = new HashMap<>();
        
        for (String station : stations) {
            // Use simple time-based fallback
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            double congestion = 5.0; // Default medium
            
            if (8 <= hour && hour <= 10 || 17 <= hour && hour <= 19) {
                congestion = 7.0; // Rush hour
            } else if (hour >= 22 || hour <= 6) {
                congestion = 2.0; // Night
            }
            
            String level = congestion > 6 ? "high" : congestion > 3 ? "medium" : "low";
            predictions.put(station, new CongestionInfo(congestion, level));
        }
        
        prediction.setPredictions(predictions);
        return prediction;
    }
    
    /**
     * Create fallback demand prediction when service is unavailable
     */
    private static DemandPrediction createFallbackDemandPrediction(List<String> stations) {
        DemandPrediction prediction = new DemandPrediction();
        Map<String, DemandInfo> predictions = new HashMap<>();
        
        for (String station : stations) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            int demand = 200; // Default
            
            if (8 <= hour && hour <= 10 || 17 <= hour && hour <= 19) {
                demand = 400; // Rush hour
            } else if (hour >= 22 || hour <= 6) {
                demand = 100; // Night
            }
            
            String level = demand > 300 ? "high" : demand > 150 ? "medium" : "low";
            predictions.put(station, new DemandInfo(demand, level));
        }
        
        prediction.setPredictions(predictions);
        return prediction;
    }
    
    /**
     * Create fallback delay prediction when service is unavailable
     */
    private static DelayPrediction createFallbackDelayPrediction(List<String> route) {
        DelayPrediction prediction = new DelayPrediction();
        Map<String, DelayInfo> delays = new HashMap<>();
        
        for (int i = 0; i < route.size() - 1; i++) {
            String segment = route.get(i) + " -> " + route.get(i + 1);
            double delay = Math.random() * 5 + 2; // 2-7 minutes
            String reason = delay > 5 ? "heavy_congestion" : "normal_operations";
            
            delays.put(segment, new DelayInfo(delay, reason));
        }
        
        prediction.setDelays(delays);
        return prediction;
    }
    
    // Data classes for predictions
    public static class CongestionPrediction {
        private Map<String, CongestionInfo> predictions;
        
        public CongestionPrediction() {
            this.predictions = new HashMap<>();
        }
        
        public void setPredictions(Map<String, CongestionInfo> predictions) {
            this.predictions = predictions;
        }
        
        public Map<String, CongestionInfo> getPredictions() {
            return predictions;
        }
    }
    
    public static class CongestionInfo {
        private final double congestion;
        private final String level;
        
        public CongestionInfo(double congestion, String level) {
            this.congestion = congestion;
            this.level = level;
        }
        
        public double getCongestion() { return congestion; }
        public String getLevel() { return level; }
    }
    
    public static class DemandPrediction {
        private Map<String, DemandInfo> predictions;
        
        public DemandPrediction() {
            this.predictions = new HashMap<>();
        }
        
        public void setPredictions(Map<String, DemandInfo> predictions) {
            this.predictions = predictions;
        }
        
        public Map<String, DemandInfo> getPredictions() {
            return predictions;
        }
    }
    
    public static class DemandInfo {
        private final int demand;
        private final String level;
        
        public DemandInfo(int demand, String level) {
            this.demand = demand;
            this.level = level;
        }
        
        public int getDemand() { return demand; }
        public String getLevel() { return level; }
    }
    
    public static class DelayPrediction {
        private Map<String, DelayInfo> delays;
        
        public DelayPrediction() {
            this.delays = new HashMap<>();
        }
        
        public void setDelays(Map<String, DelayInfo> delays) {
            this.delays = delays;
        }
        
        public Map<String, DelayInfo> getDelays() {
            return delays;
        }
    }
    
    public static class DelayInfo {
        private final double delay;
        private final String reason;
        
        public DelayInfo(double delay, String reason) {
            this.delay = delay;
            this.reason = reason;
        }
        
        public double getDelay() { return delay; }
        public String getReason() { return reason; }
    }
}
