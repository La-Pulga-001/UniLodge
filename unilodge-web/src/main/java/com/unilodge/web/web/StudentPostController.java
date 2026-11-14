package com.unilodge.web.web;

import com.unilodge.common.dto.*;
import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.common.enums.UserRole;
import com.unilodge.persistence.entity.User;
import com.unilodge.service.post.StudentPostService;
import com.unilodge.web.security.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Roommate and housing-request board for students. */
@RestController
@RequestMapping("/api/posts")
public class StudentPostController {

    private final StudentPostService postService;

    public StudentPostController(StudentPostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public PagedResponse<StudentPostDto> search(
            Authentication authentication,
            @RequestParam(required = false) ModerationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        User viewer = AuthSupport.current(authentication);
        boolean admin = viewer != null && viewer.getRole() == UserRole.ADMIN && viewer.isAdminVerified();
        ModerationStatus effectiveStatus = admin && status != null ? status : ModerationStatus.VISIBLE;
        return postService.search(viewer, effectiveStatus, q, universityId, authorId, page, size);
    }

    @GetMapping("/saved")
    public List<StudentPostDto> saved(Authentication authentication) {
        return postService.savedPosts(AuthSupport.require(authentication));
    }

    @GetMapping("/saved/ids")
    public List<UUID> savedIds(Authentication authentication) {
        return postService.savedIdsFor(AuthSupport.require(authentication));
    }

    @GetMapping("/{id}")
    public StudentPostDto detail(Authentication authentication, @PathVariable UUID id) {
        return postService.getDetail(AuthSupport.current(authentication), id);
    }

    @PostMapping
    public StudentPostDto create(Authentication authentication, @Valid @RequestBody StudentPostRequest request) {
        return postService.create(AuthSupport.require(authentication), request);
    }

    @PutMapping("/{id}")
    public StudentPostDto update(Authentication authentication,
                                 @PathVariable UUID id,
                                 @Valid @RequestBody StudentPostRequest request) {
        return postService.update(AuthSupport.require(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse remove(Authentication authentication, @PathVariable UUID id) {
        postService.remove(AuthSupport.require(authentication), id);
        return new MessageResponse("Your post has been removed.");
    }

    @PostMapping("/{id}/save")
    public Map<String, Boolean> toggleSaved(Authentication authentication, @PathVariable UUID id) {
        boolean saved = postService.toggleSaved(AuthSupport.require(authentication), id);
        return Map.of("saved", saved);
    }
}
