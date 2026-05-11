package algorithms;

import core.DynamicGraph;
import core.MetroEdge;
import core.MetroStation;
import utils.Logger;
import java.util.*;

/**
 * A* search algorithm using Haversine distance as an admissible heuristic.
 */
public class AStar {
    
    private final DynamicGraph graph;
    private static final String COMPONENT = "ASTAR";
    private static final double EARTH_RADIUS_KM = 6371.0;
    
    public AStar(DynamicGraph graph) {
        this.graph = graph;
    }

    public static class AStarResult {
        public final boolean pathFound;
        public final List<String> path;
        public final double distance;
        public final int time;
        public final int interchanges;
        public final long executionTimeNs;
        public final int nodesExplored;
        public final String algorithmUsed = "A*";
        public final double cost;

        public AStarResult(boolean pathFound, List<String> path, double distance, int time, 
                          int interchanges, long executionTimeNs, int nodesExplored, double cost) {
            this.pathFound = pathFound;
            this.path = path;
            this.distance = distance;
            this.time = time;
            this.interchanges = interchanges;
            this.executionTimeNs = executionTimeNs;
            this.nodesExplored = nodesExplored;
            this.cost = cost;
        }
    }

    private static class Node implements Comparable<Node> {
        final String stationId;
        final double gCost; // Path cost from start
        final double fCost; // gCost + hCost (heuristic)

        Node(String stationId, double gCost, double fCost) {
            this.stationId = stationId;
            this.gCost = gCost;
            this.fCost = fCost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    /**
     * Haversine formula to calculate the great-circle distance between two points.
     * This provides an admissible heuristic (never overestimates) for geographic routing.
     */
    private double calculateHeuristic(String from, String to) {
        MetroStation s1 = graph.getStation(from);
        MetroStation s2 = graph.getStation(to);
        if (s1 == null || s2 == null) return 0;
        
        double lat1 = Math.toRadians(s1.getLatitude());
        double lon1 = Math.toRadians(s1.getLongitude());
        double lat2 = Math.toRadians(s2.getLatitude());
        double lon2 = Math.toRadians(s2.getLongitude());
        
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(lat1) * Math.cos(lat2) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distanceKm = EARTH_RADIUS_KM * c;
        
        // Convert distance to estimated time (assume optimistic 60km/h = 1 min per km)
        return distanceKm * 1.0; 
    }

    public AStarResult findPath(String source, String destination) {
        long startTime = System.nanoTime();
        
        Map<String, Double> gCosts = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Node> pq = new PriorityQueue<>();
        Set<String> settled = new HashSet<>();
        
        int nodesExplored = 0;
        
        gCosts.put(source, 0.0);
        pq.add(new Node(source, 0.0, calculateHeuristic(source, destination)));
        
        while (!pq.isEmpty()) {
            Node current = pq.poll();
            String u = current.stationId;
            
            if (settled.contains(u)) continue;
            settled.add(u);
            nodesExplored++;
            
            if (u.equals(destination)) break;
            
            for (MetroEdge edge : graph.getNeighbors(u)) {
                String v = edge.getTo();
                if (settled.contains(v)) continue;
                
                double weight = edge.getAdjustedTime(); 
                double newGCost = gCosts.get(u) + weight;
                
                if (newGCost < gCosts.getOrDefault(v, Double.MAX_VALUE)) {
                    gCosts.put(v, newGCost);
                    previous.put(v, u);
                    double fCost = newGCost + calculateHeuristic(v, destination);
                    pq.add(new Node(v, newGCost, fCost));
                }
            }
        }
        
        long executionTimeNs = System.nanoTime() - startTime;
        List<String> path = reconstructPath(previous, source, destination);
        boolean found = !path.isEmpty() && path.get(path.size() - 1).equals(destination);
        
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
        
        Logger.metrics(COMPONENT, "execution_time", executionTimeNs / 1000, "us");
        Logger.metrics(COMPONENT, "nodes_explored", nodesExplored, "nodes");
        
        return new AStarResult(found, found ? path : Collections.emptyList(), totalDistance, 
                             totalTime, interchanges, executionTimeNs, nodesExplored, 
                             gCosts.getOrDefault(destination, 0.0));
    }

    private List<String> reconstructPath(Map<String, String> previous, String source, String destination) {
        LinkedList<String> path = new LinkedList<>();
        String current = destination;
        
        if (!previous.containsKey(destination) && !source.equals(destination)) {
            return path;
        }
        
        while (current != null) {
            path.addFirst(current);
            if (current.equals(source)) break;
            current = previous.get(current);
        }
        
        return path;
    }
}
