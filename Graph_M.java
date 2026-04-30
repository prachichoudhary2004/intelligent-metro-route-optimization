import java.util.*;

public class Graph_M {
    private Map<Integer, List<Edge>> adjacencyList;
    private Map<Integer, Vertex> vertices;
    
    public Graph_M() {
        adjacencyList = new HashMap<>();
        vertices = new HashMap<>();
    }
    
    public void addVertex(int id, String name) {
        vertices.put(id, new Vertex(id, name));
        adjacencyList.put(id, new ArrayList<>());
    }
    
    public void addEdge(int from, int to, double weight) {
        adjacencyList.get(from).add(new Edge(to, weight));
        adjacencyList.get(to).add(new Edge(from, weight));
    }
    
    public List<Edge> getNeighbors(int vertexId) {
        return adjacencyList.getOrDefault(vertexId, new ArrayList<>());
    }
    
    public Vertex getVertex(int id) {
        return vertices.get(id);
    }
    
    public static class Edge {
        public int to;
        public double weight;
        
        public Edge(int to, double weight) {
            this.to = to;
            this.weight = weight;
        }
    }
    
    public static class Vertex {
        public int id;
        public String name;
        
        public Vertex(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
