package algorithms;

import core.DynamicGraph;
import core.MetroEdge;
import core.MetroStation;
import utils.Logger;
import java.util.*;

/**
 * Optimized Dijkstra's algorithm for finding the absolute shortest path.
 * Includes performance metrics and exploration tracing.
 */
public class Dijkstra {
    
    private final DynamicGraph graph;
    private static final String COMPONENT = "DIJKSTRA";
    
    public Dijkstra(DynamicGraph graph) {
        this.graph = graph;
    }

    public static class DijkstraResult {
        public final boolean pathFound;
        public final List<String> path;
        public final double distance;
        public final int time;
        public final int interchanges;
        public final long executionTimeNs;
        public final int nodesExplored;
        public final String algorithmUsed = "Dijkstra";
        public final double cost;

        public DijkstraResult(boolean pathFound, List<String> path, double distance, int time, 
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
        final double cost;

        Node(String stationId, double cost) {
            this.stationId = stationId;
            this.cost = cost;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.cost, other.cost);
        }
    }

    public DijkstraResult findPath(String source, String destination) {
        return findPath(source, destination, Collections.emptySet(), Collections.emptySet());
    }

    public DijkstraResult findPath(String source, String destination, 
                                 Set<String> ignoredNodes, Set<String> ignoredEdges) {
        long startTime = System.nanoTime();
        
        if (ignoredNodes.contains(source) || ignoredNodes.contains(destination)) {
            return new DijkstraResult(false, Collections.emptyList(), 0, 0, 0, 0, 0, 0);
        }

        Map<String, Double> minCosts = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Node> pq = new PriorityQueue<>();
        Set<String> settled = new HashSet<>();
        
        int nodesExplored = 0;
        
        minCosts.put(source, 0.0);
        pq.add(new Node(source, 0.0));
        
        while (!pq.isEmpty()) {
            Node current = pq.poll();
            String u = current.stationId;
            
            if (settled.contains(u)) continue;
            settled.add(u);
            nodesExplored++;
            
            if (u.equals(destination)) break;
            
            for (MetroEdge edge : graph.getNeighbors(u)) {
                String v = edge.getTo();
                
                // Skip ignored nodes and edges
                if (settled.contains(v) || ignoredNodes.contains(v)) continue;
                String edgeId = u + "->" + v;
                if (ignoredEdges.contains(edgeId)) continue;
                
                double weight = edge.getAdjustedTime(); 
                double newCost = minCosts.get(u) + weight;
                
                if (newCost < minCosts.getOrDefault(v, Double.MAX_VALUE)) {
                    minCosts.put(v, newCost);
                    previous.put(v, u);
                    pq.add(new Node(v, newCost));
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
        
        return new DijkstraResult(found, found ? path : Collections.emptyList(), totalDistance, 
                                totalTime, interchanges, executionTimeNs, nodesExplored, 
                                minCosts.getOrDefault(destination, 0.0));
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
