package algorithms;

import core.DynamicGraph;
import core.MetroEdge;
import core.MetroStation;
import java.util.*;

public class Dijkstra {
    
    private DynamicGraph graph;
    
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

        public DijkstraResult(boolean pathFound, List<String> path, double distance, int time, int interchanges, long executionTimeNs, int nodesExplored, double cost) {
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
        double cost;

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
        long startTime = System.nanoTime();
        
        Map<String, Double> costs = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>();
        
        int nodesExplored = 0;
        
        for (String stationId : graph.getAllStationIds()) {
            costs.put(stationId, Double.POSITIVE_INFINITY);
        }
        costs.put(source, 0.0);
        queue.add(new Node(source, 0.0));
        
        while (!queue.isEmpty()) {
            Node current = queue.poll();
            String currentStation = current.stationId;
            nodesExplored++;
            
            if (currentStation.equals(destination)) {
                break;
            }
            
            for (MetroEdge edge : graph.getNeighbors(currentStation)) {
                String neighbor = edge.getTo();
                // Pure Dijkstra on edge weight (distance or time). Here we'll use base distance + dynamic modifiers via effective weight if desired.
                double edgeCost = edge.getAdjustedTime(); 
                double newCost = costs.get(currentStation) + edgeCost;
                
                if (newCost < costs.get(neighbor)) {
                    costs.put(neighbor, newCost);
                    previous.put(neighbor, currentStation);
                    queue.add(new Node(neighbor, newCost));
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
        
        return new DijkstraResult(found, found ? path : Collections.emptyList(), totalDistance, totalTime, interchanges, executionTimeNs, nodesExplored, costs.get(destination));
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
