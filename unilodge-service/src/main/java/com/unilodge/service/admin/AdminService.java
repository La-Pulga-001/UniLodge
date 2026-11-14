package com.unilodge.service.admin;

import com.unilodge.common.dto.*;
import com.unilodge.common.enums.*;
import com.unilodge.common.exception.BadRequestException;
import com.unilodge.common.exception.ConflictException;
import com.unilodge.common.exception.ForbiddenException;
import com.unilodge.common.exception.NotFoundException;
import com.unilodge.common.exception.UnauthorizedException;
import com.unilodge.persistence.entity.AdminInvitation;
import com.unilodge.persistence.entity.Comment;
import com.unilodge.persistence.entity.Property;
import com.unilodge.persistence.entity.StudentPost;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.*;
import com.unilodge.persistence.specification.PropertySpecifications;
import com.unilodge.persistence.specification.StudentPostSpecifications;
import com.unilodge.persistence.specification.UserSpecifications;
import com.unilodge.service.audit.AuditService;
import com.unilodge.service.config.AppProperties;
import com.unilodge.service.mail.EmailService;
import com.unilodge.service.mapper.DtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Administrator-only operations: moderation, account care and admin governance. */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final StudentPostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SavedPropertyRepository savedPropertyRepository;
    private final AdminInvitationRepository invitationRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppProperties properties;

    public AdminService(UserRepository userRepository,
                        PropertyRepository propertyRepository,
                        StudentPostRepository postRepository,
                        CommentRepository commentRepository,
                        SavedPropertyRepository savedPropertyRepository,
                        AdminInvitationRepository invitationRepository,
                        EmailService emailService,
                        AuditService auditService,
                        AppProperties properties) {
        this.userRepository = userRepository;
        this.propertyRepository = propertyRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.savedPropertyRepository = savedPropertyRepository;
        this.invitationRepository = invitationRepository;
        this.emailService = emailService;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Overview
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public AdminStatsDto stats() {
        return new AdminStatsDto(
                userRepository.countByRole(UserRole.STUDENT),
                userRepository.countByRole(UserRole.LANDLORD),
                userRepository.countByRoleAndStatus(UserRole.LANDLORD, UserStatus.PENDING_APPROVAL),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                propertyRepository.count(),
                propertyRepository.countByStatus(ListingStatus.PENDING_APPROVAL),
                propertyRepository.countByStatus(ListingStatus.APPROVED),
                propertyRepository.countByStatus(ListingStatus.REJECTED),
                postRepository.count(),
                postRepository.countByStatus(ModerationStatus.HIDDEN),
                commentRepository.count(),
                userRepository.countByRole(UserRole.ADMIN),
                userRepository.countByRoleAndOriginalAdminTrue(UserRole.ADMIN),
                userRepository.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7))),
                savedPropertyRepository.count(),
                Instant.now());
    }

    // ------------------------------------------------------------------
    // Accounts
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserDto> users(UserRole role, UserStatus status, String q, int page, int size) {
        Page<User> result = userRepository.findAll(UserSpecifications.withFilters(role, status, q),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AdminUserDto> content = result.getContent().stream().map(DtoMapper::adminUser).toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public AdminUserDto approveLandlord(User admin, UUID userId) {
        User target = requireUser(userId);
        if (target.getRole() != UserRole.LANDLORD) {
            throw new BadRequestException("Only landlord applications can be approved.");
        }
        target.setStatus(UserStatus.ACTIVE);
        target.setAdminNote(null);
        userRepository.save(target);
        auditService.record(admin, AuditAction.LANDLORD_APPROVED, "user", target.getId(), target.getEmail(),
                "Landlord application approved");
        notifySafely(() -> emailService.sendLandlordApproved(target.getEmail(), target.getFullName()));
        return DtoMapper.adminUser(target);
    }

    @Transactional
    public AdminUserDto rejectLandlord(User admin, UUID userId, String note) {
        User target = requireUser(userId);
        if (target.getRole() != UserRole.LANDLORD) {
            throw new BadRequestException("Only landlord applications can be rejected.");
        }
        target.setStatus(UserStatus.REJECTED);
        target.setAdminNote(blankToNull(note));
        userRepository.save(target);
        auditService.record(admin, AuditAction.LANDLORD_REJECTED, "user", target.getId(), target.getEmail(),
                detail("Landlord application rejected", note));
        notifySafely(() -> emailService.sendLandlordRejected(target.getEmail(), target.getFullName(), note));
        return DtoMapper.adminUser(target);
    }

    @Transactional
    public AdminUserDto suspendUser(User admin, UUID userId, String note) {
        User target = requireUser(userId);
        if (target.getId().equals(admin.getId())) {
            throw new BadRequestException("You cannot suspend your own account.");
        }
        if (target.getRole() == UserRole.ADMIN) {
            throw new ForbiddenException("Administrator accounts cannot be suspended. "
                    + "Revoke administrator access instead.");
        }
        target.setStatus(UserStatus.SUSPENDED);
        target.setAdminNote(blankToNull(note));
        userRepository.save(target);
        auditService.record(admin, AuditAction.USER_SUSPENDED, "user", target.getId(), target.getEmail(),
                detail("Account suspended", note));
        return DtoMapper.adminUser(target);
    }

    @Transactional
    public AdminUserDto reactivateUser(User admin, UUID userId) {
        User target = requireUser(userId);
        if (target.getRole() == UserRole.ADMIN) {
            throw new ForbiddenException("Administrator accounts are managed from the administrators tab.");
        }
        target.setStatus(UserStatus.ACTIVE);
        target.setAdminNote(null);
        userRepository.save(target);
        auditService.record(admin, AuditAction.USER_REACTIVATED, "user", target.getId(), target.getEmail(),
                "Account reactivated");
        return DtoMapper.adminUser(target);
    }

    // ------------------------------------------------------------------
    // Property moderation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<PropertyDto> properties(ListingStatus status, String q, int page, int size) {
        Page<Property> result = propertyRepository.findAll(
                PropertySpecifications.withFilters(status, q, null, null, null, null, null, null, null, null),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        List<PropertyDto> content = result.getContent().stream()
                .map(property -> DtoMapper.property(property, false, true))
                .toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public PropertyDto approveProperty(User admin, UUID propertyId) {
        Property property = requireProperty(propertyId);
        property.setStatus(ListingStatus.APPROVED);
        property.setAdminNote(null);
        property.setReviewedAt(Instant.now());
        property.setReviewedBy(admin);
        propertyRepository.save(property);
        auditService.record(admin, AuditAction.PROPERTY_APPROVED, "property", property.getId(), property.getTitle(),
                "Listing approved and published");
        notifySafely(() -> emailService.sendPropertyDecision(property.getLandlord().getEmail(),
                property.getLandlord().getFullName(), property.getTitle(), true, null));
        return DtoMapper.property(property, false, true);
    }

    @Transactional
    public PropertyDto rejectProperty(User admin, UUID propertyId, String note) {
        Property property = requireProperty(propertyId);
        property.setStatus(ListingStatus.REJECTED);
        property.setAdminNote(blankToNull(note));
        property.setReviewedAt(Instant.now());
        property.setReviewedBy(admin);
        propertyRepository.save(property);
        auditService.record(admin, AuditAction.PROPERTY_REJECTED, "property", property.getId(), property.getTitle(),
                detail("Listing rejected", note));
        notifySafely(() -> emailService.sendPropertyDecision(property.getLandlord().getEmail(),
                property.getLandlord().getFullName(), property.getTitle(), false, note));
        return DtoMapper.property(property, false, true);
    }

    @Transactional
    public PropertyDto suspendProperty(User admin, UUID propertyId, String note) {
        Property property = requireProperty(propertyId);
        property.setStatus(ListingStatus.SUSPENDED);
        property.setAdminNote(blankToNull(note));
        property.setReviewedAt(Instant.now());
        property.setReviewedBy(admin);
        propertyRepository.save(property);
        auditService.record(admin, AuditAction.PROPERTY_SUSPENDED, "property", property.getId(), property.getTitle(),
                detail("Listing suspended", note));
        return DtoMapper.property(property, false, true);
    }

    @Transactional
    public PropertyDto restoreProperty(User admin, UUID propertyId) {
        Property property = requireProperty(propertyId);
        property.setStatus(ListingStatus.APPROVED);
        property.setAdminNote(null);
        propertyRepository.save(property);
        auditService.record(admin, AuditAction.PROPERTY_RESTORED, "property", property.getId(), property.getTitle(),
                "Listing restored and published");
        return DtoMapper.property(property, false, true);
    }

    // ------------------------------------------------------------------
    // Student post and comment moderation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PagedResponse<StudentPostDto> posts(ModerationStatus status, String q, int page, int size) {
        Page<StudentPost> result = postRepository.findAll(
                StudentPostSpecifications.withFilters(status, q, null, null),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Long> counts = new HashMap<>();
        List<UUID> ids = result.getContent().stream().map(StudentPost::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : commentRepository.countVisibleByPostIds(ids)) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        List<StudentPostDto> content = result.getContent().stream()
                .map(post -> DtoMapper.post(post, counts.getOrDefault(post.getId(), 0L), false))
                .toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public StudentPostDto hidePost(User admin, UUID postId, String note) {
        StudentPost post = requirePost(postId);
        post.setStatus(ModerationStatus.HIDDEN);
        post.setAdminNote(blankToNull(note));
        postRepository.save(post);
        auditService.record(admin, AuditAction.POST_HIDDEN, "student_post", post.getId(), post.getTitle(),
                detail("Post hidden from students", note));
        return DtoMapper.post(post, commentRepository.countByPostIdAndStatus(postId, ModerationStatus.VISIBLE), false);
    }

    @Transactional
    public StudentPostDto restorePost(User admin, UUID postId) {
        StudentPost post = requirePost(postId);
        post.setStatus(ModerationStatus.VISIBLE);
        post.setAdminNote(null);
        postRepository.save(post);
        auditService.record(admin, AuditAction.POST_RESTORED, "student_post", post.getId(), post.getTitle(),
                "Post restored to the board");
        return DtoMapper.post(post, commentRepository.countByPostIdAndStatus(postId, ModerationStatus.VISIBLE), false);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentDto> comments(UUID postId, int page, int size) {
        Page<Comment> result = commentRepository.findByPostIdOrderByCreatedAtAsc(postId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<CommentDto> content = result.getContent().stream().map(DtoMapper::comment).toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public CommentDto hideComment(User admin, UUID commentId) {
        Comment comment = requireComment(commentId);
        comment.setStatus(ModerationStatus.HIDDEN);
        commentRepository.save(comment);
        auditService.record(admin, AuditAction.COMMENT_HIDDEN, "comment", comment.getId(),
                snippet(comment.getBody()), "Comment hidden");
        return DtoMapper.comment(comment);
    }

    @Transactional
    public CommentDto removeComment(User admin, UUID commentId) {
        Comment comment = requireComment(commentId);
        comment.setStatus(ModerationStatus.REMOVED);
        commentRepository.save(comment);
        auditService.record(admin, AuditAction.COMMENT_REMOVED, "comment", comment.getId(),
                snippet(comment.getBody()), "Comment removed");
        return DtoMapper.comment(comment);
    }

    // ------------------------------------------------------------------
    // Administrator governance (original administrators only)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<AdminUserDto> admins() {
        return userRepository.findAll(UserSpecifications.withFilters(UserRole.ADMIN, null, null),
                        Sort.by(Sort.Direction.ASC, "createdAt"))
                .stream().map(DtoMapper::adminUser).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminInvitationDto> invitations() {
        return invitationRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DtoMapper::invitation).toList();
    }

    @Transactional
    public AdminInvitationDto inviteAdmin(User admin, InviteAdminRequest request) {
        requireOriginalAdmin(admin);
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_EXISTS",
                    "That email already has a UniLodge account. Use the promote option instead.");
        }
        if (invitationRepository.existsByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING)) {
            throw new ConflictException("INVITE_PENDING", "An invitation is already pending for that email.");
        }

        User invited = new User();
        invited.setEmail(email);
        invited.setFullName(request.fullName().trim());
        invited.setRole(UserRole.ADMIN);
        invited.setStatus(UserStatus.ACTIVE);
        invited.setEmailVerified(false);
        invited.setOriginalAdmin(false);
        userRepository.save(invited);

        AdminInvitation invitation = new AdminInvitation();
        invitation.setEmail(email);
        invitation.setToken(UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", ""));
        invitation.setInvitedBy(admin);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        invitationRepository.save(invitation);

        String link = properties.getMail().getAppUrl() + "/admin-setup.html?invite=" + invitation.getToken();
        notifySafely(() -> emailService.sendAdminInvitation(email, admin.getFullName(), link));
        auditService.record(admin, AuditAction.ADMIN_INVITED, "user", invited.getId(), email,
                "Administrator invitation sent to " + email);

        return DtoMapper.invitation(invitation);
    }

    @Transactional
    public void revokeInvitation(User admin, UUID invitationId) {
        requireOriginalAdmin(admin);
        AdminInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new NotFoundException("That invitation could not be found."));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be revoked.");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);

        userRepository.findByEmailIgnoreCase(invitation.getEmail())
                .filter(user -> user.getRole() == UserRole.ADMIN
                        && user.getPasswordHash() == null
                        && !user.isOriginalAdmin())
                .ifPresent(userRepository::delete);

        auditService.record(admin, AuditAction.ADMIN_INVITATION_REVOKED, "invitation", invitation.getId(),
                invitation.getEmail(), "Administrator invitation revoked");
    }

    @Transactional
    public AdminUserDto promoteToAdmin(User admin, UUID userId) {
        requireOriginalAdmin(admin);
        User target = requireUser(userId);
        if (target.getRole() == UserRole.ADMIN) {
            throw new ConflictException("That account is already an administrator.");
        }
        if (target.getStatus() == UserStatus.SUSPENDED) {
            throw new BadRequestException("Reactivate this account before promoting it.");
        }

        UserRole previousRole = target.getRole();
        target.setRole(UserRole.ADMIN);
        target.setStatus(UserStatus.ACTIVE);
        target.setOriginalAdmin(false);
        target.setAdminVerifiedAt(target.isEmailVerified() ? Instant.now() : null);
        target.setAdminNote(null);
        userRepository.save(target);

        auditService.record(admin, AuditAction.ADMIN_PROMOTED, "user", target.getId(), target.getEmail(),
                "Promoted from " + previousRole.name() + " to administrator"
                        + (target.isAdminVerified() ? "" : " (email verification pending)"));
        return DtoMapper.adminUser(target);
    }

    @Transactional
    public AdminUserDto revokeAdmin(User admin, UUID userId) {
        requireOriginalAdmin(admin);
        User target = requireUser(userId);
        if (target.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("That account is not an administrator.");
        }
        if (target.getId().equals(admin.getId())) {
            throw new BadRequestException("You cannot revoke your own administrator access.");
        }
        if (userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new BadRequestException("The last administrator cannot be removed.");
        }
        if (target.isOriginalAdmin()
                && userRepository.countByRoleAndOriginalAdminTrue(UserRole.ADMIN) <= 1) {
            throw new BadRequestException("At least one original administrator must remain.");
        }

        boolean keepsListings = propertyRepository.countByLandlordId(target.getId()) > 0;
        UserRole newRole = keepsListings ? UserRole.LANDLORD : UserRole.STUDENT;
        target.setRole(newRole);
        target.setStatus(UserStatus.ACTIVE);
        target.setAdminVerifiedAt(null);
        target.setOriginalAdmin(false);
        target.setAdminNote("Administrator privileges revoked by " + admin.getFullName());
        userRepository.save(target);

        auditService.record(admin, AuditAction.ADMIN_REVOKED, "user", target.getId(), target.getEmail(),
                "Administrator privileges revoked; account now " + newRole.name().toLowerCase());
        return DtoMapper.adminUser(target);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void requireOriginalAdmin(User admin) {
        if (admin == null || admin.getRole() != UserRole.ADMIN || !admin.isOriginalAdmin()
                || !admin.isAdminVerified()) {
            throw new ForbiddenException("ORIGINAL_ADMIN_ONLY",
                    "Only the original UniLodge administrators can appoint or remove administrators.");
        }
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("That account could not be found."));
    }

    private Property requireProperty(UUID id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("That listing could not be found."));
    }

    private StudentPost requirePost(UUID id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("That student post could not be found."));
    }

    private Comment requireComment(UUID id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("That comment could not be found."));
    }

    private String detail(String base, String note) {
        return blankToNull(note) == null ? base : base + ": " + note.trim();
    }

    private String snippet(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() > 120 ? clean.substring(0, 120) + "…" : clean;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void notifySafely(Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            log.warn("Unable to send notification email: {}", exception.getMessage());
        }
    }
}
