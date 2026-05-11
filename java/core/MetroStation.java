package core;

/**
 * Represents a metro station with geographic coordinates
 */
public class MetroStation {
    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private String line;
    
    public MetroStation(String id, String name, double latitude, double longitude, String line) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.line = line;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getLine() { return line; }
    
    // Setters
    public void setLine(String line) { this.line = line; }
    
    @Override
    public String toString() {
        return name + " (" + line + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MetroStation that = (MetroStation) obj;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
