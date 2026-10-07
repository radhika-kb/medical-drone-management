package com.medicaldrone.dto;

public class GeocodeResult {
    private final String displayName;
    private final double latitude;
    private final double longitude;
    private final Long zoneId;
    private final String zoneName;

    public GeocodeResult(String displayName, double latitude, double longitude, Long zoneId, String zoneName) {
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
        this.zoneName = zoneName;
    }

    public String getDisplayName() { return displayName; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Long getZoneId() { return zoneId; }
    public String getZoneName() { return zoneName; }
}
