/* UniLodge shared browser utilities: API client, session handling, nav, toasts,
   formatting helpers and small UI primitives used by every page. */
(function () {
    "use strict";

    const TOKEN_KEY = "unilodge.token";
    const USER_KEY = "unilodge.user";
    const PLACEHOLDER = "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="420" viewBox="0 0 640 420">' +
        '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
        '<stop offset="0" stop-color="#e3eddc"/><stop offset="1" stop-color="#f4f7ef"/></linearGradient></defs>' +
        '<rect width="640" height="420" fill="url(#g)"/>' +
        '<path d="M250 230v-70l70-52 70 52v70h-48v-46h-44v46z" fill="#c3d6bb"/>' +
        '<path d="M280 300h80" stroke="#b3c9aa" stroke-width="8" stroke-linecap="round"/>' +
        '<text x="320" y="352" text-anchor="middle" fill="#8ea689" font-family="Arial, sans-serif" font-size="18">UniLodge</text>' +
        '</svg>');

    class ApiError extends Error {
        constructor(status, code, message, fields) {
            super(message || "Something went wrong. Please try again.");
            this.name = "ApiError";
            this.status = status;
            this.code = code || "ERROR";
            this.fields = fields || null;
        }
    }

    function token() {
        try { return localStorage.getItem(TOKEN_KEY); } catch (error) { return null; }
    }

    function currentUser() {
        try {
            const raw = localStorage.getItem(USER_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (error) { return null; }
    }

    function setSession(newToken, newUser) {
        try {
            if (newToken) localStorage.setItem(TOKEN_KEY, newToken);
            if (newUser) localStorage.setItem(USER_KEY, JSON.stringify(newUser));
        } catch (error) { /* private browsing */ }
    }

    function setUser(newUser) {
        try {
            if (newUser) localStorage.setItem(USER_KEY, JSON.stringify(newUser));
            else localStorage.removeItem(USER_KEY);
        } catch (error) { /* ignore */ }
    }

    function clearSession() {
        try {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(USER_KEY);
        } catch (error) { /* ignore */ }
    }

    async function api(path, options) {
        const config = options || {};
        const headers = { Accept: "application/json" };
        if (config.auth !== false && token()) {
            headers.Authorization = "Bearer " + token();
        }
        let body;
        if (config.form) {
            body = config.form;
        } else if (config.body !== undefined) {
            headers["Content-Type"] = "application/json";
            body = JSON.stringify(config.body);
        }
        let response;
        try {
            response = await fetch(path, { method: config.method || "GET", headers, body });
        } catch (error) {
            throw new ApiError(0, "NETWORK", "Cannot reach UniLodge right now. Check your connection and try again.");
        }
        if (response.status === 204) return null;
        const type = response.headers.get("content-type") || "";
        let data = null;
        if (type.includes("application/json")) {
            data = await response.json().catch(() => null);
        } else {
            data = await response.text().catch(() => "");
        }
        if (!response.ok) {
            const message = data && data.message ? data.message : "That request could not be completed.";
            throw new ApiError(response.status, data && data.error, message, data && data.fieldErrors);
        }
        return data;
    }

    function loginUrl() {
        const next = location.pathname.split("/").pop() + location.search;
        return "login.html?next=" + encodeURIComponent(next || "index.html");
    }

    function goLogin() {
        location.href = loginUrl();
    }

    async function refreshMe() {
        if (!token()) return null;
        try {
            const me = await api("/api/auth/me");
            setUser(me);
            return me;
        } catch (error) {
            if (error.status === 401 || error.status === 403) {
                clearSession();
                return null;
            }
            return currentUser();
        }
    }

    async function requireUser(roles) {
        if (!token()) {
            goLogin();
            return null;
        }
        const me = await refreshMe();
        if (!me) {
            goLogin();
            return null;
        }
        if (roles && roles.length && !roles.includes(me.role)) {
            toast("You do not have access to that page.", "warn");
            location.href = me.role === "ADMIN" ? "admin.html" : "dashboard.html";
            return null;
        }
        return me;
    }

    async function requireAdmin() {
        const me = await requireUser(["ADMIN"]);
        if (!me) return null;
        if (!me.adminVerified) {
            location.href = "admin-setup.html?verify=1";
            return null;
        }
        return me;
    }

    function logout() {
        clearSession();
        location.href = "index.html";
    }

    function escapeHtml(value) {
        return String(value === null || value === undefined ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function money(value) {
        if (value === null || value === undefined || value === "") return "—";
        const number = Number(value);
        if (Number.isNaN(number)) return "—";
        return "GH₵" + number.toLocaleString("en-GH", { maximumFractionDigits: 0 });
    }

    function moneyExact(value) {
        const number = Number(value);
        if (Number.isNaN(number)) return "—";
        return "GH₵" + number.toLocaleString("en-GH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    function formatDate(value) {
        if (!value) return "—";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "—";
        return date.toLocaleDateString("en-GH", { day: "numeric", month: "short", year: "numeric" });
    }

    function formatDateTime(value) {
        if (!value) return "—";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "—";
        return date.toLocaleString("en-GH", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
    }

    function timeAgo(value) {
        if (!value) return "";
        const date = new Date(value).getTime();
        if (Number.isNaN(date)) return "";
        const seconds = Math.max(1, Math.floor((Date.now() - date) / 1000));
        if (seconds < 60) return "just now";
        const minutes = Math.floor(seconds / 60);
        if (minutes < 60) return minutes + " min ago";
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return hours + (hours === 1 ? " hour ago" : " hours ago");
        const days = Math.floor(hours / 24);
        if (days < 30) return days + (days === 1 ? " day ago" : " days ago");
        return formatDate(value);
    }

    function initials(value) {
        const words = String(value || "UniLodge").trim().split(/\s+/).slice(0, 2);
        return words.map((word) => word.charAt(0).toUpperCase()).join("") || "U";
    }

    function avatarMarkup(user, extraClass) {
        const name = user && (user.fullName || user.name) ? (user.fullName || user.name) : "UniLodge";
        const url = user && user.avatarUrl ? user.avatarUrl : null;
        const inner = url
            ? '<img src="' + escapeHtml(url) + '" alt="' + escapeHtml(name) + '">'
            : escapeHtml(initials(name));
        return '<span class="avatar ' + (extraClass || "") + '">' + inner + "</span>";
    }

    const LABELS = {
        SINGLE_ROOM: "Single room",
        SELF_CONTAINED: "Self-contained",
        STUDIO: "Studio",
        APARTMENT: "Apartment",
        SHARED_HOUSE: "Shared house",
        HOSTEL: "Hostel",
        FURNISHED: "Furnished",
        SEMI_FURNISHED: "Semi-furnished",
        UNFURNISHED: "Unfurnished",
        PENDING_APPROVAL: "Pending approval",
        APPROVED: "Approved",
        REJECTED: "Rejected",
        SUSPENDED: "Suspended",
        REMOVED: "Removed",
        VISIBLE: "Visible",
        HIDDEN: "Hidden",
        ACTIVE: "Active",
        STUDENT: "Student",
        LANDLORD: "Landlord",
        ADMIN: "Administrator",
        PENDING: "Pending",
        ACCEPTED: "Accepted",
        REVOKED: "Revoked",
        EXPIRED: "Expired",
        ORIGINAL_ADMIN: "Original administrator"
    };

    function label(value) {
        if (!value) return "—";
        return LABELS[value] || String(value).replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
    }

    function statusClass(value) {
        return String(value || "").toLowerCase().replace(/[^a-z0-9_-]/g, "");
    }

    function badge(value, text) {
        return '<span class="badge badge-' + statusClass(value) + '">' + escapeHtml(text || label(value)) + "</span>";
    }

    function statusBadge(value) {
        return '<span class="badge badge-' + statusClass(value) + '">' + escapeHtml(label(value)) + "</span>";
    }

    function photo(url) {
        return url ? escapeHtml(url) : PLACEHOLDER;
    }

    function query(name) {
        return new URLSearchParams(location.search).get(name);
    }

    function buildQuery(params) {
        const search = new URLSearchParams();
        Object.keys(params).forEach((key) => {
            const value = params[key];
            if (value !== undefined && value !== null && value !== "" && value !== "all") {
                search.set(key, value);
            }
        });
        const text = search.toString();
        return text ? "?" + text : "";
    }

    function toast(message, type) {
        let stack = document.querySelector(".toast-stack");
        if (!stack) {
            stack = document.createElement("div");
            stack.className = "toast-stack";
            stack.setAttribute("aria-live", "polite");
            document.body.appendChild(stack);
        }
        const item = document.createElement("div");
        item.className = "toast" + (type ? " toast-" + type : "");
        item.innerHTML = "<span>" + escapeHtml(message) + "</span><button type=\"button\" aria-label=\"Dismiss\">×</button>";
        item.querySelector("button").addEventListener("click", () => item.remove());
        stack.appendChild(item);
        setTimeout(() => item.remove(), 5200);
    }

    function confirmDialog(options) {
        const settings = options || {};
        return new Promise((resolve) => {
            const backdrop = document.createElement("div");
            backdrop.className = "modal-backdrop";
            backdrop.innerHTML =
                '<div class="modal" role="dialog" aria-modal="true">' +
                "<h2>" + escapeHtml(settings.title || "Are you sure?") + "</h2>" +
                "<p>" + escapeHtml(settings.message || "This action cannot be undone.") + "</p>" +
                (settings.noteField
                    ? '<label class="field"><span>' + escapeHtml(settings.noteLabel || "Note (optional)") +
                      '</span><textarea class="textarea" data-note maxlength="500" placeholder="Add a short reason for the account owner…"></textarea></label>'
                    : "") +
                '<div class="form-actions">' +
                '<button type="button" class="btn btn-ghost" data-cancel>Cancel</button>' +
                '<button type="button" class="btn ' + (settings.danger ? "btn-danger" : "btn-primary") + '" data-confirm>' +
                escapeHtml(settings.confirmText || "Confirm") + "</button></div></div>";
            document.body.appendChild(backdrop);
            const close = (value) => {
                backdrop.remove();
                resolve(value);
            };
            backdrop.querySelector("[data-cancel]").addEventListener("click", () => close(false));
            backdrop.querySelector("[data-confirm]").addEventListener("click", () => {
                const note = backdrop.querySelector("[data-note]");
                close(settings.noteField ? { confirmed: true, note: note ? note.value.trim() : "" } : true);
            });
            backdrop.addEventListener("click", (event) => {
                if (event.target === backdrop) close(false);
            });
            document.addEventListener("keydown", function onKey(event) {
                if (event.key === "Escape") {
                    document.removeEventListener("keydown", onKey);
                    close(false);
                }
            });
            const focusTarget = backdrop.querySelector("[data-cancel]");
            if (focusTarget) focusTarget.focus();
        });
    }

    function openModal(html) {
        const backdrop = document.createElement("div");
        backdrop.className = "modal-backdrop";
        backdrop.innerHTML = '<div class="modal" role="dialog" aria-modal="true">' + html + "</div>";
        document.body.appendChild(backdrop);
        const close = () => backdrop.remove();
        backdrop.addEventListener("click", (event) => { if (event.target === backdrop) close(); });
        backdrop.querySelectorAll("[data-close]").forEach((button) => button.addEventListener("click", close));
        return { element: backdrop, close };
    }

    function fieldError(fields, name) {
        return fields && fields[name] ? fields[name] : "";
    }

    function networkMessage(error) {
        if (error instanceof ApiError) return error.message;
        return error && error.message ? error.message : "Something went wrong. Please try again.";
    }

    function renderNav() {
        const target = document.getElementById("header-actions");
        if (!target) return;
        const user = currentUser();
        if (!user) {
            target.innerHTML =
                '<a class="sign-in-link" href="login.html">Sign in</a>' +
                '<a class="header-button" href="browse.html">Explore rooms <span aria-hidden="true">↗</span></a>';
            return;
        }
        const admin = user.role === "ADMIN";
        const dashboard = admin ? "admin.html" : "dashboard.html";
        let menu = '<a href="' + dashboard + '"><span aria-hidden="true">▦</span> ' + (admin ? "Admin workspace" : "My dashboard") + "</a>" +
            '<a href="browse.html"><span aria-hidden="true">⌂</span> Browse listings</a>' +
            '<a href="posts.html"><span aria-hidden="true">☷</span> Student board</a>' +
            '<a href="profile.html"><span aria-hidden="true">♙</span> My profile</a>';
        if (admin && !user.adminVerified) {
            menu = '<a href="admin-setup.html?verify=1"><span aria-hidden="true">!</span> Verify admin access</a>' + menu;
        }
        menu += '<hr><button type="button" data-logout><span aria-hidden="true">↪</span> Sign out</button>';
        target.innerHTML =
            '<details class="nav-user">' +
            '<summary><span class="nav-avatar">' +
            (user.avatarUrl ? '<img src="' + escapeHtml(user.avatarUrl) + '" alt="">' : escapeHtml(initials(user.fullName))) +
            '</span><span class="nav-name">' + escapeHtml((user.fullName || "Account").split(" ")[0]) + "</span></summary>" +
            '<div class="nav-menu"><span class="nav-menu-label">' + escapeHtml(label(user.role)) + "</span>" + menu + "</div></details>" +
            (admin ? '<a class="header-button" href="admin.html">Open admin <span aria-hidden="true">↗</span></a>'
                   : '<a class="header-button" href="dashboard.html">Dashboard <span aria-hidden="true">↗</span></a>');
        const details = target.querySelector("details");
        target.querySelector("[data-logout]").addEventListener("click", (event) => {
            event.preventDefault();
            logout();
        });
        document.addEventListener("click", (event) => {
            if (details && !details.contains(event.target)) details.removeAttribute("open");
        });
    }

    function init() {
        renderNav();
        if (token()) {
            refreshMe().then((user) => {
                if (user) renderNav();
            });
        }
    }

    window.UniLodge = {
        ApiError,
        api,
        token,
        currentUser,
        setSession,
        setUser,
        clearSession,
        refreshMe,
        requireUser,
        requireAdmin,
        loginUrl,
        goLogin,
        logout,
        escapeHtml,
        money,
        moneyExact,
        formatDate,
        formatDateTime,
        timeAgo,
        initials,
        avatarMarkup,
        label,
        badge,
        statusBadge,
        statusClass,
        photo,
        query,
        buildQuery,
        toast,
        confirmDialog,
        openModal,
        fieldError,
        networkMessage,
        renderNav,
        PLACEHOLDER
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
