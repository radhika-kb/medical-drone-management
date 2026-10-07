package com.medicaldrone.service;

import com.medicaldrone.dto.PricingRuleDto;
import com.medicaldrone.model.MedicalRequest;
import com.medicaldrone.model.PricingRule;
import com.medicaldrone.repository.PricingRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class PricingService {

    private final PricingRuleRepository pricingRuleRepository;
    private final AuditService auditService;

    public PricingService(PricingRuleRepository pricingRuleRepository, AuditService auditService) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.auditService = auditService;
    }

    public double calculatePrice(MedicalRequest request, Double distanceKm) {
        List<PricingRule> rules = pricingRuleRepository.findAllByActiveTrueOrderByPriorityDescIdAsc();
        PricingRule base = rules.stream().filter(rule -> "BASE".equalsIgnoreCase(rule.getRuleType())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No active BASE pricing rule is configured"));

        String category = request.getMedicalItem() == null ? null : request.getMedicalItem().getCategory();
        String urgency = request.getUrgency();
        BigDecimal categoryFee = rules.stream()
                .filter(rule -> "CATEGORY".equalsIgnoreCase(rule.getRuleType()))
                .filter(rule -> category != null && category.equalsIgnoreCase(rule.getMedicalCategory()))
                .map(PricingRule::getCategoryFee).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal urgencyMultiplier = rules.stream()
                .filter(rule -> "URGENCY".equalsIgnoreCase(rule.getRuleType()))
                .filter(rule -> urgency != null && urgency.equalsIgnoreCase(rule.getUrgency()))
                .map(PricingRule::getUrgencyMultiplier).findFirst().orElse(BigDecimal.ONE);

        double distance = distanceKm != null && distanceKm > 0 ? distanceKm : 10.0;
        double weight = request.getWeightKg() != null && request.getWeightKg() > 0 ? request.getWeightKg() : 1.0;
        BigDecimal subtotal = base.getBaseFee()
                .add(base.getPerKmRate().multiply(BigDecimal.valueOf(distance)))
                .add(base.getPerKgRate().multiply(BigDecimal.valueOf(weight)))
                .add(categoryFee);
        return subtotal.multiply(urgencyMultiplier).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Transactional(readOnly = true)
    public List<PricingRuleDto> getRules() {
        return pricingRuleRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public PricingRuleDto createRule(PricingRuleDto dto, Long actorUserId, String actorEmail) {
        validateRule(dto);
        if (pricingRuleRepository.existsByRuleCodeIgnoreCase(dto.getRuleCode().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pricing rule with this code already exists");
        }
        PricingRule rule = new PricingRule();
        apply(dto, rule);
        PricingRule saved = pricingRuleRepository.save(rule);
        writeAudit(actorUserId, actorEmail, "PRICING_RULE_CREATED", saved);
        return toDto(saved);
    }

    @Transactional
    public PricingRuleDto updateRule(Long id, PricingRuleDto dto, Long actorUserId, String actorEmail) {
        validateRule(dto);
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pricing rule not found"));
        String code = dto.getRuleCode().trim();
        if (pricingRuleRepository.existsByRuleCodeIgnoreCaseAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A pricing rule with this code already exists");
        }
        apply(dto, rule);
        PricingRule saved = pricingRuleRepository.save(rule);
        writeAudit(actorUserId, actorEmail, "PRICING_RULE_UPDATED", saved);
        return toDto(saved);
    }

    @Transactional
    public void deactivateRule(Long id, Long actorUserId, String actorEmail) {
        PricingRule rule = pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pricing rule not found"));
        if (Boolean.TRUE.equals(rule.getActive()) && "BASE".equalsIgnoreCase(rule.getRuleType())) {
            boolean anotherActiveBaseExists = pricingRuleRepository.findAllByActiveTrueOrderByPriorityDescIdAsc().stream()
                    .anyMatch(candidate -> !candidate.getId().equals(id) && "BASE".equalsIgnoreCase(candidate.getRuleType()));
            if (!anotherActiveBaseExists) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "At least one active BASE pricing rule is required");
            }
        }
        rule.setActive(false);
        pricingRuleRepository.save(rule);
        writeAudit(actorUserId, actorEmail, "PRICING_RULE_DEACTIVATED", rule);
    }

    private void validateRule(PricingRuleDto dto) {
        dto.setRuleCode(dto.getRuleCode().trim());
        dto.setRuleType(dto.getRuleType().trim().toUpperCase(Locale.ROOT));
        dto.setMedicalCategory(normalize(dto.getMedicalCategory()));
        dto.setUrgency(normalize(dto.getUrgency()));
        if ("BASE".equals(dto.getRuleType()) && (dto.getMedicalCategory() != null || dto.getUrgency() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BASE rules cannot select a category or urgency");
        }
        if ("CATEGORY".equals(dto.getRuleType()) && (dto.getMedicalCategory() == null || dto.getUrgency() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CATEGORY rules require medicalCategory and cannot select urgency");
        }
        if ("URGENCY".equals(dto.getRuleType()) && (dto.getUrgency() == null || dto.getMedicalCategory() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URGENCY rules require urgency and cannot select medicalCategory");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private void apply(PricingRuleDto dto, PricingRule rule) {
        rule.setRuleCode(dto.getRuleCode().trim());
        rule.setRuleType(dto.getRuleType());
        rule.setMedicalCategory(dto.getMedicalCategory());
        rule.setUrgency(dto.getUrgency());
        rule.setBaseFee(dto.getBaseFee());
        rule.setPerKmRate(dto.getPerKmRate());
        rule.setPerKgRate(dto.getPerKgRate());
        rule.setCategoryFee(dto.getCategoryFee());
        rule.setUrgencyMultiplier(dto.getUrgencyMultiplier());
        rule.setPriority(dto.getPriority());
        rule.setActive(dto.getActive() == null || dto.getActive());
    }

    private void writeAudit(Long actorUserId, String actorEmail, String action, PricingRule rule) {
        auditService.record(actorUserId, actorEmail, action, "pricing_rules", rule.getRuleCode(),
                "ruleType=" + rule.getRuleType() + "; active=" + rule.getActive());
    }

    private PricingRuleDto toDto(PricingRule rule) {
        PricingRuleDto dto = new PricingRuleDto();
        dto.setId(rule.getId());
        dto.setRuleCode(rule.getRuleCode());
        dto.setRuleType(rule.getRuleType());
        dto.setMedicalCategory(rule.getMedicalCategory());
        dto.setUrgency(rule.getUrgency());
        dto.setBaseFee(rule.getBaseFee());
        dto.setPerKmRate(rule.getPerKmRate());
        dto.setPerKgRate(rule.getPerKgRate());
        dto.setCategoryFee(rule.getCategoryFee());
        dto.setUrgencyMultiplier(rule.getUrgencyMultiplier());
        dto.setPriority(rule.getPriority());
        dto.setActive(rule.getActive());
        dto.setCreatedAt(rule.getCreatedAt());
        dto.setUpdatedAt(rule.getUpdatedAt());
        return dto;
    }
}
