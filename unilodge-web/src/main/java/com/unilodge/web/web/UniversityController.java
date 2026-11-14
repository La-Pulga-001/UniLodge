package com.unilodge.web.web;

import com.unilodge.common.dto.UniversityDto;
import com.unilodge.service.university.UniversityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public list of institutions that accept UniLodge listings. */
@RestController
@RequestMapping("/api/universities")
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping
    public List<UniversityDto> list() {
        return universityService.listPublic();
    }
}
