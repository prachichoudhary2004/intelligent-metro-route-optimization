package algorithms;

import core.DynamicGraph;
import core.MetroEdge;
import core.MetroStation;
import utils.Logger;
import java.util.*;

/**
 * Implementation of Yen's Algorithm to find K-Shortest Paths in the metro network.
 * This provides commuters with alternative route suggestions.
 */
public class YensAlgorithm {
    
    private final DynamicGraph graph;
    private final Dijkstra dijkstra;
    private static final String COMPONENT = "YEN";
    
    public YensAlgorithm(DynamicGraph graph) {
        this.graph = graph;
        this.dijkstra = new Dijkstra(graph);
    }

    public static class KPathsResult {
        public final List<Dijkstra.DijkstraResult> paths;
        public final long totalExecutionTimeNs;
        
        public KPathsResult(List<Dijkstra.DijkstraResult> paths, long totalExecutionTimeNs) {
            this.paths = paths;
            this.totalExecutionTimeNs = totalExecutionTimeNs;
        }
    }

    public KPathsResult findKShortestPaths(String source, String destination, int K) {
        long startTime = System.nanoTime();
        
        List<Dijkstra.DijkstraResult> A = new ArrayList<>();
        PriorityQueue<Dijkstra.DijkstraResult> B = new PriorityQueue<>(Comparator.comparingDouble(p -> p.cost));

        // Find the shortest path
        Dijkstra.DijkstraResult shortest = dijkstra.findPath(source, destination);
        if (!shortest.pathFound) {
            return new KPathsResult(Collections.emptyList(), System.nanoTime() - startTime);
        }
        
        A.add(shortest);

        for (int k = 1; k < K; k++) {
            // The previous shortest path
            List<String> prevPath = A.get(k - 1).path;
            
            for (int i = 0; i < prevPath.size() - 1; i++) {
                String spurNode = prevPath.get(i);
                List<String> rootPath = new ArrayList<>(prevPath.subList(0, i + 1));

                Set<String> ignoredEdges = new HashSet<>();
                Set<String> ignoredNodes = new HashSet<>();

                for (Dijkstra.DijkstraResult p : A) {
                    if (p.path.size() > i + 1 && p.path.subList(0, i + 1).equals(rootPath)) {
                        ignoredEdges.add(p.path.get(i) + "->" + p.path.get(i + 1));
                    }
                }

                for (int j = 0; j < rootPath.size() - 1; j++) {
                    ignoredNodes.add(rootPath.get(j));
                }

                Dijkstra.DijkstraResult spurPathRes = dijkstra.findPath(spurNode, destination, ignoredNodes, ignoredEdges);

                if (spurPathRes.pathFound) {
                    List<String> totalPath = new ArrayList<>(rootPath.subList(0, rootPath.size() - 1));
                    totalPath.addAll(spurPathRes.path);
                    
                    // Re-calculate full metrics for the combined path
                    Dijkstra.DijkstraResult fullPathRes = calculatePathMetrics(totalPath, spurPathRes.executionTimeNs);
                    
                    // Check if this path already exists in B or A
                    if (!containsPath(A, totalPath) && !containsPath(B, totalPath)) {
                        B.add(fullPathRes);
                    }
                }
            }

            if (B.isEmpty()) break;
            A.add(B.poll());
        }
        
        long totalTime = System.nanoTime() - startTime;
        Logger.metrics(COMPONENT, "total_execution_time", totalTime / 1000, "us");
        Logger.metrics(COMPONENT, "paths_found", A.size(), "paths");
        
        return new KPathsResult(A, totalTime);
    }

    private boolean containsPath(Collection<Dijkstra.DijkstraResult> collection, List<String> path) {
        for (Dijkstra.DijkstraResult res : collection) {
            if (res.path.equals(path)) return true;
        }
        return false;
    }

    private Dijkstra.DijkstraResult calculatePathMetrics(List<String> path, long execTimeNs) {
        double totalDistance = 0;
        int totalTime = 0;
        int interchanges = 0;
        double cost = 0;

        for (int i = 0; i < path.size() - 1; i++) {
            String curr = path.get(i);
            String nxt = path.get(i + 1);
            boolean edgeFound = false;
            for (MetroEdge edge : graph.getNeighbors(curr)) {
                if (edge.getTo().equals(nxt)) {
                    totalDistance += edge.getDistance();
                    totalTime += edge.getAdjustedTime();
                    cost += edge.getAdjustedTime(); // Using adjusted time as cost
                    edgeFound = true;
                    break;
                }
            }
            if (!edgeFound) return new Dijkstra.DijkstraResult(false, path, 0, 0, 0, 0, 0, 0);

            if (i > 0) {
                MetroStation s1 = graph.getStation(curr);
                MetroStation s2 = graph.getStation(nxt);
                if (s1 != null && s2 != null && !s1.getLine().equals(s2.getLine())) {
                    interchanges++;
                }
            }
        }

        return new Dijkstra.DijkstraResult(true, path, totalDistance, totalTime, interchanges, execTimeNs, 0, cost);
    }
}
