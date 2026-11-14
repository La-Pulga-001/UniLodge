/* Administrator setup, invitation acceptance and administrator verification. */
(function () {
    "use strict";
    const U = window.UniLodge;
    const inviteToken = U.query("invite");
    const verifyMode = U.query("verify");
    const setupForm = document.getElementById("setup-form");
    const inviteForm = document.getElementById("invite-form");
    const verifyForm = document.getElementById("verify-form");
    const setupStatus = document.getElementById("setup-status");
    const setupDev = document.getElementById("setup-dev");
    const inviteStatus = document.getElementById("invite-status");
    const inviteDev = document.getElementById("invite-dev");
    const verifyStatus = document.getElementById("verify-status");
    const verifyDev = document.getElementById("verify-dev");
    const setupTitle = document.getElementById("setup-title");
    const setupKicker = document.getElementById("setup-kicker");
    const setupDescription = document.getElementById("setup-description");
    const setupNote = document.getElementById("setup-note");
    const setupEmail = document.getElementById("setup-email");
    const setupCode = document.getElementById("setup-code");
    const setupPassword = document.getElementById("setup-password");
    const setupConfirm = document.getElementById("setup-confirm");
    const setupSubmit = document.getElementById("setup-submit");
    const setupCodeField = document.getElementById("setup-code-field");
    const setupPasswordField = document.getElementById("setup-password-field");
    const setupConfirmField = document.getElementById("setup-confirm-field");
    const setupResendRow = document.getElementById("setup-resend-row");
    const invitePreview = document.getElementById("invite-preview");
    const inviteSubmit = document.getElementById("invite-submit");

    let setupStep = 1;

    function status(element, message, kind) {
        if (!element) return;
        if (!message) {
            element.hidden = true;
            element.textContent = "";
            element.className = "form-status";
            return;
        }
        element.hidden = false;
        element.className = "form-status " + (kind === "error" ? "auth-error" : kind === "success" ? "auth-success" : "");
        element.textContent = message;
    }

    function dev(element, code, label) {
        if (!element) return;
        if (!code) { element.hidden = true; element.innerHTML = ""; return; }
        element.hidden = false;
        element.innerHTML = "Development only — " + U.escapeHtml(label) + " is <strong>" + U.escapeHtml(code) + "</strong>";
    }

    function complete(result) {
        U.setSession(result.token, result.user);
        location.href = "admin.html";
    }

    /* ---------------------------------------------------------------- setup */

    function setSetupStep(next) {
        setupStep = next;
        document.getElementById("setup-steps").querySelectorAll("[data-step]").forEach((dot) => {
            dot.classList.toggle("is-active", Number(dot.dataset.step) <= next);
        });
        document.getElementById("setup-step-label").textContent = "STEP " + next + " OF 2";
        setupCodeField.hidden = next < 2;
        setupPasswordField.hidden = next < 2;
        setupConfirmField.hidden = next < 2;
        setupResendRow.hidden = next < 2;
        setupEmail.readOnly = next >= 2;
        setupSubmit.dataset.label = next === 1 ? 'Send setup code <span aria-hidden="true">→</span>' : 'Finish setup <span aria-hidden="true">→</span>';
        setupSubmit.innerHTML = setupSubmit.dataset.label;
        if (next === 2) setupCode.focus();
    }

    async function requestSetupCode(button) {
        const email = setupEmail.value.trim();
        if (!email) { status(setupStatus, "Enter your administrator email address.", "error"); return; }
        if (button) button.disabled = true;
        status(setupStatus, "", null);
        try {
            const result = await U.api("/api/auth/setup/request-code", {
                method: "POST", auth: false, body: { email }
            });
            status(setupStatus, result.message || "A setup code has been sent.", "success");
            dev(setupDev, result.devCode, "setup code");
            setSetupStep(2);
            setupPassword.focus();
        } catch (error) {
            status(setupStatus, U.networkMessage(error), "error");
        } finally {
            if (button) setTimeout(() => { button.disabled = false; }, 3000);
        }
    }

    setupForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (setupStep === 1) { requestSetupCode(); return; }
        const email = setupEmail.value.trim();
        const code = setupCode.value.trim();
        const password = setupPassword.value;
        const confirm = setupConfirm.value;
        if (!/^\d{6}$/.test(code)) { status(setupStatus, "Enter the 6-digit code from your email.", "error"); return; }
        if (password.length < 8) { status(setupStatus, "Passwords must be at least 8 characters long.", "error"); return; }
        if (password !== confirm) { status(setupStatus, "The two passwords do not match.", "error"); return; }
        setupSubmit.disabled = true;
        setupSubmit.innerHTML = 'Finishing setup <span aria-hidden="true">…</span>';
        try {
            const result = await U.api("/api/auth/setup/complete", {
                method: "POST", auth: false, body: { email, code, password }
            });
            status(setupStatus, "Setup complete. Opening your workspace…", "success");
            complete(result);
        } catch (error) {
            status(setupStatus, U.networkMessage(error), "error");
            setupSubmit.disabled = false;
            setupSubmit.innerHTML = setupSubmit.dataset.label;
        }
    });

    document.getElementById("setup-resend").addEventListener("click", () => requestSetupCode(document.getElementById("setup-resend")));

    /* ------------------------------------------------------------ invitation */

    async function loadInvitation() {
        try {
            const preview = await U.api("/api/auth/invitations/" + encodeURIComponent(inviteToken), { auth: false });
            invitePreview.textContent = preview.valid
                ? "Welcome, " + preview.fullName + ". This invitation is for " + preview.email + ". Request a code to continue."
                : preview.message;
            invitePreview.className = "form-status " + (preview.valid ? "" : "auth-error");
            if (!preview.valid) {
                inviteForm.querySelectorAll("input, button[type=submit]").forEach((control) => { control.disabled = true; });
            }
        } catch (error) {
            invitePreview.textContent = U.networkMessage(error);
            invitePreview.className = "form-status auth-error";
            inviteForm.querySelectorAll("input, button[type=submit]").forEach((control) => { control.disabled = true; });
        }
    }

    document.getElementById("invite-send").addEventListener("click", async function () {
        this.disabled = true;
        status(inviteStatus, "", null);
        try {
            const result = await U.api("/api/auth/invitations/" + encodeURIComponent(inviteToken) + "/code", { method: "POST", auth: false });
            status(inviteStatus, result.message || "A verification code is on its way.", "success");
            dev(inviteDev, result.devCode, "verification code");
            document.getElementById("invite-code").focus();
        } catch (error) {
            status(inviteStatus, U.networkMessage(error), "error");
        } finally {
            setTimeout(() => { this.disabled = false; }, 3000);
        }
    });

    inviteForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const code = document.getElementById("invite-code").value.trim();
        const password = document.getElementById("invite-password").value;
        const confirm = document.getElementById("invite-confirm").value;
        if (!/^\d{6}$/.test(code)) { status(inviteStatus, "Enter the 6-digit verification code.", "error"); return; }
        if (password.length < 8) { status(inviteStatus, "Passwords must be at least 8 characters long.", "error"); return; }
        if (password !== confirm) { status(inviteStatus, "The two passwords do not match.", "error"); return; }
        inviteSubmit.disabled = true;
        inviteSubmit.innerHTML = 'Accepting invitation <span aria-hidden="true">…</span>';
        try {
            const result = await U.api("/api/auth/invitations/" + encodeURIComponent(inviteToken) + "/accept", {
                method: "POST", auth: false, body: { code, password }
            });
            status(inviteStatus, "Invitation accepted. Opening the admin workspace…", "success");
            complete(result);
        } catch (error) {
            status(inviteStatus, U.networkMessage(error), "error");
            inviteSubmit.disabled = false;
            inviteSubmit.innerHTML = 'Accept invitation <span aria-hidden="true">→</span>';
        }
    });

    /* --------------------------------------------------------- verification */

    document.getElementById("verify-send").addEventListener("click", async function () {
        this.disabled = true;
        status(verifyStatus, "", null);
        try {
            const result = await U.api("/api/auth/admin-verification/request", { method: "POST" });
            status(verifyStatus, result.message || "A verification code has been sent.", "success");
            dev(verifyDev, result.devCode, "verification code");
            document.getElementById("verify-code").focus();
        } catch (error) {
            status(verifyStatus, U.networkMessage(error), "error");
        } finally {
            setTimeout(() => { this.disabled = false; }, 3000);
        }
    });

    verifyForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const code = document.getElementById("verify-code").value.trim();
        if (!/^\d{6}$/.test(code)) { status(verifyStatus, "Enter the 6-digit verification code.", "error"); return; }
        const button = document.getElementById("verify-submit");
        button.disabled = true;
        button.innerHTML = 'Verifying <span aria-hidden="true">…</span>';
        try {
            const result = await U.api("/api/auth/admin-verification/confirm", { method: "POST", body: { code } });
            complete(result);
        } catch (error) {
            status(verifyStatus, U.networkMessage(error), "error");
            button.disabled = false;
            button.innerHTML = 'Verify and continue <span aria-hidden="true">→</span>';
        }
    });

    /* ------------------------------------------------------------ boot-up */

    if (inviteToken) {
        setupForm.hidden = true;
        verifyForm.hidden = true;
        inviteForm.hidden = false;
        setupKicker.textContent = "INVITED ADMINISTRATOR";
        setupTitle.textContent = "Accept your invitation";
        setupDescription.textContent = "You were invited to join the UniLodge administration team.";
        setupNote.innerHTML = "<strong>Heads up:</strong> accepting this invitation activates an administrator account. Ask the admin who invited you if anything looks wrong.";
        loadInvitation();
    } else if (verifyMode) {
        setupForm.hidden = true;
        inviteForm.hidden = true;
        verifyForm.hidden = false;
        setupKicker.textContent = "ONE MORE STEP";
        setupTitle.textContent = "Verify your administrator access";
        setupDescription.textContent = "Confirm your email to unlock moderation and governance tools.";
        setupNote.innerHTML = "<strong>Almost there:</strong> request a code, enter it below, and your administrator workspace will open.";
        if (!U.token()) {
            location.href = "login.html?next=" + encodeURIComponent("admin-setup.html?verify=1");
        }
    } else {
        inviteForm.hidden = true;
        verifyForm.hidden = true;
        setupForm.hidden = false;
        setSetupStep(1);
    }
})();
