/* Sign-in, registration and password recovery flows. */
(function () {
    "use strict";
    const U = window.UniLodge;

    const form = document.getElementById("login-form");
    const status = document.getElementById("auth-status");
    const devBox = document.getElementById("dev-code");
    const submit = document.getElementById("auth-submit");
    const kicker = document.getElementById("auth-kicker");
    const title = document.getElementById("auth-title");
    const description = document.getElementById("auth-description");
    const steps = document.getElementById("auth-steps");
    const stepLabel = document.getElementById("auth-step-label");
    const fullNameField = document.getElementById("full-name-field");
    const roleField = document.getElementById("role-field");
    const phoneField = document.getElementById("phone-field");
    const universityField = document.getElementById("university-field");
    const businessField = document.getElementById("business-field");
    const confirmField = document.getElementById("confirm-field");
    const otpField = document.getElementById("otp-field");
    const otpActions = document.getElementById("otp-actions");
    const loginOptions = document.getElementById("login-options");
    const adminDivider = document.getElementById("admin-divider");
    const adminLink = document.getElementById("admin-link");
    const toggleText = document.getElementById("auth-toggle-text");
    const toggle = document.getElementById("auth-toggle");
    const forgotLink = document.getElementById("forgot-link");
    const resend = document.getElementById("resend-code");
    const passwordLabel = document.getElementById("password-label");
    const passwordInput = document.getElementById("password");
    const emailInput = document.getElementById("email");
    const otpInput = document.getElementById("otp-code");
    const confirmInput = document.getElementById("confirm-password");
    const universitySelect = document.getElementById("university");

    let mode = "login";
    let step = 1;
    let resetToken = null;

    function hideStatus() {
        status.hidden = true;
        status.className = "form-status";
        status.textContent = "";
        devBox.hidden = true;
        devBox.innerHTML = "";
    }

    function showStatus(message, kind) {
        status.hidden = false;
        status.className = "form-status " + (kind === "error" ? "auth-error" : kind === "success" ? "auth-success" : "");
        status.textContent = message;
    }

    function showDev(code, label) {
        if (!code) {
            devBox.hidden = true;
            return;
        }
        devBox.hidden = false;
        devBox.innerHTML = "Development only — " + U.escapeHtml(label || "your code") + " is <strong>" + U.escapeHtml(code) + "</strong>";
    }

    function busy(flag, text) {
        submit.disabled = flag;
        submit.innerHTML = flag ? (text || "Working…") + ' <span aria-hidden="true">…</span>' : submit.dataset.label;
    }

    function setText(element, text) {
        if (element) element.textContent = text;
    }

    function setStepDots(current) {
        step = current;
        steps.hidden = false;
        steps.querySelectorAll("[data-step]").forEach((dot) => {
            dot.classList.toggle("is-active", Number(dot.dataset.step) <= current);
        });
        stepLabel.textContent = "STEP " + current + " OF 3";
    }

    function resetForms() {
        form.querySelectorAll("input, select").forEach((input) => {
            if (input.type === "checkbox" || input.type === "radio") return;
            input.value = "";
        });
        const student = form.querySelector('input[name="role"][value="STUDENT"]');
        if (student) student.checked = true;
        resetToken = null;
        hideStatus();
    }

    function applyRole() {
        const role = (form.querySelector('input[name="role"]:checked') || {}).value || "STUDENT";
        const isLandlord = role === "LANDLORD";
        universityField.hidden = false;
        businessField.hidden = !isLandlord;
        universitySelect.required = !isLandlord;
        if (mode === "register") {
            phoneField.hidden = false;
        }
    }

    function setMode(next) {
        mode = next;
        step = 1;
        steps.hidden = true;
        resetForms();
        fullNameField.hidden = true;
        roleField.hidden = true;
        phoneField.hidden = true;
        universityField.hidden = true;
        businessField.hidden = true;
        confirmField.hidden = true;
        otpField.hidden = true;
        otpActions.hidden = true;
        loginOptions.hidden = false;
        adminDivider.hidden = false;
        adminLink.hidden = false;
        passwordInput.autocomplete = "current-password";
        passwordInput.required = true;
        emailInput.readOnly = false;
        setText(passwordLabel, "Password");
        submit.dataset.label = "Sign in <span aria-hidden=\"true\">→</span>";

        if (next === "login") {
            setText(kicker, "WELCOME BACK");
            setText(title, "Sign in to your account");
            setText(description, "Good to have you back. Let's find where you left off.");
            toggleText.innerHTML = 'New to UniLodge? <button class="auth-mode-toggle" type="button" id="auth-toggle">Create an account</button>';
            bindToggle();
            submit.dataset.label = "Sign in <span aria-hidden=\"true\">→</span>";
            submit.innerHTML = submit.dataset.label;
        } else if (next === "register") {
            setText(kicker, "JOIN UNILODGE");
            setText(title, "Create your account");
            setText(description, "Tell us a little about you so we can set up the right workspace.");
            fullNameField.hidden = false;
            roleField.hidden = false;
            confirmField.hidden = false;
            passwordInput.autocomplete = "new-password";
            setText(passwordLabel, "Password");
            applyRole();
            toggleText.innerHTML = 'Already have an account? <button class="auth-mode-toggle" type="button" id="auth-toggle">Sign in</button>';
            bindToggle();
            submit.dataset.label = "Create account <span aria-hidden=\"true\">→</span>";
            submit.innerHTML = submit.dataset.label;
        } else if (next === "forgot") {
            setText(kicker, "PASSWORD RECOVERY");
            setText(title, "Reset your password");
            setText(description, "We will email you a 6-digit code to confirm it is really you.");
            loginOptions.hidden = true;
            adminDivider.hidden = true;
            adminLink.hidden = true;
            emailInput.required = true;
            setStepDots(1);
            toggleText.innerHTML = 'Remembered it? <button class="auth-mode-toggle" type="button" id="auth-toggle">Back to sign in</button>';
            bindToggle();
            submit.dataset.label = "Send code <span aria-hidden=\"true\">→</span>";
            submit.innerHTML = submit.dataset.label;
        }
    }

    function bindToggle() {
        const button = toggleText.querySelector("#auth-toggle");
        if (!button) return;
        button.addEventListener("click", () => {
            if (mode === "login") setMode("register");
            else setMode("login");
        });
    }

    function redirectAfterAuth(user) {
        const raw = U.query("next");
        if (raw) {
            let candidate = raw;
            try { candidate = decodeURIComponent(raw); } catch (error) { /* keep raw */ }
            if (!/^(?:[a-z]+:)?\/\//i.test(candidate) && !candidate.startsWith("//") && !candidate.startsWith("http")) {
                location.href = candidate;
                return;
            }
        }
        if (user.role === "ADMIN") {
            location.href = user.adminVerified ? "admin.html" : "admin-setup.html?verify=1";
        } else {
            location.href = "dashboard.html";
        }
    }

    async function submitLogin() {
        hideStatus();
        const email = emailInput.value.trim();
        const password = passwordInput.value;
        if (!email || !password) {
            showStatus("Enter your email and password to continue.", "error");
            return;
        }
        busy(true, "Signing in");
        try {
            const result = await U.api("/api/auth/login", { method: "POST", auth: false, body: { email, password } });
            U.setSession(result.token, result.user);
            showStatus("Signed in. Taking you to your workspace…", "success");
            setTimeout(() => redirectAfterAuth(result.user), 350);
        } catch (error) {
            showStatus(U.networkMessage(error), "error");
        } finally {
            busy(false);
        }
    }

    async function submitRegister() {
        hideStatus();
        const fullName = document.getElementById("full-name").value.trim();
        const email = emailInput.value.trim();
        const password = passwordInput.value;
        const confirm = confirmInput.value;
        const role = (form.querySelector('input[name="role"]:checked') || {}).value || "STUDENT";
        const phone = document.getElementById("phone").value.trim();
        const universityId = universitySelect.value || null;
        const businessName = document.getElementById("business-name").value.trim();

        if (fullName.length < 2) { showStatus("Please enter your full name.", "error"); return; }
        if (!email) { showStatus("Please enter your email address.", "error"); return; }
        if (password.length < 8) { showStatus("Passwords must be at least 8 characters long.", "error"); return; }
        if (password !== confirm) { showStatus("The two passwords do not match.", "error"); return; }
        if (role === "STUDENT" && !universityId) { showStatus("Please select your university.", "error"); return; }

        busy(true, "Creating account");
        try {
            const result = await U.api("/api/auth/register", {
                method: "POST",
                auth: false,
                body: { fullName, email, password, phone, role, universityId, businessName }
            });
            U.setSession(result.token, result.user);
            showStatus(role === "LANDLORD"
                ? "Account created. An administrator will review your landlord account shortly."
                : "Welcome to UniLodge! Taking you to your dashboard…", "success");
            setTimeout(() => redirectAfterAuth(result.user), 700);
        } catch (error) {
            showStatus(U.networkMessage(error), "error");
        } finally {
            busy(false);
        }
    }

    async function submitForgot() {
        hideStatus();
        if (step === 1) {
            const email = emailInput.value.trim();
            if (!email) { showStatus("Enter the email address on your account.", "error"); return; }
            busy(true, "Sending code");
            try {
                const result = await U.api("/api/auth/password/forgot", { method: "POST", auth: false, body: { email } });
                showStatus(result.message || "If that email exists, a reset code is on its way.", "success");
                showDev(result.devCode, "reset code");
                setStepDots(2);
                setText(title, "Enter your code");
                setText(description, "Check the inbox for " + email + " and enter the 6-digit code we sent.");
                emailInput.readOnly = true;
                otpField.hidden = false;
                otpActions.hidden = false;
                passwordInput.value = "";
                passwordInput.required = false;
                document.getElementById("password-field").hidden = true;
                submit.dataset.label = "Verify code <span aria-hidden=\"true\">→</span>";
                submit.innerHTML = submit.dataset.label;
                otpInput.focus();
            } catch (error) {
                showStatus(U.networkMessage(error), "error");
            } finally {
                busy(false);
            }
            return;
        }

        if (step === 2) {
            const email = emailInput.value.trim();
            const code = otpInput.value.trim();
            if (!/^\d{6}$/.test(code)) { showStatus("Enter the 6-digit code from your email.", "error"); return; }
            busy(true, "Checking code");
            try {
                const result = await U.api("/api/auth/password/verify", { method: "POST", auth: false, body: { email, code } });
                resetToken = result.resetToken;
                showStatus("Code confirmed. Choose a new password.", "success");
                showDev(result.devCode, "code");
                setStepDots(3);
                setText(title, "Choose a new password");
                setText(description, "Pick something at least 8 characters long that you have not used before.");
                otpField.hidden = true;
                otpActions.hidden = true;
                document.getElementById("password-field").hidden = false;
                passwordInput.required = true;
                passwordInput.autocomplete = "new-password";
                confirmField.hidden = false;
                setText(passwordLabel, "New password");
                submit.dataset.label = "Update password <span aria-hidden=\"true\">→</span>";
                submit.innerHTML = submit.dataset.label;
                passwordInput.focus();
            } catch (error) {
                showStatus(U.networkMessage(error), "error");
            } finally {
                busy(false);
            }
            return;
        }

        const password = passwordInput.value;
        const confirm = confirmInput.value;
        if (password.length < 8) { showStatus("Passwords must be at least 8 characters long.", "error"); return; }
        if (password !== confirm) { showStatus("The two passwords do not match.", "error"); return; }
        busy(true, "Updating password");
        try {
            const result = await U.api("/api/auth/password/reset", {
                method: "POST", auth: false, body: { resetToken, newPassword: password }
            });
            showStatus(result.message || "Your password has been updated. You can sign in now.", "success");
            setTimeout(() => setMode("login"), 1100);
        } catch (error) {
            showStatus(U.networkMessage(error), "error");
        } finally {
            busy(false);
        }
    }

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        if (mode === "login") submitLogin();
        else if (mode === "register") submitRegister();
        else submitForgot();
    });

    form.querySelectorAll('input[name="role"]').forEach((radio) => {
        radio.addEventListener("change", applyRole);
    });

    forgotLink.addEventListener("click", () => setMode("forgot"));

    resend.addEventListener("click", async () => {
        const email = emailInput.value.trim();
        if (!email) { showStatus("Enter your email address first.", "error"); return; }
        resend.disabled = true;
        try {
            const result = await U.api("/api/auth/password/forgot", { method: "POST", auth: false, body: { email } });
            showStatus(result.message || "A new code has been sent.", "success");
            showDev(result.devCode, "reset code");
        } catch (error) {
            showStatus(U.networkMessage(error), "error");
        } finally {
            setTimeout(() => { resend.disabled = false; }, 4000);
        }
    });

    async function loadUniversities() {
        try {
            const list = await U.api("/api/universities", { auth: false });
            universitySelect.innerHTML = '<option value="">Choose a university</option>' +
                list.map((item) => '<option value="' + U.escapeHtml(item.id) + '">' +
                    U.escapeHtml(item.name) + (item.city ? " · " + U.escapeHtml(item.city) : "") + "</option>").join("");
        } catch (error) {
            universitySelect.innerHTML = '<option value="">Palm University College</option>';
        }
    }

    loadUniversities();
    setMode(U.query("mode") === "register" ? "register" : "login");

    if (U.token()) {
        U.refreshMe().then((user) => {
            if (user) {
                showStatus("You are already signed in as " + user.fullName + ".", "success");
            }
        });
    }
})();
