package com.unilodge.service.university;

import com.unilodge.common.dto.UniversityDto;
import com.unilodge.common.dto.UniversityRequest;
import com.unilodge.common.enums.AuditAction;
import com.unilodge.common.exception.BadRequestException;
import com.unilodge.common.exception.ConflictException;
import com.unilodge.common.exception.NotFoundException;
import com.unilodge.persistence.entity.University;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.UniversityRepository;
import com.unilodge.service.audit.AuditService;
import com.unilodge.service.mapper.DtoMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Manages the institutions the platform supports. */
@Service
public class UniversityService {

    private final UniversityRepository repository;
    private final AuditService auditService;

    public UniversityService(UniversityRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UniversityDto> listPublic() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(DtoMapper::university).toList();
    }

    @Transactional(readOnly = true)
    public List<UniversityDto> listAll() {
        return repository.findAllByOrderByNameAsc().stream().map(DtoMapper::university).toList();
    }

    @Transactional(readOnly = true)
    public University requireActive(UUID id) {
        University university = repository.findById(id)
                .orElseThrow(() -> new BadRequestException("That university could not be found."));
        if (!university.isActive()) {
            throw new BadRequestException("That university is not currently accepting listings.");
        }
        return university;
    }

    @Transactional
    public UniversityDto create(User admin, UniversityRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A university with that name already exists.");
        }
        University university = new University();
        apply(university, request, name);
        repository.save(university);
        auditService.record(admin, AuditAction.UNIVERSITY_CREATED, "university", university.getId(), name,
                "Added institution " + name);
        return DtoMapper.university(university);
    }

    @Transactional
    public UniversityDto update(User admin, UUID id, UniversityRequest request) {
        University university = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("That university could not be found."));
        String name = request.name().trim();
        if (!university.getName().equalsIgnoreCase(name) && repository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A university with that name already exists.");
        }
        apply(university, request, name);
        repository.save(university);
        auditService.record(admin, AuditAction.UNIVERSITY_UPDATED, "university", university.getId(), name,
                "Updated institution " + name);
        return DtoMapper.university(university);
    }

    private void apply(University university, UniversityRequest request, String name) {
        university.setName(name);
        university.setShortName(trimToNull(request.shortName()));
        university.setCity(trimToNull(request.city()));
        university.setRegion(trimToNull(request.region()));
        university.setLatitude(request.latitude());
        university.setLongitude(request.longitude());
        university.setActive(request.active() == null || request.active());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT).isEmpty()
                ? null : value.trim();
    }
}
