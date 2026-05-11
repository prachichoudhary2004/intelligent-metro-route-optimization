package utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for communicating with the Flask ML Prediction Service.
 * Implements retry logic and graceful fallback mechanisms.
 */
public class FlaskClient {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String COMPONENT = "ML_CLIENT";

    public static boolean checkHealth() {
        try {
            String response = sendGetRequest(AppConfig.ML_SERVICE_BASE_URL + "/health");
            JsonNode json = objectMapper.readTree(response);
            return json.get("status").asText().equals("healthy");
        } catch (Exception e) {
            Logger.error(COMPONENT, "ML service health check failed: " + e.getMessage());
            return false;
        }
    }

    public static MLPredictionResult predictAll(List<String> stations, int timeOfDay) {
        int maxRetries = 2;
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                if (attempt > 0) {
                    Logger.warn(COMPONENT, "Retrying ML prediction, attempt " + attempt);
                    Thread.sleep(100 * attempt); // Simple backoff
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("stations", stations);
                payload.put("time", timeOfDay);

                String jsonPayload = objectMapper.writeValueAsString(payload);
                String response = sendPostRequest(AppConfig.ML_SERVICE_BASE_URL + "/predict/congestion", jsonPayload);

                JsonNode result = objectMapper.readTree(response);
                JsonNode predictions = result.get("predictions");
                
                Map<String, MLData> dataMap = new HashMap<>();
                
                if (predictions != null) {
                    predictions.fields().forEachRemaining(entry -> {
                        String station = entry.getKey();
                        JsonNode pred = entry.getValue();
                        MLData data = new MLData(
                            pred.get("congestion").asDouble(),
                            pred.get("delay").asDouble(),
                            pred.get("demand").asInt(),
                            pred.get("confidence").asDouble(),
                            pred.get("fallback_used") != null && pred.get("fallback_used").asBoolean()
                        );
                        dataMap.put(station, data);
                    });
                }

                return new MLPredictionResult(dataMap, false);
            } catch (Exception e) {
                attempt++;
                lastException = e;
                Logger.warn(COMPONENT, "ML Prediction attempt " + (attempt) + " failed: " + e.getMessage());
            }
        }

        // Graceful Fallback if all retries fail
        Logger.error(COMPONENT, "All retries failed. Using graceful fallback logic. Error: " + lastException.getMessage());
        return getFallbackPredictions(stations, timeOfDay);
    }

    private static MLPredictionResult getFallbackPredictions(List<String> stations, int timeOfDay) {
        Map<String, MLData> dataMap = new HashMap<>();
        boolean isPeak = (8 <= timeOfDay && timeOfDay <= 10) || (17 <= timeOfDay && timeOfDay <= 19);
        
        for (String station : stations) {
            // Simplified historical simulation
            double c = 3.0 + (isPeak ? 4.0 : 0.0) + (Math.random() * 2.0);
            double d = Math.max(0.0, (c * 0.8) + (Math.random() * 1.5));
            int dem = 100 + (isPeak ? 300 : 50) + (int)(Math.random() * 100);
            dataMap.put(station, new MLData(c, d, dem, 0.5, true));
        }
        return new MLPredictionResult(dataMap, true);
    }

    private static String sendGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(2000);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return readResponse(connection);
        } else {
            throw new IOException("HTTP GET failed: " + responseCode);
        }
    }

    private static String sendPostRequest(String urlString, String jsonPayload) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(1500);
        connection.setReadTimeout(3000);

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonPayload.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            return readResponse(connection);
        } else {
            throw new IOException("HTTP POST failed: " + responseCode);
        }
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    public static class MLData {
        public final double congestion;
        public final double delay;
        public final int demand;
        public final double confidence;
        public final boolean fallbackUsed;

        public MLData(double congestion, double delay, int demand, double confidence, boolean fallbackUsed) {
            this.congestion = congestion;
            this.delay = delay;
            this.demand = demand;
            this.confidence = confidence;
            this.fallbackUsed = fallbackUsed;
        }
    }

    public static class MLPredictionResult {
        public final Map<String, MLData> predictions;
        public final boolean globalFallbackUsed;

        public MLPredictionResult(Map<String, MLData> predictions, boolean globalFallbackUsed) {
            this.predictions = predictions;
            this.globalFallbackUsed = globalFallbackUsed;
        }
    }
}
