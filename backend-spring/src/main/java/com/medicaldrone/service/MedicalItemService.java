package com.medicaldrone.service;

import com.medicaldrone.dto.MedicalItemDto;
import com.medicaldrone.model.MedicalItem;
import com.medicaldrone.repository.MedicalItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MedicalItemService {

    private final MedicalItemRepository medicalItemRepository;

    public MedicalItemService(MedicalItemRepository medicalItemRepository) {
        this.medicalItemRepository = medicalItemRepository;
    }

    public List<MedicalItemDto> getAllActiveItems() {
        return medicalItemRepository.findByActiveTrue().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<MedicalItemDto> getItemsByCategory(String category) {
        return medicalItemRepository.findByCategoryAndActiveTrue(category).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public MedicalItemDto getItemById(Long id) {
        return medicalItemRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Medical item not found with id: " + id));
    }

    @Transactional
    public MedicalItemDto createItem(MedicalItemDto dto) {
        MedicalItem item = new MedicalItem(
                dto.getCode(),
                dto.getName(),
                dto.getCategory(),
                dto.getUnit(),
                dto.getDescription()
        );
        if (dto.getActive() != null) {
            item.setActive(dto.getActive());
        }
        MedicalItem saved = medicalItemRepository.save(item);
        return toDto(saved);
    }

    public MedicalItemDto toDto(MedicalItem item) {
        return new MedicalItemDto(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getCategory(),
                item.getUnit(),
                item.getDescription(),
                item.getActive()
        );
    }
}
