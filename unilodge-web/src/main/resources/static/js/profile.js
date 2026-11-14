/* Profile: account details, avatar upload and password change. */
(function () {
    "use strict";
    const U = window.UniLodge;

    const avatarBox = document.getElementById("profile-avatar");
    const nameEl = document.getElementById("profile-name");
    const emailEl = document.getElementById("profile-email");
    const badges = document.getElementById("profile-badges");
    const meta = document.getElementById("profile-meta");
    const alertBox = document.getElementById("profile-alert");
    const actions = document.getElementById("profile-actions");
    const avatarInput = document.getElementById("avatar-input");
    const avatarLabel = document.getElementById("avatar-label");
    const profileForm = document.getElementById("profile-form");
    const profileError = document.getElementById("profile-error");
    const passwordForm = document.getElementById("password-form");
    const passwordError = document.getElementById("password-error");
    const universitySelect = document.getElementById("profile-university");
    const universityField = document.getElementById("profile-university-field");
    const businessField = document.getElementById("profile-business-field");

    let me = null;
    let universities = [];

    function renderAlerts() {
        let html = "";
        if (me.role === "ADMIN" && !me.adminVerified) {
            html += '<div class="alert alert-warn" style="margin-bottom:16px"><span><strong>Your administrator access is not verified.</strong>' +
                'Confirm your email with a one-time code before opening the admin workspace. ' +
                '<a href="admin-setup.html?verify=1">Verify admin access →</a></span></div>';
        }
        if (me.role === "LANDLORD" && me.status !== "ACTIVE") {
            html += '<div class="alert alert-warn" style="margin-bottom:16px"><span><strong>Your landlord account is ' +
                U.escapeHtml(U.label(me.status).toLowerCase()) + '.</strong>' +
                (me.adminNote ? U.escapeHtml(me.adminNote) : "An administrator will review your application before your listings can go live.") +
                '</span></div>';
        }
        if (me.role !== "ADMIN" && me.status === "SUSPENDED") {
            html += '<div class="alert alert-danger" style="margin-bottom:16px"><span><strong>This account is suspended.</strong>' +
                (me.adminNote ? U.escapeHtml(me.adminNote) : "Contact the UniLodge team if you think this is a mistake.") + '</span></div>';
        }
        alertBox.innerHTML = html;
    }

    function renderAvatar() {
        const inner = me.avatarUrl
            ? '<img src="' + U.escapeHtml(me.avatarUrl) + '" alt="' + U.escapeHtml(me.fullName) + '">'
            : U.escapeHtml(U.initials(me.fullName));
        avatarBox.innerHTML = inner;
    }

    function renderProfile() {
        renderAvatar();
        nameEl.textContent = me.fullName || "UniLodge account";
        emailEl.textContent = me.email;

        badges.innerHTML =
            '<span class="badge badge-' + (me.role === "ADMIN" ? "admin" : me.role === "LANDLORD" ? "landlord" : "student") + '">' +
            U.escapeHtml(U.label(me.role)) + '</span>' +
            U.statusBadge(me.status) +
            (me.emailVerified
                ? '<span class="badge badge-approved">Email verified</span>'
                : '<span class="badge badge-muted">Email not verified</span>');

        let metaHtml = "";
        metaHtml += '<div><span>Account type</span><strong>' + U.escapeHtml(U.label(me.role)) + '</strong></div>';
        metaHtml += '<div><span>Status</span><strong>' + U.escapeHtml(U.label(me.status)) + '</strong></div>';
        metaHtml += '<div><span>Email</span><strong>' + (me.emailVerified ? "Verified" : "Not verified") + '</strong></div>';
        if (me.university && me.university.name) {
            metaHtml += '<div><span>University</span><strong>' + U.escapeHtml(me.university.name) + '</strong></div>';
        }
        if (me.role === "LANDLORD" && me.businessName) {
            metaHtml += '<div><span>Business</span><strong>' + U.escapeHtml(me.businessName) + '</strong></div>';
        }
        if (me.role === "ADMIN") {
            metaHtml += '<div><span>Admin access</span><strong>' + (me.adminVerified ? "Verified" : "Setup pending") + '</strong></div>';
            metaHtml += '<div><span>Appointed by</span><strong>' + (me.originalAdmin ? "Original administrator" : "Team appointment") + '</strong></div>';
        }
        metaHtml += '<div><span>Member since</span><strong>' + U.formatDate(me.createdAt) + '</strong></div>';
        meta.innerHTML = metaHtml;

        const dashboard = me.role === "ADMIN" ? "admin.html" : "dashboard.html";
        actions.innerHTML = '<a class="btn btn-outline" href="' + dashboard + '">' +
            (me.role === "ADMIN" ? "Admin workspace" : "My dashboard") + '</a>' +
            '<a class="btn btn-ghost" href="browse.html">Browse homes</a>';

        renderAlerts();
    }

    function populateForm() {
        document.getElementById("profile-full-name").value = me.fullName || "";
        document.getElementById("profile-phone").value = me.phone || "";
        document.getElementById("profile-business").value = me.businessName || "";
        universityField.hidden = me.role === "ADMIN";
        businessField.hidden = me.role !== "LANDLORD";
        if (me.university && me.university.id) {
            const exists = universities.some((item) => item.id === me.university.id);
            if (!exists) {
                const option = document.createElement("option");
                option.value = me.university.id;
                option.textContent = me.university.name || "Your university";
                universitySelect.appendChild(option);
            }
            universitySelect.value = me.university.id;
        }
    }

    avatarInput.addEventListener("change", async () => {
        const file = avatarInput.files && avatarInput.files[0];
        avatarInput.value = "";
        if (!file) return;
        if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) {
            U.toast("Choose a JPEG, PNG or WebP image.", "warn");
            return;
        }
        if (file.size > 2 * 1024 * 1024) {
            U.toast("Your photo must be 2 MB or smaller.", "warn");
            return;
        }
        avatarLabel.textContent = "Uploading…";
        avatarLabel.style.pointerEvents = "none";
        try {
            const formData = new FormData();
            formData.append("file", file);
            const updated = await U.api("/api/auth/me/avatar", { method: "POST", form: formData });
            me = updated;
            U.setUser(me);
            U.renderNav();
            renderProfile();
            U.toast("Profile photo updated.");
        } catch (error) {
            U.toast(U.networkMessage(error), "error");
        } finally {
            avatarLabel.textContent = "Change photo";
            avatarLabel.style.pointerEvents = "";
        }
    });

    profileForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        profileError.hidden = true;
        const fullName = document.getElementById("profile-full-name").value.trim();
        if (fullName.length < 2) {
            profileError.hidden = false;
            profileError.textContent = "Enter your full name (at least 2 characters).";
            return;
        }
        const body = {
            fullName,
            phone: document.getElementById("profile-phone").value.trim() || null,
            universityId: me.role === "ADMIN" ? null : universitySelect.value || null,
            businessName: me.role === "LANDLORD" ? document.getElementById("profile-business").value.trim() || null : null
        };
        const button = document.getElementById("profile-save");
        button.disabled = true;
        button.textContent = "Saving…";
        try {
            me = await U.api("/api/auth/me", { method: "PUT", body });
            U.setUser(me);
            U.renderNav();
            renderProfile();
            populateForm();
            U.toast("Your details have been saved.");
        } catch (error) {
            profileError.hidden = false;
            profileError.textContent = U.networkMessage(error);
        } finally {
            button.disabled = false;
            button.textContent = "Save details";
        }
    });

    passwordForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        passwordError.hidden = true;
        const current = document.getElementById("current-password").value;
        const next = document.getElementById("new-password").value;
        const confirm = document.getElementById("confirm-password").value;
        if (!current) {
            passwordError.hidden = false;
            passwordError.textContent = "Enter your current password.";
            return;
        }
        if (next.length < 8) {
            passwordError.hidden = false;
            passwordError.textContent = "Your new password must be at least 8 characters.";
            return;
        }
        if (next !== confirm) {
            passwordError.hidden = false;
            passwordError.textContent = "The new passwords do not match.";
            return;
        }
        const button = document.getElementById("password-save");
        button.disabled = true;
        button.textContent = "Changing…";
        try {
            await U.api("/api/auth/password/change", { method: "POST", body: { currentPassword: current, newPassword: next } });
            passwordForm.reset();
            U.toast("Your password has been changed.");
        } catch (error) {
            passwordError.hidden = false;
            passwordError.textContent = U.networkMessage(error);
        } finally {
            button.disabled = false;
            button.textContent = "Change password";
        }
    });

    document.getElementById("profile-logout").addEventListener("click", () => U.logout());

    async function init() {
        me = await U.requireUser();
        if (!me) return;
        try {
            universities = await U.api("/api/universities", { auth: false });
        } catch (error) {
            universities = [];
            universityField.hidden = true;
        }
        universitySelect.innerHTML = universities.map((university) =>
            '<option value="' + U.escapeHtml(university.id) + '">' + U.escapeHtml(university.name) + '</option>').join("");
        renderProfile();
        populateForm();
    }

    init();
})();
