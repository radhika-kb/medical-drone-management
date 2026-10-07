package com.medicaldrone.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PricingRuleDto {
    private Long id;

    @NotBlank
    private String ruleCode;

    @NotBlank
    @Pattern(regexp = "BASE|CATEGORY|URGENCY", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String ruleType;

    private String medicalCategory;
    private String urgency;

    @NotNull @DecimalMin("0.00")
    private BigDecimal baseFee = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00")
    private BigDecimal perKmRate = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00")
    private BigDecimal perKgRate = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00")
    private BigDecimal categoryFee = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.001")
    private BigDecimal urgencyMultiplier = BigDecimal.ONE;
    @NotNull @Min(0)
    private Integer priority = 0;
    private Boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getMedicalCategory() { return medicalCategory; }
    public void setMedicalCategory(String medicalCategory) { this.medicalCategory = medicalCategory; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public BigDecimal getPerKmRate() { return perKmRate; }
    public void setPerKmRate(BigDecimal perKmRate) { this.perKmRate = perKmRate; }
    public BigDecimal getPerKgRate() { return perKgRate; }
    public void setPerKgRate(BigDecimal perKgRate) { this.perKgRate = perKgRate; }
    public BigDecimal getCategoryFee() { return categoryFee; }
    public void setCategoryFee(BigDecimal categoryFee) { this.categoryFee = categoryFee; }
    public BigDecimal getUrgencyMultiplier() { return urgencyMultiplier; }
    public void setUrgencyMultiplier(BigDecimal urgencyMultiplier) { this.urgencyMultiplier = urgencyMultiplier; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
