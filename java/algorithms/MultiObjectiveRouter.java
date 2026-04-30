package algorithms;

import core.DynamicGraph;
import core.MetroEdge;
import core.MetroStation;
import java.util.*;

public class MultiObjectiveRouter {
    
    private DynamicGraph graph;
    
    private double alpha = 0.4;    // Time weight
    private double beta = 0.3;     // Distance weight  
    private double gamma = 0.2;    // Interchange weight
    private double delta = 0.1;    // Congestion weight
    
    public MultiObjectiveRouter(DynamicGraph graph) {
        this.graph = graph;
    }
    
    public void setWeights(double alpha, double beta, double gamma, double delta) {
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.delta = delta;
    }
    
    public MultiObjectiveResult findOptimalRoute(String source, String destination, String mode) {
        long startTime = System.nanoTime();
        
        List<String> path;
        String algorithmUsed;
        int nodesExplored = 0;
        
        double originalAlpha = alpha, originalBeta = beta, originalGamma = gamma, originalDelta = delta;
        
        switch (mode.toLowerCase()) {
            case "fastest":
                setWeights(0.6, 0.05, 0.05, 0.3);
                algorithmUsed = "Multi-Objective Fastest";
                break;
            case "shortest":
                setWeights(0.2, 0.7, 0.05, 0.05);
                algorithmUsed = "Multi-Objective Shortest";
                break;
            case "least_interchanges":
                setWeights(0.1, 0.1, 0.7, 0.1);
                algorithmUsed = "Multi-Objective Least Interchanges";
                break;
            case "least_congested":
                setWeights(0.2, 0.1, 0.1, 0.6);
                algorithmUsed = "Multi-Objective Least Congested";
                break;
            case "balanced":
            default:
                setWeights(0.4, 0.3, 0.2, 0.1);
                algorithmUsed = "Multi-Objective Balanced";
                break;
        }
        
        Map<String, Double> costs = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<RouteNode> queue = new PriorityQueue<>();
        
        for (String stationId : graph.getAllStationIds()) {
            costs.put(stationId, Double.POSITIVE_INFINITY);
        }
        costs.put(source, 0.0);
        queue.add(new RouteNode(source, 0.0));
        
        while (!queue.isEmpty()) {
            RouteNode current = queue.poll();
            String currentStation = current.stationId;
            nodesExplored++;
            
            if (currentStation.equals(destination)) {
                break;
            }
            
            for (MetroEdge edge : graph.getNeighbors(currentStation)) {
                String neighbor = edge.getTo();
                
                double timeCost = edge.getAdjustedTime() * alpha;
                double distanceCost = edge.getAdjustedDistance() * beta;
                double congestionCost = (edge.getCongestionFactor() - 1.0) * 10 * delta;
                
                double interchangeCost = 0;
                if (previous.containsKey(currentStation)) {
                    MetroStation s1 = graph.getStation(previous.get(currentStation));
                    MetroStation s2 = graph.getStation(neighbor);
                    if (s1 != null && s2 != null && !s1.getLine().equals(s2.getLine())) {
                        interchangeCost = 10 * gamma; 
                    }
                }
                
                double edgeCost = timeCost + distanceCost + congestionCost + interchangeCost;
                double newCost = costs.get(currentStation) + edgeCost;
                
                if (newCost < costs.get(neighbor)) {
                    costs.put(neighbor, newCost);
                    previous.put(neighbor, currentStation);
                    queue.add(new RouteNode(neighbor, newCost));
                }
            }
        }
        
        path = reconstructPath(previous, source, destination);
        boolean found = !path.isEmpty() && path.get(path.size()-1).equals(destination);
        
        long executionTimeNs = System.nanoTime() - startTime;
        
        setWeights(originalAlpha, originalBeta, originalGamma, originalDelta);
        
        double totalDistance = 0;
        int totalTime = 0;
        int interchanges = 0;
        
        if (found) {
            for (int i = 0; i < path.size() - 1; i++) {
                String curr = path.get(i);
                String nxt = path.get(i + 1);
                for (MetroEdge edge : graph.getNeighbors(curr)) {
                    if (edge.getTo().equals(nxt)) {
                        totalDistance += edge.getDistance();
                        totalTime += edge.getAdjustedTime();
                        break;
                    }
                }
                if (i > 0) {
                    MetroStation s1 = graph.getStation(curr);
                    MetroStation s2 = graph.getStation(nxt);
                    if (s1 != null && s2 != null && !s1.getLine().equals(s2.getLine())) {
                        interchanges++;
                    }
                }
            }
        }
        
        return new MultiObjectiveResult(found, found ? path : Collections.emptyList(), totalDistance, totalTime, interchanges, executionTimeNs, nodesExplored, costs.get(destination), algorithmUsed);
    }
    
    private List<String> reconstructPath(Map<String, String> previous, String source, String destination) {
        List<String> path = new LinkedList<>();
        String current = destination;
        
        if (!previous.containsKey(destination) && !source.equals(destination)) {
            return path;
        }
        
        while (current != null) {
            path.add(0, current);
            if (current.equals(source)) break;
            current = previous.get(current);
        }
        
        return path;
    }
    
    private static class RouteNode implements Comparable<RouteNode> {
        String stationId;
        double cost;
        
        RouteNode(String stationId, double cost) {
            this.stationId = stationId;
            this.cost = cost;
        }
        
        @Override
        public int compareTo(RouteNode other) {
            return Double.compare(this.cost, other.cost);
        }
    }
    
    public static class MultiObjectiveResult {
        public final boolean pathFound;
        public final List<String> path;
        public final double distance;
        public final int time;
        public final int interchanges;
        public final long executionTimeNs;
        public final int nodesExplored;
        public final double cost;
        public final String algorithmUsed;
        
        public MultiObjectiveResult(boolean pathFound, List<String> path, double distance, int time, int interchanges, long executionTimeNs, int nodesExplored, double cost, String algorithmUsed) {
            this.pathFound = pathFound;
            this.path = path;
            this.distance = distance;
            this.time = time;
            this.interchanges = interchanges;
            this.executionTimeNs = executionTimeNs;
            this.nodesExplored = nodesExplored;
            this.cost = cost;
            this.algorithmUsed = algorithmUsed;
        }
    }
}
