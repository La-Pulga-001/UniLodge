package com.unilodge.web.web;

import com.unilodge.common.dto.*;
import com.unilodge.service.auth.AuthService;
import com.unilodge.web.security.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Registration, sign-in, profile, password recovery and administrator access. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.profile(AuthSupport.require(authentication));
    }

    @PutMapping("/me")
    public UserResponse updateProfile(Authentication authentication,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(AuthSupport.require(authentication), request);
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse updateAvatar(Authentication authentication,
                                     @RequestParam("file") MultipartFile file) {
        return authService.updateAvatar(AuthSupport.require(authentication), file);
    }

    // --- Password recovery -------------------------------------------------

    @PostMapping("/password/forgot")
    public OtpRequestResponse requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password/verify")
    public OtpVerifyResponse verifyPasswordReset(@Valid @RequestBody VerifyOtpRequest request) {
        return authService.verifyPasswordResetOtp(request);
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Your password has been updated. You can sign in now.");
    }

    @PostMapping("/password/change")
    public MessageResponse changePassword(Authentication authentication,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(AuthSupport.require(authentication), request);
        return new MessageResponse("Your password has been changed.");
    }

    // --- Original administrator initial setup ------------------------------

    @PostMapping("/setup/request-code")
    public OtpRequestResponse requestSetupCode(@Valid @RequestBody SetupOtpRequest request) {
        return authService.requestSetupCode(request);
    }

    @PostMapping("/setup/complete")
    public AuthResponse completeSetup(@Valid @RequestBody SetupCompleteRequest request) {
        return authService.completeSetup(request);
    }

    // --- Administrator invitations -----------------------------------------

    @GetMapping("/invitations/{token}")
    public InvitePreviewResponse previewInvitation(@PathVariable String token) {
        return authService.previewInvitation(token);
    }

    @PostMapping("/invitations/{token}/code")
    public OtpRequestResponse sendInvitationCode(@PathVariable String token) {
        return authService.sendInvitationCode(token);
    }

    @PostMapping("/invitations/{token}/accept")
    public AuthResponse acceptInvitation(@PathVariable String token,
                                         @Valid @RequestBody AdminInviteAcceptRequest request) {
        return authService.acceptInvitation(token, request);
    }

    // --- Promoted administrator verification -------------------------------

    @PostMapping("/admin-verification/request")
    public OtpRequestResponse requestAdminVerification(Authentication authentication) {
        return authService.requestAdminVerification(AuthSupport.require(authentication));
    }

    @PostMapping("/admin-verification/confirm")
    public AuthResponse confirmAdminVerification(Authentication authentication,
                                                 @Valid @RequestBody AdminVerifyConfirmRequest request) {
        return authService.confirmAdminVerification(AuthSupport.require(authentication), request);
    }
}
