package com.medicaldrone.dto;

public class MedicalCenterDto {
    private Long id;
    private String name;
    private String type;
    private String address;
    private Double latitude;
    private Double longitude;
    private Long zoneId;
    private String zoneName;
    private String contactNumber;
    private String verificationStatus;
    private String operationalStatus;
    private String status;
    private boolean locationConfirmed;

    public MedicalCenterDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getOperationalStatus() { return operationalStatus; }
    public void setOperationalStatus(String operationalStatus) { this.operationalStatus = operationalStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isLocationConfirmed() { return locationConfirmed; }
    public void setLocationConfirmed(boolean locationConfirmed) { this.locationConfirmed = locationConfirmed; }
}
