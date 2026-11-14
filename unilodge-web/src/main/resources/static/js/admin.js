/* Administrator workspace: moderation, accounts, governance, audit and institutions. */
(function () {
    "use strict";
    const U = window.UniLodge;

    const viewEl = document.getElementById("admin-view");
    const alertEl = document.getElementById("admin-alert");
    const nav = document.getElementById("admin-nav");
    const titleEl = document.getElementById("page-title");
    const eyebrowEl = document.getElementById("page-eyebrow");
    const descEl = document.getElementById("page-description");
    const actionsEl = document.getElementById("page-actions");
    const topbarLabel = document.getElementById("topbar-label");
    const identity = document.getElementById("admin-identity");

    const META = {
        overview: {
            eyebrow: "ADMINISTRATION",
            title: "Platform overview",
            description: "Moderate the platform, care for accounts and keep UniLodge trustworthy.",
            actions: '<button type="button" class="btn btn-sm btn-outline" data-action="refresh">Refresh</button>'
        },
        properties: {
            eyebrow: "MODERATION",
            title: "Listings",
            description: "Review new homes, reject unsafe listings and suspend anything that breaks the rules.",
            actions: '<button type="button" class="btn btn-sm btn-outline" data-action="refresh">Refresh</button>'
        },
        posts: {
            eyebrow: "MODERATION",
            title: "Student posts",
            description: "Keep the student board useful and respectful. Hidden posts stay out of public view.",
            actions: '<button type="button" class="btn btn-sm btn-outline" data-action="refresh">Refresh</button>'
        },
        users: {
            eyebrow: "ACCOUNTS",
            title: "User accounts",
            description: "Approve landlords, suspend accounts that break the rules and appoint administrators.",
            actions: '<button type="button" class="btn btn-sm btn-outline" data-action="refresh">Refresh</button>'
        },
        admins: {
            eyebrow: "GOVERNANCE",
            title: "Administrators",
            description: "Original administrators appoint, promote and remove administrators. Every change is audited.",
            actions: '<button type="button" class="btn btn-sm btn-outline" data-action="refresh">Refresh</button>'
        },
        audit: {
            eyebrow: "ACCOUNTABILITY",
            title: "Audit trail",
            description: "A chronological record of moderation and governance decisions across UniLodge.",
            actions: '<button type="button" class="btn btn-sm btn-outline" data-action="refresh">Refresh</button>'
        },
        universities: {
            eyebrow: "INSTITUTIONS",
            title: "Institutions",
            description: "Palm University College is the home campus. Add more Ghanaian universities without rebuilding.",
            actions: '<button type="button" class="btn btn-sm btn-primary" data-action="add-university">+ Add institution</button>'
        }
    };

    let me = null;
    let current = "overview";
    let stats = null;
    let universitiesCache = [];
    // Guards against a slower, previously selected view overwriting the latest one.
    let renderEpoch = 0;

    const propertyState = { status: "", q: "", page: 0 };
    const postState = { status: "", q: "", page: 0 };
    const userState = { role: "", status: "", q: "", page: 0 };
    const auditState = { page: 0 };

    function adminLoading(text) {
        return '<div class="admin-loading"><span class="admin-spinner"></span> ' + U.escapeHtml(text || "Loading…") + '</div>';
    }

    function setAlert(html, kind) {
        alertEl.innerHTML = html
            ? '<div class="alert alert-' + (kind || "warn") + '" style="margin-bottom:15px"><span>' + html + '</span></div>'
            : "";
    }

    function adminBadge(status) {
        return '<span class="admin-badge is-' + U.statusClass(status) + '">' + U.escapeHtml(U.label(status)) + '</span>';
    }

    function roleBadge(role) {
        return '<span class="admin-badge role-' + U.statusClass(role) + '">' + U.escapeHtml(U.label(role)) + '</span>';
    }

    function viewError(error) {
        viewEl.innerHTML = '<div class="admin-card" style="margin-top:0"><div class="admin-empty-state">' +
            '<strong>We could not load this view.</strong><br>' + U.escapeHtml(U.networkMessage(error)) +
            '<br><br><button type="button" class="btn btn-sm btn-outline" id="view-retry">Try again</button></div></div>';
        const retry = document.getElementById("view-retry");
        if (retry) retry.addEventListener("click", () => renderView(current));
    }

    function pageControls(page, totalPages, totalElements) {
        if (!totalPages || totalPages <= 1) {
            return '<p class="help" style="margin:10px 0 4px">' + (totalElements || 0) + ' item' + ((totalElements || 0) === 1 ? "" : "s") + '</p>';
        }
        return '<div class="pagination" style="justify-content:flex-start">' +
            '<button type="button" data-admin-page="' + (page - 1) + '"' + (page <= 0 ? " disabled" : "") + '>‹</button>' +
            '<span class="small muted" style="padding:0 6px">Page ' + (page + 1) + ' of ' + totalPages + '</span>' +
            '<button type="button" data-admin-page="' + (page + 1) + '"' + (page >= totalPages - 1 ? " disabled" : "") + '>›</button>' +
            '</div>';
    }

    function bindPager(scope, onChange) {
        scope.querySelectorAll("[data-admin-page]").forEach((button) => {
            button.addEventListener("click", () => onChange(Number(button.dataset.adminPage)));
        });
    }

    function bindGoto(scope) {
        scope.querySelectorAll("[data-goto-view]").forEach((button) => {
            button.addEventListener("click", () => showView(button.dataset.gotoView));
        });
    }

    async function loadStats() {
        try {
            stats = await U.api("/api/admin/stats");
            updateNavCounts();
        } catch (error) { /* the view still works without counters */ }
    }

    function updateNavCounts() {
        const listings = document.getElementById("nav-properties-count");
        const users = document.getElementById("nav-users-count");
        if (stats) {
            if (stats.pendingProperties > 0) {
                listings.hidden = false;
                listings.textContent = String(stats.pendingProperties);
            } else {
                listings.hidden = true;
            }
            if (stats.pendingLandlords > 0) {
                users.hidden = false;
                users.textContent = String(stats.pendingLandlords);
            } else {
                users.hidden = true;
            }
        }
    }

    async function refreshAfterAction() {
        await loadStats();
        await renderView(current);
    }

    async function runAction(button, task, message) {
        button.disabled = true;
        try {
            await task();
            U.toast(message);
            await refreshAfterAction();
        } catch (error) {
            U.toast(U.networkMessage(error), "error");
            button.disabled = false;
        }
    }

    async function notePrompt(title, message, confirmText, noteLabel) {
        const answer = await U.confirmDialog({
            title,
            message,
            confirmText,
            danger: true,
            noteField: true,
            noteLabel: noteLabel || "Note (optional)"
        });
        if (!answer) return null;
        return answer.note || null;
    }

    // --------------------------------------------------------------- actions
    function bindPropertyActions(scope) {
        scope.querySelectorAll("[data-approve-property]").forEach((button) => {
            button.addEventListener("click", () => runAction(button, () => U.api(
                "/api/admin/properties/" + encodeURIComponent(button.dataset.approveProperty) + "/approve",
                { method: "POST" }), "Listing approved and published."));
        });
        scope.querySelectorAll("[data-reject-property]").forEach((button) => {
            button.addEventListener("click", async () => {
                const note = await notePrompt("Reject this listing?", "The landlord sees your note and can fix the listing before resubmitting.", "Reject listing", "Reason for rejection (optional)");
                if (note === null) return;
                runAction(button, () => U.api("/api/admin/properties/" + encodeURIComponent(button.dataset.rejectProperty) + "/reject",
                    { method: "POST", body: { note } }), "Listing rejected.");
            });
        });
        scope.querySelectorAll("[data-suspend-property]").forEach((button) => {
            button.addEventListener("click", async () => {
                const note = await notePrompt("Suspend this listing?", "It disappears from the public catalogue until you restore it.", "Suspend listing", "Reason for suspension (optional)");
                if (note === null) return;
                runAction(button, () => U.api("/api/admin/properties/" + encodeURIComponent(button.dataset.suspendProperty) + "/suspend",
                    { method: "POST", body: { note } }), "Listing suspended.");
            });
        });
        scope.querySelectorAll("[data-restore-property]").forEach((button) => {
            button.addEventListener("click", () => runAction(button, () => U.api(
                "/api/admin/properties/" + encodeURIComponent(button.dataset.restoreProperty) + "/restore",
                { method: "POST" }), "Listing restored and published."));
        });
    }

    function bindUserActions(scope) {
        scope.querySelectorAll("[data-approve-user]").forEach((button) => {
            button.addEventListener("click", () => runAction(button, () => U.api(
                "/api/admin/users/" + encodeURIComponent(button.dataset.approveUser) + "/approve-landlord",
                { method: "POST" }), "Landlord approved. They can now publish listings."));
        });
        scope.querySelectorAll("[data-reject-user]").forEach((button) => {
            button.addEventListener("click", async () => {
                const note = await notePrompt("Reject this landlord application?", "They can still sign in to read your note, but cannot publish listings.", "Reject application", "Reason for rejection (optional)");
                if (note === null) return;
                runAction(button, () => U.api("/api/admin/users/" + encodeURIComponent(button.dataset.rejectUser) + "/reject-landlord",
                    { method: "POST", body: { note } }), "Landlord application rejected.");
            });
        });
        scope.querySelectorAll("[data-suspend-user]").forEach((button) => {
            button.addEventListener("click", async () => {
                const note = await notePrompt("Suspend this account?", "The account will be signed out and cannot sign in until it is reactivated.", "Suspend account", "Reason for suspension (optional)");
                if (note === null) return;
                runAction(button, () => U.api("/api/admin/users/" + encodeURIComponent(button.dataset.suspendUser) + "/suspend",
                    { method: "POST", body: { note } }), "Account suspended.");
            });
        });
        scope.querySelectorAll("[data-reactivate-user]").forEach((button) => {
            button.addEventListener("click", async () => {
                const ok = await U.confirmDialog({
                    title: "Reactivate this account?",
                    message: "The person will be able to sign in again.",
                    confirmText: "Reactivate account"
                });
                if (!ok) return;
                runAction(button, () => U.api("/api/admin/users/" + encodeURIComponent(button.dataset.reactivateUser) + "/reactivate",
                    { method: "POST" }), "Account reactivated.");
            });
        });
        scope.querySelectorAll("[data-promote-user]").forEach((button) => {
            button.addEventListener("click", async () => {
                const ok = await U.confirmDialog({
                    title: "Make this person an administrator?",
                    message: "They will be able to moderate listings and accounts. They must verify their email before using the admin workspace.",
                    confirmText: "Make administrator"
                });
                if (!ok) return;
                runAction(button, () => U.api("/api/admin/users/" + encodeURIComponent(button.dataset.promoteUser) + "/promote",
                    { method: "POST" }), "Administrator access granted.");
            });
        });
        scope.querySelectorAll("[data-revoke-admin]").forEach((button) => {
            button.addEventListener("click", async () => {
                const ok = await U.confirmDialog({
                    title: "Remove administrator access?",
                    message: "Their account becomes a student or landlord account, depending on whether they own listings. This is recorded in the audit log.",
                    confirmText: "Remove admin access",
                    danger: true
                });
                if (!ok) return;
                runAction(button, () => U.api("/api/admin/users/" + encodeURIComponent(button.dataset.revokeAdmin) + "/revoke-admin",
                    { method: "POST" }), "Administrator access removed.");
            });
        });
        scope.querySelectorAll("[data-revoke-invitation]").forEach((button) => {
            button.addEventListener("click", async () => {
                const ok = await U.confirmDialog({
                    title: "Revoke this invitation?",
                    message: "The invitation link will stop working immediately.",
                    confirmText: "Revoke invitation",
                    danger: true
                });
                if (!ok) return;
                runAction(button, () => U.api("/api/admin/invitations/" + encodeURIComponent(button.dataset.revokeInvitation),
                    { method: "DELETE" }), "Invitation revoked.");
            });
        });
        bindGoto(scope);
    }

    // -------------------------------------------------------------- overview
    function statCard(label, value, hint) {
        return '<article class="admin-stat"><span>' + U.escapeHtml(label) + '</span><strong>' +
            U.escapeHtml(String(value)) + '</strong><small>' + U.escapeHtml(hint || "") + '</small></article>';
    }

    function auditIcon(action) {
        if (!action) return "•";
        if (action.startsWith("PROPERTY")) return "⌂";
        if (action.startsWith("POST") || action.startsWith("COMMENT")) return "☷";
        if (action.startsWith("ADMIN")) return "♜";
        if (action.startsWith("UNIVERSITY")) return "☆";
        return "♙";
    }

    function auditItem(log) {
        const target = log.targetLabel ? U.escapeHtml(log.targetLabel) : "";
        const details = log.details ? U.escapeHtml(log.details) : "";
        return '<div class="admin-audit-item"><span class="admin-audit-icon">' + auditIcon(log.action) + '</span>' +
            '<div><strong>' + U.escapeHtml(U.label(log.action)) + '</strong>' +
            '<p>' + target + (target && details ? " · " : "") + details + '</p>' +
            '<small>' + U.escapeHtml(log.actorName || "System") + ' · ' + U.formatDateTime(log.createdAt) + '</small></div></div>';
    }

    async function renderOverview(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Reading the platform…");
        try {
            const [statsData, pendingLandlords, pendingProperties, recent] = await Promise.all([
                U.api("/api/admin/stats"),
                U.api("/api/admin/users?role=LANDLORD&status=PENDING_APPROVAL&size=5"),
                U.api("/api/admin/properties?status=PENDING_APPROVAL&size=5"),
                U.api("/api/admin/audit/recent")
            ]);
            if (epoch !== renderEpoch) return;
            stats = statsData;
            updateNavCounts();

            let landlordRows = (pendingLandlords.content || []).map((user) =>
                '<tr><td><div class="admin-user-cell">' + U.avatarMarkup({ fullName: user.fullName }) +
                '<span><strong>' + U.escapeHtml(user.fullName) + '</strong><small>' + U.escapeHtml(user.email) + '</small></span></div></td>' +
                '<td>' + U.formatDate(user.createdAt) + '</td>' +
                '<td><div class="admin-actions">' +
                '<button type="button" class="admin-approve" data-approve-user="' + U.escapeHtml(user.id) + '">Approve</button>' +
                '<button type="button" class="admin-danger-link" data-reject-user="' + U.escapeHtml(user.id) + '">Reject</button>' +
                '</div></td></tr>').join("");
            if (!landlordRows) {
                landlordRows = '<tr><td colspan="3" class="admin-empty">No landlord applications are waiting.</td></tr>';
            }

            let propertyRows = (pendingProperties.content || []).map((property) =>
                '<tr><td><strong>' + U.escapeHtml(property.title) + '</strong><small>' +
                U.escapeHtml((property.city || "Ghana") + " · " + U.money(property.rentAmount) + " / month") + '</small></td>' +
                '<td><strong>' + U.escapeHtml(property.landlord ? property.landlord.fullName : "Landlord") + '</strong><small>' +
                U.escapeHtml(property.landlord ? property.landlord.email || "" : "") + '</small></td>' +
                '<td><div class="admin-actions">' +
                '<a class="admin-link-button" href="listing.html?id=' + encodeURIComponent(property.id) + '">Open</a>' +
                '<button type="button" class="admin-approve" data-approve-property="' + U.escapeHtml(property.id) + '">Approve</button>' +
                '<button type="button" class="admin-danger-link" data-reject-property="' + U.escapeHtml(property.id) + '">Reject</button>' +
                '</div></td></tr>').join("");
            if (!propertyRows) {
                propertyRows = '<tr><td colspan="3" class="admin-empty">No listings are waiting for review.</td></tr>';
            }

            viewEl.innerHTML =
                '<div class="admin-stats">' +
                statCard("Pending landlords", stats.pendingLandlords, "Applications to review") +
                statCard("Pending listings", stats.pendingProperties, "Homes waiting for review") +
                statCard("Students", stats.totalStudents, "Student accounts") +
                statCard("Landlords", stats.totalLandlords, "All landlord accounts") +
                statCard("Suspended accounts", stats.suspendedUsers, "Currently blocked") +
                statCard("Approved listings", stats.approvedProperties, "Visible to students") +
                statCard("Student posts", stats.totalPosts, stats.hiddenPosts + " hidden") +
                statCard("Administrators", stats.totalAdmins, stats.originalAdmins + " original") +
                '</div>' +
                '<div class="admin-two-col">' +
                '<section class="admin-card" style="margin-top:0"><div class="admin-card-heading"><div><span class="admin-eyebrow">LANDLORDS</span>' +
                '<h2>Applications waiting</h2></div><button type="button" class="admin-link-button" data-goto-view="users">All users →</button></div>' +
                '<div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Applicant</th><th>Applied</th><th>Action</th></tr></thead><tbody>' +
                landlordRows + '</tbody></table></div></section>' +
                '<section class="admin-card" style="margin-top:0"><div class="admin-card-heading"><div><span class="admin-eyebrow">LISTINGS</span>' +
                '<h2>Waiting for review</h2></div><button type="button" class="admin-link-button" data-goto-view="properties">All listings →</button></div>' +
                '<div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Listing</th><th>Landlord</th><th>Action</th></tr></thead><tbody>' +
                propertyRows + '</tbody></table></div></section>' +
                '</div>' +
                '<section class="admin-card"><div class="admin-card-heading"><div><span class="admin-eyebrow">RECENT ACTIVITY</span>' +
                '<h2>What has happened lately</h2></div><button type="button" class="admin-link-button" data-goto-view="audit">Full audit trail →</button></div>' +
                '<div class="admin-audit-list">' +
                ((recent && recent.length) ? recent.map(auditItem).join("") : '<div class="admin-empty-state">No activity recorded yet.</div>') +
                '</div></section>';

            bindPropertyActions(viewEl);
            bindUserActions(viewEl);
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ------------------------------------------------------------ properties
    function propertyTabs() {
        const tabs = [["", "All"], ["PENDING_APPROVAL", "Pending"], ["APPROVED", "Approved"],
            ["REJECTED", "Rejected"], ["SUSPENDED", "Suspended"], ["REMOVED", "Removed"]];
        return '<div class="admin-tabs">' + tabs.map(([value, text]) =>
            '<button type="button" data-pstatus="' + value + '" class="' + (propertyState.status === value ? "is-active" : "") + '">' +
            text + '</button>').join("") + '</div>';
    }

    function propertyActionButtons(property) {
        let html = '<a class="admin-link-button" href="listing.html?id=' + encodeURIComponent(property.id) + '">Open</a>';
        if (property.status === "PENDING_APPROVAL" || property.status === "REJECTED") {
            html += '<button type="button" class="admin-approve" data-approve-property="' + U.escapeHtml(property.id) + '">Approve</button>';
        }
        if (property.status === "PENDING_APPROVAL" || property.status === "APPROVED") {
            html += '<button type="button" class="admin-danger-link" data-reject-property="' + U.escapeHtml(property.id) + '">Reject</button>';
        }
        if (property.status === "APPROVED") {
            html += '<button type="button" class="admin-danger-link" data-suspend-property="' + U.escapeHtml(property.id) + '">Suspend</button>';
        }
        if (property.status === "REJECTED" || property.status === "SUSPENDED" || property.status === "REMOVED") {
            html += '<button type="button" class="admin-approve" data-restore-property="' + U.escapeHtml(property.id) + '">Restore</button>';
        }
        return html;
    }

    async function renderProperties(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Loading listings…");
        try {
            const params = new URLSearchParams();
            if (propertyState.status) params.set("status", propertyState.status);
            if (propertyState.q) params.set("q", propertyState.q);
            params.set("page", String(propertyState.page));
            params.set("size", "20");
            const result = await U.api("/api/admin/properties?" + params.toString());
            if (epoch !== renderEpoch) return;

            const rows = (result.content || []).map((property) => {
                const landlord = property.landlord || {};
                return '<tr><td><strong>' + U.escapeHtml(property.title) + '</strong><small>' +
                    U.escapeHtml((property.city || "Ghana") + " · " + U.label(property.propertyType) + " · " +
                        (property.universityName || "Near campus")) + '</small></td>' +
                    '<td><strong>' + U.escapeHtml(landlord.fullName || "Landlord") + '</strong><small>' +
                    U.escapeHtml(landlord.email || "") + '</small></td>' +
                    '<td>' + U.money(property.rentAmount) + '<small>per month</small></td>' +
                    '<td>' + adminBadge(property.status) + (property.adminNote ? '<small>' + U.escapeHtml(property.adminNote) + '</small>' : "") + '</td>' +
                    '<td>' + U.formatDate(property.createdAt) + '</td>' +
                    '<td><div class="admin-actions">' + propertyActionButtons(property) + '</div></td></tr>';
            }).join("");

            viewEl.innerHTML = propertyTabs() +
                '<div class="admin-toolbar">' +
                '<input class="input grow" id="property-search" type="search" placeholder="Search title, city or keyword" value="' + U.escapeHtml(propertyState.q) + '">' +
                '<button type="button" class="btn btn-sm btn-primary" id="property-filter">Search</button>' +
                '<button type="button" class="btn btn-sm btn-ghost" id="property-clear">Clear</button>' +
                '</div>' +
                '<div class="admin-card" style="margin-top:0"><div class="admin-table-wrap"><table class="admin-table">' +
                '<thead><tr><th>Listing</th><th>Landlord</th><th>Rent</th><th>Status</th><th>Submitted</th><th>Actions</th></tr></thead><tbody>' +
                (rows || '<tr><td colspan="6" class="admin-empty">No listings match these filters.</td></tr>') +
                '</tbody></table></div>' + pageControls(result.page, result.totalPages, result.totalElements) + '</div>';

            viewEl.querySelectorAll("[data-pstatus]").forEach((button) => {
                button.addEventListener("click", () => {
                    propertyState.status = button.dataset.pstatus;
                    propertyState.page = 0;
                    renderProperties();
                });
            });
            const search = document.getElementById("property-search");
            const apply = () => { propertyState.q = search.value.trim(); propertyState.page = 0; renderProperties(); };
            document.getElementById("property-filter").addEventListener("click", apply);
            search.addEventListener("keydown", (event) => { if (event.key === "Enter") apply(); });
            document.getElementById("property-clear").addEventListener("click", () => {
                propertyState.status = "";
                propertyState.q = "";
                propertyState.page = 0;
                renderProperties();
            });
            bindPager(viewEl, (page) => { propertyState.page = Math.max(0, page); renderProperties(); });
            bindPropertyActions(viewEl);
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ----------------------------------------------------------------- posts
    function postTabs() {
        const tabs = [["", "All"], ["VISIBLE", "Visible"], ["HIDDEN", "Hidden"], ["REMOVED", "Removed"]];
        return '<div class="admin-tabs">' + tabs.map(([value, text]) =>
            '<button type="button" data-postatus="' + value + '" class="' + (postState.status === value ? "is-active" : "") + '">' +
            text + '</button>').join("") + '</div>';
    }

    async function openComments(post) {
        const modal = U.openModal(
            '<div class="row-between"><h2 style="margin:0">Comments</h2>' +
            '<button type="button" class="btn btn-icon btn-ghost" data-close aria-label="Close">×</button></div>' +
            '<p style="margin:6px 0 14px">' + U.escapeHtml(post.title) + '</p>' +
            '<div id="admin-comments"><div class="loading-line"><span class="spinner"></span> Loading comments…</div></div>'
        );
        const box = modal.element.querySelector("#admin-comments");

        async function loadComments() {
            box.innerHTML = '<div class="loading-line"><span class="spinner"></span> Loading comments…</div>';
            try {
                const result = await U.api("/api/admin/posts/" + encodeURIComponent(post.id) + "/comments?size=100");
                if (!result.content || !result.content.length) {
                    box.innerHTML = '<div class="admin-empty-state">No comments on this post yet.</div>';
                    return;
                }
                box.innerHTML = result.content.map((comment) =>
                    '<div class="comment" style="margin-bottom:9px"><div class="comment-head">' +
                    U.avatarMarkup(comment.author || {}) +
                    '<span><strong>' + U.escapeHtml((comment.author && comment.author.fullName) || "Student") + '</strong>' +
                    '<small>' + U.formatDateTime(comment.createdAt) + '</small></span>' +
                    '<span style="margin-left:auto">' + adminBadge(comment.status) + '</span></div>' +
                    '<p class="comment-body">' + U.escapeHtml(comment.body) + '</p>' +
                    '<div class="comment-actions">' +
                    (comment.status === "VISIBLE"
                        ? '<button type="button" class="admin-link-button" data-hide-comment="' + U.escapeHtml(comment.id) + '">Hide</button>' +
                          '<button type="button" class="admin-danger-link" data-remove-comment="' + U.escapeHtml(comment.id) + '">Remove</button>'
                        : '<span class="small muted">Moderated</span>') +
                    '</div></div>').join("");

                box.querySelectorAll("[data-hide-comment]").forEach((button) => {
                    button.addEventListener("click", async () => {
                        button.disabled = true;
                        try {
                            await U.api("/api/admin/comments/" + encodeURIComponent(button.dataset.hideComment) + "/hide", { method: "POST" });
                            U.toast("Comment hidden.");
                            loadComments();
                        } catch (error) {
                            U.toast(U.networkMessage(error), "error");
                            button.disabled = false;
                        }
                    });
                });
                box.querySelectorAll("[data-remove-comment]").forEach((button) => {
                    button.addEventListener("click", async () => {
                        const ok = await U.confirmDialog({
                            title: "Remove this comment?",
                            message: "It will disappear from the thread and stay out of public view.",
                            confirmText: "Remove comment",
                            danger: true
                        });
                        if (!ok) return;
                        button.disabled = true;
                        try {
                            await U.api("/api/admin/comments/" + encodeURIComponent(button.dataset.removeComment) + "/remove", { method: "POST" });
                            U.toast("Comment removed.");
                            loadComments();
                        } catch (error) {
                            U.toast(U.networkMessage(error), "error");
                            button.disabled = false;
                        }
                    });
                });
            } catch (error) {
                box.innerHTML = '<div class="admin-empty-state">' + U.escapeHtml(U.networkMessage(error)) + '</div>';
            }
        }

        loadComments();
    }

    async function renderPosts(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Loading student posts…");
        try {
            const params = new URLSearchParams();
            if (postState.status) params.set("status", postState.status);
            if (postState.q) params.set("q", postState.q);
            params.set("page", String(postState.page));
            params.set("size", "20");
            const result = await U.api("/api/admin/posts?" + params.toString());
            if (epoch !== renderEpoch) return;

            const rows = (result.content || []).map((post) => {
                const author = post.author || {};
                return '<tr><td><strong>' + U.escapeHtml(post.title) + '</strong><small class="admin-clamp">' +
                    U.escapeHtml(post.body) + '</small></td>' +
                    '<td><div class="admin-user-cell">' + U.avatarMarkup(author) +
                    '<span><strong>' + U.escapeHtml(author.fullName || "Student") + '</strong><small>' +
                    U.escapeHtml(post.universityName || "Ghana") + '</small></span></div></td>' +
                    '<td>' + (post.commentCount || 0) + '</td>' +
                    '<td>' + adminBadge(post.status) + (post.adminNote ? '<small>' + U.escapeHtml(post.adminNote) + '</small>' : "") + '</td>' +
                    '<td>' + U.formatDate(post.createdAt) + '</td>' +
                    '<td><div class="admin-actions">' +
                    '<a class="admin-link-button" href="post.html?id=' + encodeURIComponent(post.id) + '">Open</a>' +
                    '<button type="button" class="admin-link-button" data-comments-post="' + U.escapeHtml(post.id) + '">Comments</button>' +
                    (post.status === "VISIBLE"
                        ? '<button type="button" class="admin-danger-link" data-hide-post="' + U.escapeHtml(post.id) + '">Hide</button>'
                        : '<button type="button" class="admin-approve" data-restore-post="' + U.escapeHtml(post.id) + '">Restore</button>') +
                    '</div></td></tr>';
            }).join("");

            viewEl.innerHTML = postTabs() +
                '<div class="admin-toolbar">' +
                '<input class="input grow" id="post-search" type="search" placeholder="Search posts by title or content" value="' + U.escapeHtml(postState.q) + '">' +
                '<button type="button" class="btn btn-sm btn-primary" id="post-filter">Search</button>' +
                '<button type="button" class="btn btn-sm btn-ghost" id="post-clear">Clear</button>' +
                '</div>' +
                '<div class="admin-card" style="margin-top:0"><div class="admin-table-wrap"><table class="admin-table">' +
                '<thead><tr><th>Post</th><th>Author</th><th>Comments</th><th>Status</th><th>Posted</th><th>Actions</th></tr></thead><tbody>' +
                (rows || '<tr><td colspan="6" class="admin-empty">No posts match these filters.</td></tr>') +
                '</tbody></table></div>' + pageControls(result.page, result.totalPages, result.totalElements) + '</div>';

            viewEl.querySelectorAll("[data-postatus]").forEach((button) => {
                button.addEventListener("click", () => {
                    postState.status = button.dataset.postatus;
                    postState.page = 0;
                    renderPosts();
                });
            });
            const search = document.getElementById("post-search");
            const apply = () => { postState.q = search.value.trim(); postState.page = 0; renderPosts(); };
            document.getElementById("post-filter").addEventListener("click", apply);
            search.addEventListener("keydown", (event) => { if (event.key === "Enter") apply(); });
            document.getElementById("post-clear").addEventListener("click", () => {
                postState.status = "";
                postState.q = "";
                postState.page = 0;
                renderPosts();
            });
            bindPager(viewEl, (page) => { postState.page = Math.max(0, page); renderPosts(); });

            const postsById = {};
            (result.content || []).forEach((post) => { postsById[post.id] = post; });
            viewEl.querySelectorAll("[data-comments-post]").forEach((button) => {
                button.addEventListener("click", () => {
                    const post = postsById[button.dataset.commentsPost];
                    if (post) openComments(post);
                });
            });
            viewEl.querySelectorAll("[data-hide-post]").forEach((button) => {
                button.addEventListener("click", async () => {
                    const note = await notePrompt("Hide this post?", "It disappears from the public student board. You can restore it later.", "Hide post", "Reason for hiding (optional)");
                    if (note === null) return;
                    runAction(button, () => U.api("/api/admin/posts/" + encodeURIComponent(button.dataset.hidePost) + "/hide",
                        { method: "POST", body: { note } }), "Post hidden from students.");
                });
            });
            viewEl.querySelectorAll("[data-restore-post]").forEach((button) => {
                button.addEventListener("click", () => runAction(button, () => U.api(
                    "/api/admin/posts/" + encodeURIComponent(button.dataset.restorePost) + "/restore",
                    { method: "POST" }), "Post restored to the board."));
            });
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ----------------------------------------------------------------- users
    function userActions(user) {
        let html = "";
        if (user.role === "LANDLORD") {
            if (user.status === "PENDING_APPROVAL" || user.status === "REJECTED") {
                html += '<button type="button" class="admin-approve" data-approve-user="' + U.escapeHtml(user.id) + '">Approve</button>';
            }
            if (user.status === "PENDING_APPROVAL") {
                html += '<button type="button" class="admin-danger-link" data-reject-user="' + U.escapeHtml(user.id) + '">Reject</button>';
            }
        }
        if (user.role !== "ADMIN" && user.status === "ACTIVE") {
            html += '<button type="button" class="admin-danger-link" data-suspend-user="' + U.escapeHtml(user.id) + '">Suspend</button>';
        }
        if (user.role !== "ADMIN" && user.status === "SUSPENDED") {
            html += '<button type="button" class="admin-approve" data-reactivate-user="' + U.escapeHtml(user.id) + '">Reactivate</button>';
        }
        if (me.originalAdmin && user.role !== "ADMIN" && user.status !== "SUSPENDED" && user.id !== me.id) {
            html += '<button type="button" class="admin-link-button" data-promote-user="' + U.escapeHtml(user.id) + '">Make admin</button>';
        }
        if (user.role === "ADMIN") {
            html += '<button type="button" class="admin-link-button" data-goto-view="admins">Manage</button>';
        }
        return html || '<span class="small muted">—</span>';
    }

    async function renderUsers(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Loading accounts…");
        try {
            const params = new URLSearchParams();
            if (userState.role) params.set("role", userState.role);
            if (userState.status) params.set("status", userState.status);
            if (userState.q) params.set("q", userState.q);
            params.set("page", String(userState.page));
            params.set("size", "20");
            const result = await U.api("/api/admin/users?" + params.toString());
            if (epoch !== renderEpoch) return;

            const roleOptions = [["", "All roles"], ["STUDENT", "Students"], ["LANDLORD", "Landlords"], ["ADMIN", "Administrators"]]
                .map(([value, text]) => '<option value="' + value + '"' + (userState.role === value ? " selected" : "") + '>' + text + '</option>').join("");
            const statusOptions = [["", "All statuses"], ["ACTIVE", "Active"], ["PENDING_APPROVAL", "Pending approval"],
                ["REJECTED", "Rejected"], ["SUSPENDED", "Suspended"]]
                .map(([value, text]) => '<option value="' + value + '"' + (userState.status === value ? " selected" : "") + '>' + text + '</option>').join("");

            const rows = (result.content || []).map((user) =>
                '<tr><td><div class="admin-user-cell">' + U.avatarMarkup({ fullName: user.fullName }) +
                '<span><strong>' + U.escapeHtml(user.fullName) + '</strong><small>' + U.escapeHtml(user.email) + '</small></span></div></td>' +
                '<td>' + roleBadge(user.role) + (user.originalAdmin ? '<small>Original administrator</small>' : "") + '</td>' +
                '<td>' + adminBadge(user.status) + (user.adminNote ? '<small>' + U.escapeHtml(user.adminNote) + '</small>' : "") + '</td>' +
                '<td>' + (user.emailVerified
                    ? '<span class="admin-badge is-active">Verified</span>'
                    : '<span class="admin-badge is-pending">Not verified</span>') +
                (user.role === "ADMIN" && !user.adminVerified ? '<small>Admin setup pending</small>' : "") + '</td>' +
                '<td>' + U.formatDate(user.createdAt) + '</td>' +
                '<td><div class="admin-actions">' + userActions(user) + '</div></td></tr>').join("");

            viewEl.innerHTML =
                '<div class="admin-toolbar">' +
                '<select class="select" id="user-role">' + roleOptions + '</select>' +
                '<select class="select" id="user-status">' + statusOptions + '</select>' +
                '<input class="input grow" id="user-search" type="search" placeholder="Search name or email" value="' + U.escapeHtml(userState.q) + '">' +
                '<button type="button" class="btn btn-sm btn-primary" id="user-filter">Apply</button>' +
                '<button type="button" class="btn btn-sm btn-ghost" id="user-clear">Clear</button>' +
                '</div>' +
                '<div class="admin-card" style="margin-top:0"><div class="admin-table-wrap"><table class="admin-table">' +
                '<thead><tr><th>Account</th><th>Role</th><th>Status</th><th>Email</th><th>Joined</th><th>Actions</th></tr></thead><tbody>' +
                (rows || '<tr><td colspan="6" class="admin-empty">No accounts match these filters.</td></tr>') +
                '</tbody></table></div>' + pageControls(result.page, result.totalPages, result.totalElements) + '</div>';

            const apply = () => {
                userState.role = document.getElementById("user-role").value;
                userState.status = document.getElementById("user-status").value;
                userState.q = document.getElementById("user-search").value.trim();
                userState.page = 0;
                renderUsers();
            };
            document.getElementById("user-filter").addEventListener("click", apply);
            document.getElementById("user-search").addEventListener("keydown", (event) => {
                if (event.key === "Enter") apply();
            });
            document.getElementById("user-clear").addEventListener("click", () => {
                userState.role = "";
                userState.status = "";
                userState.q = "";
                userState.page = 0;
                renderUsers();
            });
            bindPager(viewEl, (page) => { userState.page = Math.max(0, page); renderUsers(); });
            bindUserActions(viewEl);
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ------------------------------------------------------------ admins view
    async function renderAdmins(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Loading administrators…");
        try {
            const [admins, invitations] = await Promise.all([
                U.api("/api/admin/admins"),
                U.api("/api/admin/invitations")
            ]);
            if (epoch !== renderEpoch) return;

            const adminRows = (admins || []).map((admin) =>
                '<tr><td><div class="admin-user-cell">' + U.avatarMarkup({ fullName: admin.fullName }) +
                '<span><strong>' + U.escapeHtml(admin.fullName) + '</strong><small>' + U.escapeHtml(admin.email) + '</small></span></div></td>' +
                '<td>' + (admin.originalAdmin
                    ? '<span class="admin-badge role-admin">Original admin</span>'
                    : '<span class="admin-badge role-admin">Administrator</span>') + '</td>' +
                '<td>' + (admin.adminVerified
                    ? '<span class="admin-badge is-active">Verified</span>'
                    : '<span class="admin-badge is-pending">Verification pending</span>') + '</td>' +
                '<td>' + (admin.emailVerified
                    ? '<span class="admin-badge is-active">Email verified</span>'
                    : '<span class="admin-badge is-pending">Email unverified</span>') + '</td>' +
                '<td>' + U.formatDate(admin.createdAt) + '</td>' +
                '<td><div class="admin-actions">' +
                (me.originalAdmin && admin.id !== me.id
                    ? '<button type="button" class="admin-danger-link" data-revoke-admin="' + U.escapeHtml(admin.id) + '">Remove admin</button>'
                    : '<span class="small muted">' + (admin.id === me.id ? "You" : "Protected") + '</span>') +
                '</div></td></tr>').join("");

            const invitationRows = (invitations || []).map((invitation) =>
                '<tr><td><strong>' + U.escapeHtml(invitation.email) + '</strong><small>Invited by ' +
                U.escapeHtml(invitation.invitedByName || "Administrator") + '</small></td>' +
                '<td>' + adminBadge(invitation.status) + '</td>' +
                '<td>' + U.formatDate(invitation.createdAt) + '</td>' +
                '<td>' + U.formatDateTime(invitation.expiresAt) + '</td>' +
                '<td>' + (me.originalAdmin && invitation.status === "PENDING"
                    ? '<button type="button" class="admin-danger-link" data-revoke-invitation="' + U.escapeHtml(invitation.id) + '">Revoke</button>'
                    : '<span class="small muted">—</span>') + '</td></tr>').join("");

            viewEl.innerHTML =
                (!me.originalAdmin
                    ? '<div class="admin-notice"><strong>Read-only team view.</strong> Only the original UniLodge administrators can invite, promote or remove administrators.</div>'
                    : '<div class="admin-inline-form" style="margin-top:0"><h3>Invite a new administrator</h3>' +
                      '<form id="invite-form"><div class="form-grid">' +
                      '<label class="field">Full name<input class="input" id="invite-name" maxlength="80" placeholder="Their full name"></label>' +
                      '<label class="field">Email address<input class="input" id="invite-email" type="email" maxlength="160" placeholder="name@example.com"></label>' +
                      '</div><div class="form-actions"><button class="btn btn-primary" type="submit">Send invitation</button></div></form>' +
                      '<p class="help" style="margin-top:8px">They receive an email invitation, verify with a one-time code and set their own password. Invitations expire after 7 days.</p></div>') +
                '<section class="admin-card" style="margin-top:0"><div class="admin-card-heading"><div><span class="admin-eyebrow">TEAM</span>' +
                '<h2>Current administrators</h2></div><span class="admin-badge role-admin">' + (admins || []).length + ' total</span></div>' +
                '<div class="admin-table-wrap"><table class="admin-table">' +
                '<thead><tr><th>Administrator</th><th>Type</th><th>Admin access</th><th>Email</th><th>Joined</th><th>Actions</th></tr></thead><tbody>' +
                (adminRows || '<tr><td colspan="6" class="admin-empty">No administrators found.</td></tr>') +
                '</tbody></table></div></section>' +
                '<section class="admin-card"><div class="admin-card-heading"><div><span class="admin-eyebrow">INVITATIONS</span>' +
                '<h2>Pending and past invitations</h2></div></div>' +
                '<div class="admin-table-wrap"><table class="admin-table">' +
                '<thead><tr><th>Email</th><th>Status</th><th>Sent</th><th>Expires</th><th>Actions</th></tr></thead><tbody>' +
                (invitationRows || '<tr><td colspan="5" class="admin-empty">No administrator invitations yet.</td></tr>') +
                '</tbody></table></div></section>';

            if (me.originalAdmin) {
                document.getElementById("invite-form").addEventListener("submit", async (event) => {
                    event.preventDefault();
                    const name = document.getElementById("invite-name").value.trim();
                    const email = document.getElementById("invite-email").value.trim();
                    if (name.length < 2) {
                        U.toast("Enter the person's full name.", "warn");
                        return;
                    }
                    if (!email) {
                        U.toast("Enter their email address.", "warn");
                        return;
                    }
                    const button = event.target.querySelector('button[type="submit"]');
                    button.disabled = true;
                    try {
                        await U.api("/api/admin/invitations", { method: "POST", body: { fullName: name, email } });
                        U.toast("Invitation email sent to " + email + ".");
                        setAlert("", "");
                        renderAdmins();
                    } catch (error) {
                        U.toast(U.networkMessage(error), "error");
                        setAlert(U.escapeHtml(U.networkMessage(error)), "danger");
                        button.disabled = false;
                    }
                });
            }
            bindUserActions(viewEl);
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ---------------------------------------------------------------- audit
    async function renderAudit(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Loading the audit trail…");
        try {
            const result = await U.api("/api/admin/audit?page=" + auditState.page + "&size=30");
            if (epoch !== renderEpoch) return;
            const items = (result.content || []).map(auditItem).join("");
            viewEl.innerHTML = '<div class="admin-card" style="margin-top:0">' +
                '<div class="admin-card-heading"><div><span class="admin-eyebrow">RECORD</span>' +
                '<h2>Every moderation and governance decision</h2></div><span class="admin-badge is-active">' +
                result.totalElements + ' entries</span></div>' +
                '<div class="admin-audit-list">' +
                (items || '<div class="admin-empty-state">No audit entries yet.</div>') +
                '</div>' + pageControls(result.page, result.totalPages, result.totalElements) + '</div>';
            bindPager(viewEl, (page) => { auditState.page = Math.max(0, page); renderAudit(); });
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ---------------------------------------------------------- universities
    function openUniversityForm(university) {
        const editing = !!university;
        const value = (key) => U.escapeHtml(university && university[key] != null ? String(university[key]) : "");
        const modal = U.openModal(
            '<div class="row-between"><h2 style="margin:0">' + (editing ? "Edit institution" : "Add an institution") + '</h2>' +
            '<button type="button" class="btn btn-icon btn-ghost" data-close aria-label="Close">×</button></div>' +
            '<p style="margin:6px 0 0">Institutions appear in search filters and listing forms once active.</p>' +
            '<form id="university-form" class="stack" style="margin-top:14px">' +
            '<div class="form-grid">' +
            '<label class="field span-2">Institution name<input class="input" id="uni-name" maxlength="160" value="' + value("name") + '" placeholder="e.g. University of Ghana"></label>' +
            '<label class="field">Short name<input class="input" id="uni-short" maxlength="30" value="' + value("shortName") + '" placeholder="e.g. UG"></label>' +
            '<label class="field">City<input class="input" id="uni-city" maxlength="120" value="' + value("city") + '" placeholder="Legon"></label>' +
            '<label class="field">Region<input class="input" id="uni-region" maxlength="120" value="' + value("region") + '" placeholder="Greater Accra"></label>' +
            '<label class="field">Latitude<input class="input" id="uni-lat" type="number" step="any" min="-90" max="90" value="' + value("latitude") + '" placeholder="5.6505"></label>' +
            '<label class="field">Longitude<input class="input" id="uni-lng" type="number" step="any" min="-180" max="180" value="' + value("longitude") + '" placeholder="-0.1870"></label>' +
            '<label class="inline-check span-2"><input type="checkbox" id="uni-active"' + (!university || university.active ? " checked" : "") + '> Active and visible to students</label>' +
            '</div>' +
            '<p class="field-error" id="uni-error" hidden></p>' +
            '<div class="form-actions"><button type="button" class="btn btn-ghost" data-close>Cancel</button>' +
            '<button type="submit" class="btn btn-primary">' + (editing ? "Save institution" : "Add institution") + '</button></div>' +
            '</form>'
        );
        const form = modal.element.querySelector("#university-form");
        const error = modal.element.querySelector("#uni-error");
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            error.hidden = true;
            const body = {
                name: modal.element.querySelector("#uni-name").value.trim(),
                shortName: modal.element.querySelector("#uni-short").value.trim() || null,
                city: modal.element.querySelector("#uni-city").value.trim() || null,
                region: modal.element.querySelector("#uni-region").value.trim() || null,
                latitude: Number(modal.element.querySelector("#uni-lat").value),
                longitude: Number(modal.element.querySelector("#uni-lng").value),
                active: modal.element.querySelector("#uni-active").checked
            };
            if (body.name.length < 2) {
                error.hidden = false;
                error.textContent = "Enter the institution name.";
                return;
            }
            if (Number.isNaN(body.latitude) || Number.isNaN(body.longitude)) {
                error.hidden = false;
                error.textContent = "Enter the campus latitude and longitude.";
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await U.api(editing ? "/api/admin/universities/" + encodeURIComponent(university.id) : "/api/admin/universities", {
                    method: editing ? "PUT" : "POST",
                    body
                });
                modal.close();
                U.toast(editing ? "Institution updated." : "Institution added.");
                renderUniversities();
            } catch (err) {
                error.hidden = false;
                error.textContent = U.networkMessage(err);
                button.disabled = false;
            }
        });
    }

    async function renderUniversities(epoch = ++renderEpoch) {
        viewEl.innerHTML = adminLoading("Loading institutions…");
        try {
            universitiesCache = await U.api("/api/admin/universities");
            if (epoch !== renderEpoch) return;
            const rows = (universitiesCache || []).map((university) =>
                '<tr><td><strong>' + U.escapeHtml(university.name) + '</strong><small>' +
                U.escapeHtml(university.shortName || "Ghana") + '</small></td>' +
                '<td>' + U.escapeHtml((university.city || "—") + (university.region ? " · " + university.region : "")) + '</td>' +
                '<td>' + (university.latitude != null ? Number(university.latitude).toFixed(4) : "—") + ', ' +
                (university.longitude != null ? Number(university.longitude).toFixed(4) : "—") + '</td>' +
                '<td>' + (university.active
                    ? '<span class="admin-badge is-active">Active</span>'
                    : '<span class="admin-badge is-removed">Hidden</span>') + '</td>' +
                '<td><button type="button" class="admin-link-button" data-edit-university="' + U.escapeHtml(university.id) + '">Edit</button></td></tr>').join("");

            viewEl.innerHTML = '<div class="admin-card" style="margin-top:0"><div class="admin-card-heading"><div>' +
                '<span class="admin-eyebrow">CAMPUSES</span><h2>Universities and colleges</h2></div>' +
                '<button type="button" class="admin-link-button" id="add-university-inline">+ Add institution</button></div>' +
                '<div class="admin-table-wrap"><table class="admin-table">' +
                '<thead><tr><th>Institution</th><th>Location</th><th>Campus coordinates</th><th>Status</th><th>Actions</th></tr></thead><tbody>' +
                (rows || '<tr><td colspan="5" class="admin-empty">No institutions yet.</td></tr>') +
                '</tbody></table></div></div>';

            document.getElementById("add-university-inline").addEventListener("click", () => openUniversityForm(null));
            viewEl.querySelectorAll("[data-edit-university]").forEach((button) => {
                button.addEventListener("click", () => {
                    const university = universitiesCache.find((item) => item.id === button.dataset.editUniversity);
                    if (university) openUniversityForm(university);
                });
            });
        } catch (error) {
            if (epoch === renderEpoch) viewError(error);
        }
    }

    // ----------------------------------------------------------------- views
    function setHeading(viewName) {
        const meta = META[viewName] || META.overview;
        eyebrowEl.textContent = meta.eyebrow;
        titleEl.textContent = meta.title;
        descEl.textContent = meta.description;
        topbarLabel.textContent = meta.eyebrow;
        actionsEl.innerHTML = meta.actions;
        actionsEl.querySelectorAll("[data-action]").forEach((button) => {
            button.addEventListener("click", () => {
                const action = button.dataset.action;
                if (action === "refresh") {
                    if (current === "overview") loadStats();
                    renderView(current);
                } else if (action === "add-university") {
                    openUniversityForm(null);
                }
            });
        });
        nav.querySelectorAll("[data-view]").forEach((button) => {
            button.classList.toggle("is-active", button.dataset.view === viewName);
        });
    }

    async function renderView(viewName) {
        if (!META[viewName]) viewName = "overview";
        current = viewName;
        setAlert("", "");
        if (viewName === "overview") await renderOverview();
        else if (viewName === "properties") await renderProperties();
        else if (viewName === "posts") await renderPosts();
        else if (viewName === "users") await renderUsers();
        else if (viewName === "admins") await renderAdmins();
        else if (viewName === "audit") await renderAudit();
        else if (viewName === "universities") await renderUniversities();
    }

    function showView(viewName) {
        if (!META[viewName]) viewName = "overview";
        history.replaceState(null, "", "admin.html#" + viewName);
        setHeading(viewName);
        renderView(viewName);
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function paintIdentity() {
        const name = me.fullName || "Administrator";
        const inner = me.avatarUrl
            ? '<img src="' + U.escapeHtml(me.avatarUrl) + '" alt="" style="width:100%;height:100%;object-fit:cover;border-radius:50%">'
            : U.escapeHtml(U.initials(name));
        identity.innerHTML = '<span class="admin-avatar" style="overflow:hidden">' + inner + '</span>' +
            '<span><strong>' + U.escapeHtml(name) + '</strong><small>' + U.escapeHtml(me.email) + '</small></span>';
    }

    async function init() {
        me = await U.requireAdmin();
        if (!me) return;
        paintIdentity();
        document.getElementById("admin-logout").addEventListener("click", () => U.logout());

        nav.querySelectorAll("[data-view]").forEach((button) => {
            button.addEventListener("click", () => showView(button.dataset.view));
        });

        window.addEventListener("hashchange", () => {
            const viewName = location.hash.replace("#", "");
            if (META[viewName] && viewName !== current) {
                setHeading(viewName);
                renderView(viewName);
            }
        });

        await loadStats();
        const initial = location.hash.replace("#", "");
        showView(META[initial] ? initial : "overview");
    }

    init();
})();
