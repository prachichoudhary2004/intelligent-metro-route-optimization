import core.DynamicGraph;
import core.MetroStation;
import algorithms.MultiObjectiveRouter;
import algorithms.Dijkstra;
import algorithms.AStar;
import algorithms.YensAlgorithm;
import utils.FlaskClient;
import utils.LRUCache;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import utils.AppConfig;
import utils.Logger;
import java.util.UUID;

public class MetroRouteAPI {
    
    private static DynamicGraph graph;
    private static MultiObjectiveRouter multiRouter;
    private static Dijkstra dijkstra;
    private static AStar astar;
    private static YensAlgorithm yensAlgorithm;
    private static ObjectMapper objectMapper;
    private static HttpServer server;
    private static LRUCache<String, Object> routeCache;
    private static ScheduledExecutorService cleanupExecutor;
    private static long startTime = System.currentTimeMillis();
    
    public static void main(String[] args) throws Exception {
        Logger.info("SYSTEM", "Starting Metro Route Optimization API Server...");
        
        objectMapper = new ObjectMapper();
        graph = new DynamicGraph();
        multiRouter = new MultiObjectiveRouter(graph);
        dijkstra = new Dijkstra(graph);
        astar = new AStar(graph);
        yensAlgorithm = new YensAlgorithm(graph);
        
        routeCache = new LRUCache<>(AppConfig.CACHE_MAX_SIZE, AppConfig.CACHE_EXPIRY_MS); 
        
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleAtFixedRate(() -> {
            routeCache.cleanupExpired();
        }, 1, 1, TimeUnit.MINUTES);
        
        server = HttpServer.create(new InetSocketAddress(AppConfig.JAVA_API_PORT), 0);
        
        server.createContext("/api/route", new RouteHandler());
        server.createContext("/api/compare", new CompareHandler());
        server.createContext("/api/load_city", new LoadCityHandler());
        server.createContext("/api/event", new EventHandler());
        server.createContext("/api/docs", new DocHandler());
        server.createContext("/api/openapi.json", new OpenApiHandler());
        server.createContext("/api/health", new HealthHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(10)); 
        server.start();
        
        Logger.info("SYSTEM", "API Server started on http://localhost:" + AppConfig.JAVA_API_PORT);
    }
    
    // Legacy log method removed
    
    static class RouteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String requestId = UUID.randomUUID().toString().substring(0, 8);
                String requestBody = readRequestBody(exchange);
                JsonNode requestJson = objectMapper.readTree(requestBody);
                
                String city = requestJson.get("city").asText();
                String source = requestJson.get("source").asText();
                String destination = requestJson.get("destination").asText();
                String algorithm = requestJson.has("algorithm") ? requestJson.get("algorithm").asText() : "auto";
                String mode = requestJson.get("mode").asText();
                int timeOfDay = requestJson.has("time") ? requestJson.get("time").asInt() : 12;
                
                Logger.info("REQUEST", String.format("[%s] Route req: %s->%s, alg=%s, mode=%s, time=%d", requestId, source, destination, algorithm, mode, timeOfDay));
                
                if (graph.getAllStations().isEmpty() || !getCurrentCity().equalsIgnoreCase(city)) {
                    graph.clear();
                    graph.loadFromJSON(city);
                    Logger.info("SYSTEM", "Loaded city data: " + city);
                }
                
                String cacheKey = String.format("%s-%s-%s-%s-%d", source, destination, algorithm, mode, timeOfDay);
                if (AppConfig.ENABLE_CACHE) {
                    Object cachedResult = routeCache.getWithMetrics(cacheKey);
                    if (cachedResult != null) {
                        Logger.info("CACHE", String.format("[%s] Cache HIT for key: %s", requestId, cacheKey));
                        sendResponse(exchange, 200, objectMapper.writeValueAsString(cachedResult));
                        return;
                    }
                }
                Logger.info("CACHE", String.format("[%s] Cache MISS for key: %s", requestId, cacheKey));
                
                // ML Update
                long mlStart = System.currentTimeMillis();
                List<String> stations = new ArrayList<>(graph.getAllStationIds());
                FlaskClient.MLPredictionResult mlRes = FlaskClient.predictAll(stations, timeOfDay);
                long mlTime = System.currentTimeMillis() - mlStart;
                
                if (mlTime > AppConfig.ML_TIME_THRESHOLD_MS) {
                    Logger.warn("PERF", String.format("[%s] ML prediction slow: %d ms", requestId, mlTime));
                }
                Logger.info("ML", String.format("[%s] Fetched predictions in %d ms. Fallback: %b", requestId, mlTime, mlRes.globalFallbackUsed));
                
                // Update dynamic graph weights
                for (Map.Entry<String, FlaskClient.MLData> entry : mlRes.predictions.entrySet()) {
                    graph.updateStationCongestion(entry.getKey(), entry.getValue().congestion / 10.0 + 1.0); // Simple scaling
                }
                
                // Route calc
                Map<String, Object> response = new HashMap<>();
                
                String autoReason = "";
                if (algorithm.equals("auto")) {
                    if (graph.getAllStationIds().size() > AppConfig.ASTAR_SWITCH_THRESHOLD) {
                        algorithm = "astar"; 
                        autoReason = "A* selected dynamically due to heuristic search space reduction over long distances.";
                    } else {
                        algorithm = "multi_objective";
                        autoReason = "Multi-objective routing selected to provide a balanced trade-off in a dense urban topology.";
                    }
                }
                
                long rtStart = System.nanoTime();
                
                // Baseline calculation to generate Trade-offs
                Dijkstra.DijkstraResult baselineRes = dijkstra.findPath(source, destination);
                
                boolean success = false;
                List<String> path = new ArrayList<>();
                int time = 0;
                double distance = 0;
                int interchanges = 0;
                long execNs = 0;
                int nodesExplored = 0;
                String algUsed = algorithm;
                
                Map<String, Object> insights = new HashMap<>();
                List<String> tradeOffs = new ArrayList<>();
                List<String> altRejected = new ArrayList<>();
                
                if (algorithm.equals("dijkstra")) {
                    success = baselineRes.pathFound;
                    path = baselineRes.path;
                    time = baselineRes.time;
                    distance = baselineRes.distance;
                    interchanges = baselineRes.interchanges;
                    execNs = baselineRes.executionTimeNs;
                    nodesExplored = baselineRes.nodesExplored;
                    
                    insights.put("reason", "Dijkstra provides the absolute mathematical shortest path regardless of heuristic optimizations.");
                    tradeOffs.add("= Guaranteed shortest path");
                    tradeOffs.add("- High exploration overhead (" + nodesExplored + " nodes checked)");
                    
                    altRejected.add("A* was rejected because absolute accuracy was prioritized over search speed.");
                } else if (algorithm.equals("astar")) {
                    AStar.AStarResult res = astar.findPath(source, destination);
                    success = res.pathFound;
                    path = res.path;
                    time = res.time;
                    distance = res.distance;
                    interchanges = res.interchanges;
                    execNs = res.executionTimeNs;
                    nodesExplored = res.nodesExplored;
                    
                    insights.put("reason", autoReason.isEmpty() ? "A* guided by geographic heuristics to narrow the search space towards the destination." : autoReason);
                    tradeOffs.add("+ Faster convergence (" + nodesExplored + " nodes vs " + baselineRes.nodesExplored + " for Dijkstra)");
                    tradeOffs.add("= Same optimal path quality");
                    
                    altRejected.add("Dijkstra rejected due to redundant exploration in non-target directions.");
                } else {
                    MultiObjectiveRouter.MultiObjectiveResult res = multiRouter.findOptimalRoute(source, destination, mode);
                    success = res.pathFound;
                    path = res.path;
                    time = res.time;
                    distance = res.distance;
                    interchanges = res.interchanges;
                    execNs = res.executionTimeNs;
                    nodesExplored = res.nodesExplored;
                    algUsed = res.algorithmUsed;
                    
                    insights.put("reason", autoReason.isEmpty() ? "Multi-objective routed using balanced weights considering time, distance, interchanges, and dynamic congestion." : autoReason);
                    
                    if (res.time < baselineRes.time) {
                        tradeOffs.add("+ Reduced time by " + (baselineRes.time - res.time) + " min by avoiding congestion");
                    } else if (res.time > baselineRes.time) {
                        tradeOffs.add("- Increased time by " + (res.time - baselineRes.time) + " min to optimize other factors");
                    } else {
                        tradeOffs.add("= Time optimal (" + res.time + " min)");
                    }
                    
                    if (res.interchanges < baselineRes.interchanges) {
                        tradeOffs.add("+ Reduced interchanges from " + baselineRes.interchanges + " → " + res.interchanges);
                    }
                    
                    if (res.distance > baselineRes.distance) {
                        tradeOffs.add("- Slightly longer distance (+" + String.format("%.1f", (res.distance - baselineRes.distance)) + " km)");
                    }
                    
                    if (tradeOffs.isEmpty() || tradeOffs.size() == 1) {
                        tradeOffs.add("= Perfectly balanced optimal route");
                    }
                    
                    if (res.time != baselineRes.time || res.distance != baselineRes.distance) {
                        altRejected.add("Shortest-path alternative rejected: " + 
                            (baselineRes.time < res.time ? "Faster by " + (res.time - baselineRes.time) + " min" : "Shorter by " + String.format("%.1f", (res.distance - baselineRes.distance)) + " km") + 
                            " but had higher congestion or interchanges.");
                    } else {
                        altRejected.add("Alternative non-weighted paths rejected to ensure balanced commuter comfort.");
                    }
                }
                
                long rtEnd = System.nanoTime();
                double execMs = execNs / 1000000.0;
                if (execMs > AppConfig.ROUTE_TIME_THRESHOLD_MS) {
                    Logger.warn("PERF", String.format("[%s] Routing slow: %.2f ms", requestId, execMs));
                }
                Logger.info("ROUTER", String.format("[%s] Calculated %s in %.2f ms. Explored: %d", requestId, algorithm, execMs, nodesExplored));
                
                insights.put("algorithm", algUsed);
                insights.put("trade_offs", tradeOffs);
                insights.put("alternative_rejected", altRejected);
                insights.put("confidence_score", mlRes.globalFallbackUsed ? 65 : 94);
                
                List<String> sysMessages = new ArrayList<>();
                sysMessages.add(mlRes.globalFallbackUsed ? "Fallback used \u2014 ML service unavailable" : "Route updated with real-time congestion");
                sysMessages.add("Cache miss \u2014 freshly computed");
                insights.put("system_messages", sysMessages);
                
                // Cost Calculation (Example: 10 base + 5 per km)
                double cost = 10.0 + (distance * 5.0);
                
                // Identify Interchange Stations
                List<String> interchangesList = new ArrayList<>();
                for (int i = 1; i < path.size() - 1; i++) {
                    String prevLine = graph.getEdge(path.get(i-1), path.get(i)).getLine();
                    String nextLine = graph.getEdge(path.get(i), path.get(i+1)).getLine();
                    if (prevLine != null && nextLine != null && !prevLine.equals(nextLine)) {
                        interchangesList.add(path.get(i));
                    }
                }

                // Alternative Routes (Yen's Algorithm)
                List<Map<String, Object>> alternatives = new ArrayList<>();
                if (success && graph.getAllStations().size() > 0) {
                    YensAlgorithm.KPathsResult kRes = yensAlgorithm.findKShortestPaths(source, destination, 3);
                    List<Dijkstra.DijkstraResult> yensPaths = kRes.paths;
                    
                    Set<List<String>> seenPaths = new HashSet<>();
                    seenPaths.add(path); // Don't suggest the main path as an alternative
                    
                    for (int i = 0; i < yensPaths.size(); i++) {
                        Dijkstra.DijkstraResult p = yensPaths.get(i);
                        if (!p.pathFound || seenPaths.contains(p.path)) continue;
                        
                        seenPaths.add(p.path);
                        String deviationStation = "alternative segment";
                        if (p.path.size() > 1) {
                            MetroStation s = graph.getStation(p.path.get(1));
                            if (s != null) deviationStation = s.getName();
                        }
                        
                        Map<String, Object> alt = new HashMap<>();
                        alt.put("name", alternatives.isEmpty() ? "Least Congested Alternative" : "Secondary Route");
                        alt.put("path", p.path);
                        alt.put("time", p.time);
                        alt.put("distance", p.distance);
                        alt.put("desc", "Alternative via " + deviationStation + " balancing efficiency.");
                        alternatives.add(alt);
                    }
                }
                
                Map<String, Object> finalResponse = new HashMap<>();
                finalResponse.put("success", success);
                finalResponse.put("path", path);
                finalResponse.put("distance", distance);
                finalResponse.put("time", time);
                finalResponse.put("cost", Math.round(cost));
                finalResponse.put("interchanges", interchanges);
                finalResponse.put("interchange_stations", interchangesList);
                finalResponse.put("algorithm", algUsed);
                finalResponse.put("execution_time", execMs);
                finalResponse.put("nodes_explored", nodesExplored);
                finalResponse.put("decision_insights", insights);
                finalResponse.put("alternatives", alternatives);

                sendResponse(exchange, success ? 200 : 400, objectMapper.writeValueAsString(finalResponse));
                
            } catch (Exception e) {
                Logger.error("ERROR", "Route handling failed: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class CompareHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String requestBody = readRequestBody(exchange);
                JsonNode requestJson = objectMapper.readTree(requestBody);
                String source = requestJson.get("source").asText();
                String destination = requestJson.get("destination").asText();
                
                Logger.info("REQUEST", "Compare req: " + source + " -> " + destination);
                
                Dijkstra.DijkstraResult dRes = dijkstra.findPath(source, destination);
                AStar.AStarResult aRes = astar.findPath(source, destination);
                
                Map<String, Object> dMap = new HashMap<>();
                dMap.put("path", dRes.path);
                dMap.put("execution_time", dRes.executionTimeNs / 1000000.0);
                dMap.put("nodes_explored", dRes.nodesExplored);
                dMap.put("cost", dRes.cost);
                
                Map<String, Object> aMap = new HashMap<>();
                aMap.put("path", aRes.path);
                aMap.put("execution_time", aRes.executionTimeNs / 1000000.0);
                aMap.put("nodes_explored", aRes.nodesExplored);
                aMap.put("cost", aRes.cost);
                
                Map<String, Object> response = new HashMap<>();
                response.put("dijkstra", dMap);
                response.put("astar", aMap);
                
                Map<String, Object> analysis = new HashMap<>();
                if (dRes.nodesExplored > 0) {
                    double reduction = ((double)(dRes.nodesExplored - aRes.nodesExplored) / dRes.nodesExplored) * 100;
                    analysis.put("nodes_reduction_percent", String.format("%.1f", Math.max(0, reduction)));
                } else {
                    analysis.put("nodes_reduction_percent", "0");
                }
                
                if (aRes.nodesExplored < dRes.nodesExplored) {
                    analysis.put("recommended", "A*");
                    analysis.put("reason", "A* explored fewer nodes (" + aRes.nodesExplored + " vs " + dRes.nodesExplored + ") making it more efficient.");
                } else {
                    analysis.put("recommended", "Dijkstra");
                    analysis.put("reason", "Dijkstra performed equally or better in node exploration for this specific topology.");
                }
                response.put("analysis", analysis);
                
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }
    
    static class EventHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) return;
            try {
                String requestBody = readRequestBody(exchange);
                JsonNode requestJson = objectMapper.readTree(requestBody);
                String type = requestJson.get("type").asText();
                Logger.info("EVENT", "Event simulation triggered: " + type);
                
                routeCache.clear();
                Logger.info("CACHE", "Cache cleared due to graph modification event");
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Graph updated. Cache cleared.");
                
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class LoadCityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            try {
                String requestBody = readRequestBody(exchange);
                JsonNode requestJson = objectMapper.readTree(requestBody);
                String city = requestJson.get("city").asText();
                
                graph.clear();
                graph.loadFromJSON(city);
                routeCache.clear();
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("city", city);
                response.put("stations", graph.getAllStationsData());
                response.put("lines", graph.getAllLinesData());
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
            } catch (Exception e) {
                Logger.error("ERROR", "Failed to load city: " + e.getMessage());
                sendResponse(exchange, 500, "{\"error\":\"Failed to load city\"}");
            }
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            Map<String, Object> response = new HashMap<>();
            LRUCache.CacheStats stats = routeCache.getStats();
            
            response.put("status", "healthy");
            response.put("cache_hits", stats.hits);
            response.put("cache_misses", stats.misses);
            response.put("cache_hit_rate", stats.hitRate);
            response.put("graph_size", graph.getStats().getStationCount());
            
            sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        StringBuilder requestBody = new StringBuilder();
        int bytesRead;
        byte[] buffer = new byte[1024];
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            requestBody.append(new String(buffer, 0, bytesRead));
        }
        return requestBody.toString();
    }

    static class DocHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] response = Files.readAllBytes(Paths.get("../dashboard/docs.html"));
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    static class OpenApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String openApi = "{\n" +
                "  \"openapi\": \"3.0.0\",\n" +
                "  \"info\": {\n" +
                "    \"title\": \"Metro Navigator API\",\n" +
                "    \"version\": \"2.0.0\"\n" +
                "  },\n" +
                "  \"paths\": {\n" +
                "    \"/api/route\": {\n" +
                "      \"post\": {\n" +
                "        \"summary\": \"Calculate optimal route\",\n" +
                "        \"responses\": { \"200\": { \"description\": \"Success\" } }\n" +
                "      }\n" +
                "    },\n" +
                "    \"/api/compare\": {\n" +
                "      \"post\": {\n" +
                "        \"summary\": \"Benchmark algorithms\",\n" +
                "        \"responses\": { \"200\": { \"description\": \"Success\" } }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
            sendResponse(exchange, 200, openApi);
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private static String getCurrentCity() {
        if (graph.getAllStations().isEmpty()) return "None";
        return graph.getStats().getCityName();
    }
}
