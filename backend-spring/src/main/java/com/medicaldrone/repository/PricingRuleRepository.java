package com.medicaldrone.repository;

import com.medicaldrone.model.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    List<PricingRule> findAllByActiveTrueOrderByPriorityDescIdAsc();
    boolean existsByRuleCodeIgnoreCase(String ruleCode);
    boolean existsByRuleCodeIgnoreCaseAndIdNot(String ruleCode, Long id);
}
