package algorithms;

import core.DynamicGraph;
import core.MetroEdge;
import core.MetroStation;
import java.util.*;

public class AStar {
    
    private DynamicGraph graph;
    
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

        public AStarResult(boolean pathFound, List<String> path, double distance, int time, int interchanges, long executionTimeNs, int nodesExplored, double cost) {
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
        String stationId;
        double gCost; // Actual cost from start
        double hCost; // Heuristic
        double fCost; // Total cost

        Node(String stationId, double gCost, double hCost) {
            this.stationId = stationId;
            this.gCost = gCost;
            this.hCost = hCost;
            this.fCost = gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost, other.fCost);
        }
    }

    private double heuristic(String from, String to) {
        MetroStation s1 = graph.getStation(from);
        MetroStation s2 = graph.getStation(to);
        if (s1 == null || s2 == null) return 5.0; // Fallback
        
        // Manhattan distance approximation (1 degree is roughly 111 km)
        double dLat = Math.abs(s1.getLatitude() - s2.getLatitude()) * 111.0;
        double dLon = Math.abs(s1.getLongitude() - s2.getLongitude()) * 111.0 * Math.cos(Math.toRadians(s1.getLatitude()));
        return (dLat + dLon) * 2.0; // Assuming 2 minutes per km
    }

    public AStarResult findPath(String source, String destination) {
        long startTime = System.nanoTime();
        
        Map<String, Double> gCosts = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>();
        
        int nodesExplored = 0;
        
        for (String stationId : graph.getAllStationIds()) {
            gCosts.put(stationId, Double.POSITIVE_INFINITY);
        }
        gCosts.put(source, 0.0);
        queue.add(new Node(source, 0.0, heuristic(source, destination)));
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            String currentStation = current.stationId;
            nodesExplored++;
            
            if (currentStation.equals(destination)) {
                break;
            }
            
            for (MetroEdge edge : graph.getNeighbors(currentStation)) {
                String neighbor = edge.getTo();
                double edgeCost = edge.getAdjustedTime(); 
                double newGCost = gCosts.get(currentStation) + edgeCost;
                
                if (newGCost < gCosts.get(neighbor)) {
                    gCosts.put(neighbor, newGCost);
                    previous.put(neighbor, currentStation);
                    queue.add(new Node(neighbor, newGCost, heuristic(neighbor, destination)));
                }
            }
        }
        
        long executionTimeNs = System.nanoTime() - startTime;
        
        List<String> path = reconstructPath(previous, source, destination);
        boolean found = !path.isEmpty() && path.get(path.size()-1).equals(destination);
        
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
        
        return new AStarResult(found, found ? path : Collections.emptyList(), totalDistance, totalTime, interchanges, executionTimeNs, nodesExplored, gCosts.get(destination));
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
}
