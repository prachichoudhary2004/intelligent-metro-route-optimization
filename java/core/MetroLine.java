package core;

/**
 * Represents a metro line with color and name
 */
public class MetroLine {
    private String id;
    private String name;
    private String color;
    
    public MetroLine(String id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    
    @Override
    public String toString() {
        return name + " (" + color + ")";
    }
}
