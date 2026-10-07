package com.medicaldrone.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_rules", indexes = {
        @Index(name = "idx_pricing_rules_active_type", columnList = "active, rule_type")
})
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 80)
    private String ruleCode;

    @Column(name = "rule_type", nullable = false, length = 20)
    private String ruleType;

    @Column(name = "medical_category", length = 50)
    private String medicalCategory;

    @Column(length = 30)
    private String urgency;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFee = BigDecimal.ZERO;

    @Column(name = "per_km_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKmRate = BigDecimal.ZERO;

    @Column(name = "per_kg_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKgRate = BigDecimal.ZERO;

    @Column(name = "category_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal categoryFee = BigDecimal.ZERO;

    @Column(name = "urgency_multiplier", nullable = false, precision = 6, scale = 3)
    private BigDecimal urgencyMultiplier = BigDecimal.ONE;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() { updatedAt = LocalDateTime.now(); }

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
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
