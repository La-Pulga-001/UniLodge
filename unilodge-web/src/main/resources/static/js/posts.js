/* Student board: search, create/edit posts and saved state. */
(function () {
    "use strict";
    const U = window.UniLodge;
    const grid = document.getElementById("post-grid");
    const empty = document.getElementById("post-empty");
    const pagination = document.getElementById("post-pagination");
    const count = document.getElementById("post-count");
    const q = document.getElementById("post-q");
    const university = document.getElementById("post-university");
    let page = 0;
    let total = 0;
    let savedIds = [];
    let universities = [];
    let me = null;

    function card(post) {
        const saved = savedIds.includes(post.id) || post.saved;
        const author = post.author || {};
        return '<article class="post-card">' +
            (post.status !== "VISIBLE" ? '<div style="margin-bottom:8px">' + U.statusBadge(post.status) + '</div>' : "") +
            '<div class="post-tags">' +
            '<span>' + U.escapeHtml(post.universityName || "Ghana") + '</span>' +
            (post.preferredLocation ? '<span>⌖ ' + U.escapeHtml(post.preferredLocation) + '</span>' : "") +
            (post.budgetMax ? '<span>up to ' + U.money(post.budgetMax) + '</span>' : "") +
            '<span>' + (post.roommatesNeeded || 1) + ' roommate' + ((post.roommatesNeeded || 1) === 1 ? "" : "s") + ' needed</span>' +
            '</div>' +
            '<h3><a href="post.html?id=' + encodeURIComponent(post.id) + '">' + U.escapeHtml(post.title) + '</a></h3>' +
            '<p>' + U.escapeHtml(post.body) + '</p>' +
            '<div class="post-meta">' +
            '<span class="author">' + U.avatarMarkup(author) + '<span>' + U.escapeHtml(author.fullName || "Student") + '<br><small>' + U.timeAgo(post.createdAt) + '</small></span></span>' +
            '<span>💬 ' + (post.commentCount || 0) + ' · <button type="button" class="save-button" style="position:static;width:auto;height:auto;background:transparent;font-size:14px;vertical-align:middle" data-save-post="' + U.escapeHtml(post.id) + '" aria-pressed="' + saved + '">' + (saved ? "♥" : "♡") + '</button></span>' +
            '</div></article>';
    }

    function bindSaves() {
        grid.querySelectorAll("[data-save-post]").forEach((button) => {
            button.addEventListener("click", async () => {
                if (!U.token()) { U.toast("Sign in to save posts.", "warn"); setTimeout(() => U.goLogin(), 650); return; }
                button.disabled = true;
                try {
                    const result = await U.api("/api/posts/" + encodeURIComponent(button.dataset.savePost) + "/save", { method: "POST" });
                    button.textContent = result.saved ? "♥" : "♡";
                    button.setAttribute("aria-pressed", String(result.saved));
                    U.toast(result.saved ? "Post saved." : "Post removed from saved.");
                } catch (error) { U.toast(U.networkMessage(error), "error"); }
                finally { button.disabled = false; }
            });
        });
    }

    function renderPagination() {
        pagination.innerHTML = "";
        pagination.hidden = total <= 1;
        if (total <= 1) return;
        const add = (label, target, opts) => {
            const b = document.createElement("button");
            b.type = "button"; b.textContent = label;
            if (opts && opts.active) b.classList.add("is-active");
            if (opts && opts.disabled) b.disabled = true;
            b.addEventListener("click", () => { page = target; load(); });
            pagination.appendChild(b);
        };
        add("‹", Math.max(0, page - 1), { disabled: page === 0 });
        for (let i = 0; i < total; i++) {
            if (total > 7 && i > 1 && i < total - 2 && Math.abs(i - page) > 1) {
                if (i === 2 || i === total - 3) {
                    const gap = document.createElement("span");
                    gap.textContent = "…";
                    gap.style.cssText = "color:#9aa49d;font-size:10px;padding:0 4px";
                    pagination.appendChild(gap);
                }
                continue;
            }
            add(String(i + 1), i, { active: i === page });
        }
        add("›", Math.min(total - 1, page + 1), { disabled: page >= total - 1 });
    }

    async function load() {
        const params = new URLSearchParams();
        if (q.value.trim()) params.set("q", q.value.trim());
        if (university.value) params.set("universityId", university.value);
        params.set("page", String(page));
        params.set("size", "8");
        grid.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div>';
        empty.hidden = true;
        try {
            const result = await U.api("/api/posts?" + params.toString());
            total = result.totalPages;
            count.textContent = result.totalElements + (result.totalElements === 1 ? " post" : " posts");
            grid.innerHTML = result.content.map(card).join("");
            empty.hidden = result.content.length > 0;
            bindSaves();
            renderPagination();
        } catch (error) {
            grid.innerHTML = "";
            empty.hidden = false;
            empty.innerHTML = "<strong>We could not load the board.</strong>" + U.escapeHtml(U.networkMessage(error));
            count.textContent = "";
        }
    }

    function openEditor(post) {
        if (!me) {
            U.toast("Sign in to start a post.", "warn");
            setTimeout(() => U.goLogin(), 650);
            return;
        }
        if (me.role !== "STUDENT") {
            U.toast("Only students can start a board post.", "warn");
            return;
        }
        const editing = !!post;
        const modal = U.openModal(
            '<button type="button" class="btn btn-icon btn-ghost" data-close style="position:absolute;top:13px;right:13px" aria-label="Close">×</button>' +
            '<h2>' + (editing ? "Edit your post" : "Start a conversation") + '</h2>' +
            '<p>Keep it clear and kind. Posts are public to signed-in students.</p>' +
            '<form class="stack" id="post-form">' +
            '<div class="form-grid">' +
            '<label class="field span-2">Title<span class="input-wrap" style="min-height:42px"><input class="input" id="pf-title" maxlength="120" required value="' + U.escapeHtml(post ? post.title : "") + '" placeholder="e.g. Looking for a calm roommate near campus"></span></label>' +
            '<label class="field span-2">Details<textarea class="textarea" id="pf-body" maxlength="4000" required placeholder="Tell students about yourself, the home, budget and what you are looking for.">' + U.escapeHtml(post ? post.body : "") + '</textarea></label>' +
            '<label class="field">University<select class="select" id="pf-university" required>' +
            universities.map((u) => '<option value="' + U.escapeHtml(u.id) + '"' + (post && post.universityId === u.id ? " selected" : "") + '>' + U.escapeHtml(u.name) + '</option>').join("") +
            '</select></label>' +
            '<label class="field">Preferred area<input class="input" id="pf-location" maxlength="160" value="' + U.escapeHtml(post ? post.preferredLocation || "" : "") + '" placeholder="e.g. Shai Hills"></label>' +
            '<label class="field">Monthly budget (GH₵)<input class="input" id="pf-budget" type="number" min="0" step="50" value="' + (post && post.budgetMax ? post.budgetMax : "") + '" placeholder="600"></label>' +
            '<label class="field">Roommates needed<input class="input" id="pf-roommates" type="number" min="1" max="10" value="' + (post ? post.roommatesNeeded : 1) + '"></label>' +
            '</div>' +
            '<p class="field-error" id="pf-error" hidden></p>' +
            '<div class="form-actions" style="justify-content:flex-end">' +
            '<button class="btn btn-ghost" type="button" data-close>Cancel</button>' +
            '<button class="btn btn-primary" type="submit">' + (editing ? "Save changes" : "Publish post") + '</button>' +
            '</div></form>'
        );
        const form = modal.element.querySelector("#post-form");
        const error = modal.element.querySelector("#pf-error");
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const body = {
                title: modal.element.querySelector("#pf-title").value.trim(),
                body: modal.element.querySelector("#pf-body").value.trim(),
                universityId: modal.element.querySelector("#pf-university").value || null,
                preferredLocation: modal.element.querySelector("#pf-location").value.trim() || null,
                budgetMax: modal.element.querySelector("#pf-budget").value ? Number(modal.element.querySelector("#pf-budget").value) : null,
                roommatesNeeded: Number(modal.element.querySelector("#pf-roommates").value) || 1
            };
            if (body.title.length < 5) { error.hidden = false; error.textContent = "Give your post a title of at least 5 characters."; return; }
            if (body.body.length < 10) { error.hidden = false; error.textContent = "Add a little more detail (at least 10 characters)."; return; }
            if (!body.universityId) { error.hidden = false; error.textContent = "Choose your university."; return; }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                const result = await U.api(editing ? "/api/posts/" + encodeURIComponent(post.id) : "/api/posts", {
                    method: editing ? "PUT" : "POST", body
                });
                modal.close();
                U.toast(editing ? "Post updated." : "Post published.");
                if (!editing) location.href = "post.html?id=" + encodeURIComponent(result.id);
                else load();
            } catch (err2) {
                error.hidden = false;
                error.textContent = U.networkMessage(err2);
            } finally { button.disabled = false; }
        });
    }

    document.getElementById("post-filters").addEventListener("submit", (event) => {
        event.preventDefault();
        page = 0;
        load();
    });
    document.getElementById("post-reset").addEventListener("click", () => {
        q.value = ""; university.value = ""; page = 0; load();
    });
    document.getElementById("new-post-button").addEventListener("click", () => openEditor(null));

    async function init() {
        if (!U.token()) {
            location.replace(U.loginUrl());
            return;
        }
        me = await U.refreshMe();
        if (!me) {
            U.goLogin();
            return;
        }
        if (me.role === "LANDLORD") {
            U.toast("The student board is for students only.", "warn");
            location.replace("dashboard.html");
            return;
        }
        if (me.role !== "STUDENT") {
            document.getElementById("new-post-button").hidden = true;
        }
        try {
            universities = await U.api("/api/universities", { auth: false });
            university.innerHTML = '<option value="">All universities</option>' +
                universities.map((u) => '<option value="' + U.escapeHtml(u.id) + '">' + U.escapeHtml(u.name) + '</option>').join("");
        } catch (error) { universities = []; }
        savedIds = await U.api("/api/posts/saved/ids").catch(() => []);
        load();
        const editId = U.query("edit");
        if (editId && me) {
            try {
                const post = await U.api("/api/posts/" + encodeURIComponent(editId));
                if (post.author && post.author.id === me.id) openEditor(post);
            } catch (error) { /* ignore */ }
        }
    }

    init();
})();
