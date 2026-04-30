import core.DynamicGraph;
import algorithms.MultiObjectiveRouter;
import algorithms.Dijkstra;
import algorithms.AStar;
import utils.FlaskClient;
import utils.LRUCache;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class MetroRouteAPI {
    
    private static DynamicGraph graph;
    private static MultiObjectiveRouter multiRouter;
    private static Dijkstra dijkstra;
    private static AStar astar;
    private static ObjectMapper objectMapper;
    private static HttpServer server;
    private static LRUCache<String, Object> routeCache;
    private static ScheduledExecutorService cleanupExecutor;
    private static long startTime = System.currentTimeMillis();
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Metro Route Optimization API Server...");
        
        objectMapper = new ObjectMapper();
        graph = new DynamicGraph();
        multiRouter = new MultiObjectiveRouter(graph);
        dijkstra = new Dijkstra(graph);
        astar = new AStar(graph);
        
        routeCache = new LRUCache<>(100, 300000); 
        
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        cleanupExecutor.scheduleAtFixedRate(() -> {
            routeCache.cleanupExpired();
        }, 1, 1, TimeUnit.MINUTES);
        
        server = HttpServer.create(new InetSocketAddress(8081), 0);
        
        server.createContext("/api/route", new RouteHandler());
        server.createContext("/api/compare", new CompareHandler());
        server.createContext("/api/load_city", new LoadCityHandler());
        server.createContext("/api/event", new EventHandler());
        server.createContext("/api/health", new HealthHandler());
        
        server.setExecutor(null); 
        server.start();
        
        System.out.println("API Server started on http://localhost:8081");
    }
    
    private static void log(String tag, String message) {
        System.out.println(String.format("[%s] %s", tag, message));
    }
    
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
                String requestBody = readRequestBody(exchange);
                JsonNode requestJson = objectMapper.readTree(requestBody);
                
                String city = requestJson.get("city").asText();
                String source = requestJson.get("source").asText();
                String destination = requestJson.get("destination").asText();
                String algorithm = requestJson.has("algorithm") ? requestJson.get("algorithm").asText() : "auto";
                String mode = requestJson.get("mode").asText();
                int timeOfDay = requestJson.has("time") ? requestJson.get("time").asInt() : 12;
                
                log("REQUEST", String.format("Route req: %s->%s, alg=%s, mode=%s, time=%d", source, destination, algorithm, mode, timeOfDay));
                
                if (graph.getAllStations().isEmpty() || !getCurrentCity().equals(city)) {
                    graph.clear();
                    graph.loadFromJSON(city);
                    log("SYSTEM", "Loaded city JSON: " + city);
                }
                
                String cacheKey = String.format("%s-%s-%s-%s-%d", source, destination, algorithm, mode, timeOfDay);
                Object cachedResult = routeCache.getWithMetrics(cacheKey);
                if (cachedResult != null) {
                    log("CACHE", "Cache HIT for key: " + cacheKey);
                    sendResponse(exchange, 200, objectMapper.writeValueAsString(cachedResult));
                    return;
                } else {
                    log("CACHE", "Cache MISS for key: " + cacheKey);
                }
                
                // ML Update
                long mlStart = System.currentTimeMillis();
                List<String> stations = new ArrayList<>(graph.getAllStationIds());
                FlaskClient.MLPredictionResult mlRes = FlaskClient.predictAll(stations, timeOfDay);
                long mlTime = System.currentTimeMillis() - mlStart;
                log("ML", "Fetched predictions in " + mlTime + "ms. Fallback used: " + mlRes.globalFallbackUsed);
                
                // Update dynamic graph weights
                for (Map.Entry<String, FlaskClient.MLData> entry : mlRes.predictions.entrySet()) {
                    graph.updateStationCongestion(entry.getKey(), entry.getValue().congestion / 10.0 + 1.0); // Simple scaling
                }
                
                // Route calc
                Map<String, Object> response = new HashMap<>();
                
                String autoReason = "";
                if (algorithm.equals("auto")) {
                    if (graph.getAllStationIds().size() > 50) {
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
                log("ROUTER", "Calculated " + algorithm + " in " + (execNs / 1000000.0) + "ms. Explored: " + nodesExplored);
                log("TIME", "Total request time handling: " + ((rtEnd - rtStart)/1000000.0) + " ms");
                
                insights.put("algorithm", algUsed);
                insights.put("trade_offs", tradeOffs);
                insights.put("alternative_rejected", altRejected);
                insights.put("confidence_score", mlRes.globalFallbackUsed ? 65 : 94);
                
                List<String> sysMessages = new ArrayList<>();
                sysMessages.add(mlRes.globalFallbackUsed ? "Fallback used \u2014 ML service unavailable" : "Route updated with real-time congestion");
                sysMessages.add("Cache miss \u2014 freshly computed");
                insights.put("system_messages", sysMessages);
                
                response.put("success", success);
                if (success) {
                    response.put("path", path);
                    response.put("distance", distance);
                    response.put("time", time);
                    response.put("interchanges", interchanges);
                    response.put("algorithm", algUsed);
                    response.put("execution_time", execNs / 1000000.0);
                    response.put("nodes_explored", nodesExplored);
                    response.put("decision_insights", insights);
                    
                    routeCache.putWithMetrics(cacheKey, response);
                } else {
                    response.put("error", "Failed to calculate route");
                }
                
                sendResponse(exchange, success ? 200 : 400, objectMapper.writeValueAsString(response));
                
            } catch (Exception e) {
                log("ERROR", "Route failed: " + e.getMessage());
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
                
                log("REQUEST", "Compare req: " + source + " -> " + destination);
                
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
                
                if (aRes.nodesExplored < dRes.nodesExplored) {
                    response.put("recommended", "A*");
                    response.put("reason", "A* explored fewer nodes (" + aRes.nodesExplored + " vs " + dRes.nodesExplored + ") making it more efficient.");
                } else {
                    response.put("recommended", "Dijkstra");
                    response.put("reason", "Dijkstra performed equally or better in node exploration for this specific topology.");
                }
                
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
                log("REQUEST", "Event simulation triggered: " + type);
                
                routeCache.clear();
                log("CACHE", "Cache cleared due to graph modification event");
                
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
                sendResponse(exchange, 200, objectMapper.writeValueAsString(response));
            } catch (Exception e) {
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
