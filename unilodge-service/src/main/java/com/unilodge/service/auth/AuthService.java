package com.unilodge.service.auth;

import com.unilodge.common.dto.*;
import com.unilodge.common.enums.*;
import com.unilodge.common.exception.*;
import com.unilodge.persistence.entity.AdminInvitation;
import com.unilodge.persistence.entity.University;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.AdminInvitationRepository;
import com.unilodge.persistence.repository.UniversityRepository;
import com.unilodge.persistence.repository.UserRepository;
import com.unilodge.service.audit.AuditService;
import com.unilodge.service.config.AppProperties;
import com.unilodge.service.mail.EmailService;
import com.unilodge.service.mapper.DtoMapper;
import com.unilodge.service.security.LoginAttemptService;
import com.unilodge.service.security.OtpService;
import com.unilodge.service.security.TokenService;
import com.unilodge.service.storage.FileStorageService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, sign-in, profile, password recovery and administrator
 * activation flows.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UniversityRepository universityRepository;
    private final AdminInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final LoginAttemptService loginAttemptService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository,
                       UniversityRepository universityRepository,
                       AdminInvitationRepository invitationRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       OtpService otpService,
                       EmailService emailService,
                       LoginAttemptService loginAttemptService,
                       FileStorageService fileStorageService,
                       AuditService auditService,
                       AppProperties properties) {
        this.userRepository = userRepository;
        this.universityRepository = universityRepository;
        this.invitationRepository = invitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.otpService = otpService;
        this.emailService = emailService;
        this.loginAttemptService = loginAttemptService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Registration and sign-in
    // ------------------------------------------------------------------

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.role() != UserRole.STUDENT && request.role() != UserRole.LANDLORD) {
            throw new BadRequestException("Choose whether you are joining as a student or a landlord.");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with that email already exists.");
        }

        University university = null;
        if (request.role() == UserRole.STUDENT) {
            if (request.universityId() == null) {
                throw new BadRequestException("Please select your university.");
            }
            university = requireActiveUniversity(request.universityId());
        } else if (request.universityId() != null) {
            university = requireActiveUniversity(request.universityId());
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));
        user.setRole(request.role());
        user.setStatus(request.role() == UserRole.LANDLORD ? UserStatus.PENDING_APPROVAL : UserStatus.ACTIVE);
        user.setUniversity(university);
        user.setBusinessName(request.role() == UserRole.LANDLORD ? blankToNull(request.businessName()) : null);
        user.setEmailVerified(false);
        user.setOriginalAdmin(false);
        userRepository.save(user);

        return new AuthResponse(tokenService.issueAccessToken(user), DtoMapper.user(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        loginAttemptService.checkAllowed(email);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new UnauthorizedException("Email or password is incorrect.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED",
                    user.getAdminNote() == null || user.getAdminNote().isBlank()
                            ? "This account has been suspended. Please contact UniLodge support."
                            : "This account has been suspended: " + user.getAdminNote());
        }

        loginAttemptService.recordSuccess(email);
        return new AuthResponse(tokenService.issueAccessToken(user), DtoMapper.user(user));
    }

    @Transactional(readOnly = true)
    public User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UnauthorizedException("Your account could not be found. Please sign in again."));
    }

    @Transactional(readOnly = true)
    public UserResponse profile(User current) {
        return DtoMapper.user(requireUser(current.getId()));
    }

    // ------------------------------------------------------------------
    // Profile
    // ------------------------------------------------------------------

    @Transactional
    public UserResponse updateProfile(User current, UpdateProfileRequest request) {
        User user = requireUser(current.getId());
        user.setFullName(request.fullName().trim());
        user.setPhone(blankToNull(request.phone()));

        if (user.getRole() == UserRole.LANDLORD) {
            user.setBusinessName(blankToNull(request.businessName()));
        }
        if (user.getRole() != UserRole.ADMIN && request.universityId() != null) {
            user.setUniversity(requireActiveUniversity(request.universityId()));
        }
        userRepository.save(user);
        return DtoMapper.user(user);
    }

    @Transactional
    public UserResponse updateAvatar(User current, MultipartFile file) {
        User user = requireUser(current.getId());
        FileStorageService.StoredFile stored = fileStorageService.storeAvatar(file);
        String previous = user.getAvatarFile();
        user.setAvatarFile(stored.fileName());
        userRepository.save(user);
        if (previous != null) {
            fileStorageService.deleteAvatar(previous);
        }
        return DtoMapper.user(user);
    }

    // ------------------------------------------------------------------
    // Password recovery (email OTP)
    // ------------------------------------------------------------------

    @Transactional
    public OtpRequestResponse requestPasswordReset(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(email);

        if (maybeUser.isPresent() && maybeUser.get().getPasswordHash() != null) {
            OtpService.IssuedOtp issued = otpService.issue(email, OtpPurpose.PASSWORD_RESET);
            emailService.sendOtp(email, "password reset", issued.code(), OtpService.CODE_LIFETIME);
            return new OtpRequestResponse(
                    "If that email belongs to a UniLodge account, a reset code has been sent.",
                    expose(issued.code()));
        }
        // Do not reveal whether an account exists.
        return new OtpRequestResponse(
                "If that email belongs to a UniLodge account, a reset code has been sent.", null);
    }

    @Transactional
    public OtpVerifyResponse verifyPasswordResetOtp(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        otpService.verify(email, OtpPurpose.PASSWORD_RESET, request.code());
        String resetToken = tokenService.issuePurposeToken(email, TokenService.SCOPE_PASSWORD_RESET,
                Duration.ofMinutes(15));
        return new OtpVerifyResponse(resetToken, expose(request.code()));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = tokenService.parsePurposeToken(request.resetToken(), TokenService.SCOPE_PASSWORD_RESET);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadRequestException("This reset link is no longer valid."));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(User current, ChangePasswordRequest request) {
        User user = requireUser(current.getId());
        if (user.getPasswordHash() == null) {
            throw new BadRequestException("This account has no password yet. Complete the setup flow first.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // ------------------------------------------------------------------
    // Administrator setup and activation
    // ------------------------------------------------------------------

    /** Sends a setup code to an original administrator who has no password yet. */
    @Transactional
    public OtpRequestResponse requestSetupCode(SetupOtpRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(candidate -> candidate.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new BadRequestException(
                        "No administrator account is waiting for setup with that email."));

        if (user.getPasswordHash() != null) {
            throw new BadRequestException("That administrator account is already set up. Please sign in.");
        }
        if (!user.isOriginalAdmin()
                && !invitationRepository.existsByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING)) {
            throw new ForbiddenException("This administrator invitation is no longer active.");
        }

        OtpService.IssuedOtp issued = otpService.issue(email, OtpPurpose.ADMIN_SETUP);
        emailService.sendOtp(email, "administrator setup", issued.code(), OtpService.CODE_LIFETIME);
        return new OtpRequestResponse("A setup code has been sent to " + email + ".", expose(issued.code()));
    }

    /** Completes setup for an original administrator (no invitation token). */
    @Transactional
    public AuthResponse completeSetup(SetupCompleteRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(candidate -> candidate.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new BadRequestException("No administrator account needs setup with that email."));

        if (user.getPasswordHash() != null) {
            throw new BadRequestException("That administrator account is already set up. Please sign in.");
        }

        AdminInvitation invitation = null;
        if (request.inviteToken() != null && !request.inviteToken().isBlank()) {
            invitation = validateInvitation(request.inviteToken());
            if (!invitation.getEmail().equalsIgnoreCase(email)) {
                throw new BadRequestException("This invitation belongs to a different email address.");
            }
        } else if (!user.isOriginalAdmin()) {
            throw new ForbiddenException("An invitation link is required to finish this setup.");
        }

        otpService.verify(email, OtpPurpose.ADMIN_SETUP, request.code());
        activateAdmin(user, request.password(), invitation);
        return new AuthResponse(tokenService.issueAccessToken(user), DtoMapper.user(user));
    }

    @Transactional(readOnly = true)
    public InvitePreviewResponse previewInvitation(String token) {
        AdminInvitation invitation = invitationRepository.findByToken(token).orElse(null);
        if (invitation == null) {
            return new InvitePreviewResponse(null, null, null, false,
                    "This invitation link is not valid. Ask an administrator for a new one.");
        }
        boolean expired = invitation.getStatus() == InvitationStatus.PENDING
                && invitation.getExpiresAt().isBefore(Instant.now());
        boolean valid = invitation.getStatus() == InvitationStatus.PENDING && !expired;
        String message = valid
                ? "You have been invited to administer UniLodge."
                : switch (invitation.getStatus()) {
                    case ACCEPTED -> "This invitation has already been used.";
                    case REVOKED -> "This invitation has been revoked.";
                    case EXPIRED -> "This invitation has expired.";
                    default -> expired ? "This invitation has expired." : "This invitation is no longer valid.";
                };
        String fullName = userRepository.findByEmailIgnoreCase(invitation.getEmail())
                .map(User::getFullName)
                .orElse(invitation.getEmail());
        return new InvitePreviewResponse(invitation.getEmail(), fullName, invitation.getStatus(),
                valid, message);
    }

    @Transactional
    public OtpRequestResponse sendInvitationCode(String token) {
        AdminInvitation invitation = validateInvitation(token);
        OtpService.IssuedOtp issued = otpService.issue(invitation.getEmail(), OtpPurpose.ADMIN_SETUP);
        emailService.sendOtp(invitation.getEmail(), "administrator setup", issued.code(),
                OtpService.CODE_LIFETIME);
        return new OtpRequestResponse("A verification code has been sent to " + invitation.getEmail() + ".",
                expose(issued.code()));
    }

    @Transactional
    public AuthResponse acceptInvitation(String token, AdminInviteAcceptRequest request) {
        AdminInvitation invitation = validateInvitation(token);
        User user = userRepository.findByEmailIgnoreCase(invitation.getEmail())
                .filter(candidate -> candidate.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new BadRequestException("This invitation cannot be completed."));

        otpService.verify(invitation.getEmail(), OtpPurpose.ADMIN_SETUP, request.code());
        activateAdmin(user, request.password(), invitation);
        return new AuthResponse(tokenService.issueAccessToken(user), DtoMapper.user(user));
    }

    /** Sends a verification code to a promoted administrator. */
    @Transactional
    public OtpRequestResponse requestAdminVerification(User current) {
        User user = requireUser(current.getId());
        if (user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only administrators can verify administrator access.");
        }
        if (user.isAdminVerified()) {
            throw new BadRequestException("Your administrator access is already verified.");
        }
        OtpService.IssuedOtp issued = otpService.issue(user.getEmail(), OtpPurpose.ADMIN_VERIFICATION);
        emailService.sendOtp(user.getEmail(), "administrator verification", issued.code(),
                OtpService.CODE_LIFETIME);
        return new OtpRequestResponse("A verification code has been sent to " + user.getEmail() + ".",
                expose(issued.code()));
    }

    @Transactional
    public AuthResponse confirmAdminVerification(User current, AdminVerifyConfirmRequest request) {
        User user = requireUser(current.getId());
        if (user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only administrators can verify administrator access.");
        }
        otpService.verify(user.getEmail(), OtpPurpose.ADMIN_VERIFICATION, request.code());
        user.setEmailVerified(true);
        user.setAdminVerifiedAt(Instant.now());
        userRepository.save(user);
        auditService.record(user, AuditAction.ADMIN_VERIFIED, "user", user.getId(), user.getEmail(),
                "Administrator verified their email address");
        return new AuthResponse(tokenService.issueAccessToken(user), DtoMapper.user(user));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void activateAdmin(User user, String rawPassword, AdminInvitation invitation) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setEmailVerified(true);
        user.setAdminVerifiedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        if (invitation != null) {
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setAcceptedAt(Instant.now());
            invitationRepository.save(invitation);
        }

        auditService.record(user, AuditAction.ADMIN_VERIFIED, "user", user.getId(), user.getEmail(),
                invitation == null
                        ? "Original administrator completed initial setup"
                        : "Accepted administrator invitation from " + invitation.getInvitedBy().getFullName());
    }

    private AdminInvitation validateInvitation(String token) {
        AdminInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("This invitation link is not valid."));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("This invitation is no longer active.");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException("This invitation has expired. Ask an administrator for a new one.");
        }
        return invitation;
    }

    private University requireActiveUniversity(UUID id) {
        University university = universityRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("That university could not be found."));
        if (!university.isActive()) {
            throw new BadRequestException("That university is not currently accepting listings.");
        }
        return university;
    }

    private String expose(String code) {
        return properties.getMail().isExposeOtp() ? code : null;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
