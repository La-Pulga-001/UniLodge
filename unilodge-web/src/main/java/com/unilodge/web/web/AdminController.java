package com.unilodge.web.web;

import com.unilodge.common.dto.*;
import com.unilodge.common.enums.ListingStatus;
import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.common.enums.UserRole;
import com.unilodge.common.enums.UserStatus;
import com.unilodge.persistence.entity.User;
import com.unilodge.service.admin.AdminService;
import com.unilodge.service.audit.AuditService;
import com.unilodge.service.university.UniversityService;
import com.unilodge.web.security.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Administrator workspace: moderation, accounts and governance. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;
    private final UniversityService universityService;

    public AdminController(AdminService adminService,
                           AuditService auditService,
                           UniversityService universityService) {
        this.adminService = adminService;
        this.auditService = auditService;
        this.universityService = universityService;
    }

    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return adminService.stats();
    }

    // --- Accounts ----------------------------------------------------------

    @GetMapping("/users")
    public PagedResponse<AdminUserDto> users(@RequestParam(required = false) UserRole role,
                                             @RequestParam(required = false) UserStatus status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return adminService.users(role, status, q, page, size);
    }

    @PostMapping("/users/{id}/approve-landlord")
    public AdminUserDto approveLandlord(Authentication authentication, @PathVariable UUID id) {
        return adminService.approveLandlord(AuthSupport.require(authentication), id);
    }

    @PostMapping("/users/{id}/reject-landlord")
    public AdminUserDto rejectLandlord(Authentication authentication,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) AdminActionRequest request) {
        return adminService.rejectLandlord(AuthSupport.require(authentication), id, note(request));
    }

    @PostMapping("/users/{id}/suspend")
    public AdminUserDto suspendUser(Authentication authentication,
                                    @PathVariable UUID id,
                                    @RequestBody(required = false) AdminActionRequest request) {
        return adminService.suspendUser(AuthSupport.require(authentication), id, note(request));
    }

    @PostMapping("/users/{id}/reactivate")
    public AdminUserDto reactivateUser(Authentication authentication, @PathVariable UUID id) {
        return adminService.reactivateUser(AuthSupport.require(authentication), id);
    }

    @PostMapping("/users/{id}/promote")
    public AdminUserDto promote(Authentication authentication, @PathVariable UUID id) {
        return adminService.promoteToAdmin(AuthSupport.require(authentication), id);
    }

    @PostMapping("/users/{id}/revoke-admin")
    public AdminUserDto revokeAdmin(Authentication authentication, @PathVariable UUID id) {
        return adminService.revokeAdmin(AuthSupport.require(authentication), id);
    }

    // --- Property moderation ----------------------------------------------

    @GetMapping("/properties")
    public PagedResponse<PropertyDto> properties(@RequestParam(required = false) ListingStatus status,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return adminService.properties(status, q, page, size);
    }

    @PostMapping("/properties/{id}/approve")
    public PropertyDto approveProperty(Authentication authentication, @PathVariable UUID id) {
        return adminService.approveProperty(AuthSupport.require(authentication), id);
    }

    @PostMapping("/properties/{id}/reject")
    public PropertyDto rejectProperty(Authentication authentication,
                                      @PathVariable UUID id,
                                      @RequestBody(required = false) AdminActionRequest request) {
        return adminService.rejectProperty(AuthSupport.require(authentication), id, note(request));
    }

    @PostMapping("/properties/{id}/suspend")
    public PropertyDto suspendProperty(Authentication authentication,
                                       @PathVariable UUID id,
                                       @RequestBody(required = false) AdminActionRequest request) {
        return adminService.suspendProperty(AuthSupport.require(authentication), id, note(request));
    }

    @PostMapping("/properties/{id}/restore")
    public PropertyDto restoreProperty(Authentication authentication, @PathVariable UUID id) {
        return adminService.restoreProperty(AuthSupport.require(authentication), id);
    }

    // --- Student post moderation ------------------------------------------

    @GetMapping("/posts")
    public PagedResponse<StudentPostDto> posts(@RequestParam(required = false) ModerationStatus status,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return adminService.posts(status, q, page, size);
    }

    @PostMapping("/posts/{id}/hide")
    public StudentPostDto hidePost(Authentication authentication,
                                   @PathVariable UUID id,
                                   @RequestBody(required = false) AdminActionRequest request) {
        return adminService.hidePost(AuthSupport.require(authentication), id, note(request));
    }

    @PostMapping("/posts/{id}/restore")
    public StudentPostDto restorePost(Authentication authentication, @PathVariable UUID id) {
        return adminService.restorePost(AuthSupport.require(authentication), id);
    }

    @GetMapping("/posts/{postId}/comments")
    public PagedResponse<CommentDto> comments(@PathVariable UUID postId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return adminService.comments(postId, page, size);
    }

    @PostMapping("/comments/{id}/hide")
    public CommentDto hideComment(Authentication authentication, @PathVariable UUID id) {
        return adminService.hideComment(AuthSupport.require(authentication), id);
    }

    @PostMapping("/comments/{id}/remove")
    public CommentDto removeComment(Authentication authentication, @PathVariable UUID id) {
        return adminService.removeComment(AuthSupport.require(authentication), id);
    }

    // --- Administrator governance ------------------------------------------

    @GetMapping("/admins")
    public List<AdminUserDto> admins() {
        return adminService.admins();
    }

    @GetMapping("/invitations")
    public List<AdminInvitationDto> invitations() {
        return adminService.invitations();
    }

    @PostMapping("/invitations")
    public AdminInvitationDto invite(Authentication authentication,
                                     @Valid @RequestBody InviteAdminRequest request) {
        return adminService.inviteAdmin(AuthSupport.require(authentication), request);
    }

    @DeleteMapping("/invitations/{id}")
    public MessageResponse revokeInvitation(Authentication authentication, @PathVariable UUID id) {
        adminService.revokeInvitation(AuthSupport.require(authentication), id);
        return new MessageResponse("The invitation has been revoked.");
    }

    @GetMapping("/audit")
    public PagedResponse<AuditLogDto> audit(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return auditService.list(page, size);
    }

    @GetMapping("/audit/recent")
    public List<AuditLogDto> recentAudit() {
        return auditService.recent();
    }

    // --- Institutions -------------------------------------------------------

    @GetMapping("/universities")
    public List<UniversityDto> universities() {
        return universityService.listAll();
    }

    @PostMapping("/universities")
    public UniversityDto createUniversity(Authentication authentication,
                                          @Valid @RequestBody UniversityRequest request) {
        return universityService.create(AuthSupport.require(authentication), request);
    }

    @PutMapping("/universities/{id}")
    public UniversityDto updateUniversity(Authentication authentication,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody UniversityRequest request) {
        return universityService.update(AuthSupport.require(authentication), id, request);
    }

    private String note(AdminActionRequest request) {
        return request == null ? null : request.note();
    }
}
