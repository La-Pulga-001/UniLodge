/* Student post thread with comments and author controls. */
(function () {
    "use strict";
    const U = window.UniLodge;
    const root = document.getElementById("thread-root");
    const id = U.query("id");
    let post = null;
    let me = U.currentUser();

    function commentMarkup(comment) {
        const author = comment.author || {};
        const own = me && author.id === me.id;
        return '<article class="comment" data-comment="' + U.escapeHtml(comment.id) + '">' +
            '<div class="comment-head">' + U.avatarMarkup(author) +
            '<div><strong>' + U.escapeHtml(author.fullName || "Student") + '</strong> <small>' + U.timeAgo(comment.createdAt) + '</small></div>' +
            (comment.status !== "VISIBLE" ? '<span style="margin-left:auto">' + U.statusBadge(comment.status) + '</span>' : "") +
            '</div>' +
            '<p class="comment-body">' + U.escapeHtml(comment.body) + '</p>' +
            (own ? '<div class="comment-actions"><button type="button" class="btn btn-ghost btn-sm" data-delete-comment="' + U.escapeHtml(comment.id) + '">Delete</button></div>' : "") +
            '</article>';
    }

    function renderComments(comments) {
        const list = document.getElementById("comment-list");
        if (!list) return;
        list.innerHTML = comments.length
            ? comments.map(commentMarkup).join("")
            : '<div class="empty" style="padding:22px"><strong>No replies yet.</strong>Be the first to help or ask a question.</div>';
        list.querySelectorAll("[data-delete-comment]").forEach((button) => {
            button.addEventListener("click", async () => {
                if (!await U.confirmDialog({ title: "Delete your comment?", message: "This cannot be undone.", confirmText: "Delete", danger: true })) return;
                try {
                    await U.api("/api/comments/" + encodeURIComponent(button.dataset.deleteComment), { method: "DELETE" });
                    U.toast("Comment deleted.");
                    loadComments();
                } catch (error) { U.toast(U.networkMessage(error), "error"); }
            });
        });
    }

    async function loadComments() {
        try {
            const result = await U.api("/api/posts/" + encodeURIComponent(id) + "/comments?size=50");
            renderComments(result.content || []);
        } catch (error) {
            const list = document.getElementById("comment-list");
            if (list) list.innerHTML = '<div class="empty">' + U.escapeHtml(U.networkMessage(error)) + '</div>';
        }
    }

    function render(p) {
        const author = p.author || {};
        const own = me && (author.id === me.id || me.role === "ADMIN");
        root.innerHTML =
            '<p class="small" style="margin:0 0 13px"><a href="posts.html" style="color:var(--green);text-decoration:none;font-weight:700">← Back to the student board</a></p>' +
            (p.status !== "VISIBLE"
                ? '<div class="alert alert-warn" style="margin-bottom:16px"><strong>This post is ' + U.escapeHtml(U.label(p.status).toLowerCase()) + '.</strong>' +
                  (p.adminNote ? U.escapeHtml(p.adminNote) : "It is hidden from the public board.") + '</div>'
                : "") +
            '<article class="thread-card panel">' +
            '<div class="post-tags">' +
            '<span>' + U.escapeHtml(p.universityName || "Ghana") + '</span>' +
            (p.preferredLocation ? '<span>⌖ ' + U.escapeHtml(p.preferredLocation) + '</span>' : "") +
            (p.budgetMax ? '<span>up to ' + U.money(p.budgetMax) + ' / month</span>' : "") +
            '<span>' + (p.roommatesNeeded || 1) + ' roommate' + ((p.roommatesNeeded || 1) === 1 ? "" : "s") + ' needed</span>' +
            '</div>' +
            '<h1>' + U.escapeHtml(p.title) + '</h1>' +
            '<p class="prose">' + U.escapeHtml(p.body) + '</p>' +
            '<div class="thread-author">' + U.avatarMarkup(author) +
            '<span><strong>' + U.escapeHtml(author.fullName || "Student") + '</strong>' +
            '<small>' + U.escapeHtml(U.label(author.role)) + ' · posted ' + U.timeAgo(p.createdAt) + '</small></span>' +
            '<span class="spacer"></span>' +
            '<button type="button" class="btn btn-sm btn-outline" id="save-post" aria-pressed="' + p.saved + '">' + (p.saved ? "♥ Saved" : "♡ Save") + '</button>' +
            (own ? '<a class="btn btn-sm btn-ghost" href="posts.html?edit=' + encodeURIComponent(p.id) + '">Edit</a>' : "") +
            (me && author.id === me.id ? '<button type="button" class="btn btn-sm btn-danger" id="delete-post">Delete</button>' : "") +
            '</div></article>' +
            '<section class="dash-section">' +
            '<h2>Replies <span class="muted small" id="comment-count"></span></h2>' +
            '<div class="comment-list" id="comment-list"><div class="loading-line"><span class="spinner"></span> Loading replies…</div></div>' +
            (me
                ? '<form class="panel" id="comment-form" style="margin-top:15px">' +
                  '<label class="field">Add a reply<textarea class="textarea" id="comment-body" maxlength="2000" style="min-height:90px" placeholder="Be helpful and kind…"></textarea></label>' +
                  '<p class="field-error" id="comment-error" hidden></p>' +
                  '<div class="form-actions" style="margin-top:10px"><button class="btn btn-primary" type="submit">Post reply</button></div></form>'
                : '<div class="panel" style="margin-top:15px;text-align:center;color:var(--muted);font-size:11px">' +
                  '<a href="' + U.loginUrl() + '" style="color:var(--green);font-weight:700">Sign in</a> to join the conversation.</div>') +
            '</section>';

        const save = document.getElementById("save-post");
        save.addEventListener("click", async () => {
            if (!U.token()) { U.toast("Sign in to save posts.", "warn"); setTimeout(() => U.goLogin(), 650); return; }
            save.disabled = true;
            try {
                const result = await U.api("/api/posts/" + encodeURIComponent(p.id) + "/save", { method: "POST" });
                p.saved = result.saved;
                save.textContent = result.saved ? "♥ Saved" : "♡ Save";
                save.setAttribute("aria-pressed", String(result.saved));
            } catch (error) { U.toast(U.networkMessage(error), "error"); }
            finally { save.disabled = false; }
        });

        const remove = document.getElementById("delete-post");
        if (remove) {
            remove.addEventListener("click", async () => {
                if (!await U.confirmDialog({ title: "Delete this post?", message: "The conversation and replies will be removed from the board.", confirmText: "Delete post", danger: true })) return;
                try {
                    await U.api("/api/posts/" + encodeURIComponent(p.id), { method: "DELETE" });
                    U.toast("Your post has been removed.");
                    setTimeout(() => { location.href = "posts.html"; }, 600);
                } catch (error) { U.toast(U.networkMessage(error), "error"); }
            });
        }

        const commentForm = document.getElementById("comment-form");
        if (commentForm) {
            commentForm.addEventListener("submit", async (event) => {
                event.preventDefault();
                const bodyEl = document.getElementById("comment-body");
                const errorEl = document.getElementById("comment-error");
                const body = bodyEl.value.trim();
                errorEl.hidden = true;
                if (!body) { errorEl.hidden = false; errorEl.textContent = "Write a reply first."; return; }
                const button = commentForm.querySelector('button[type="submit"]');
                button.disabled = true;
                try {
                    await U.api("/api/posts/" + encodeURIComponent(p.id) + "/comments", { method: "POST", body: { body } });
                    bodyEl.value = "";
                    U.toast("Reply posted.");
                    loadComments();
                } catch (error) {
                    errorEl.hidden = false;
                    errorEl.textContent = U.networkMessage(error);
                } finally { button.disabled = false; }
            });
        }
        loadComments();
    }

    async function load() {
        if (!id) {
            root.innerHTML = '<div class="empty"><strong>Post not found.</strong>Return to the <a href="posts.html">student board</a>.</div>';
            return;
        }
        if (!U.token()) {
            location.replace(U.loginUrl());
            return;
        }
        me = await U.refreshMe() || me;
        if (!me) {
            U.goLogin();
            return;
        }
        if (me.role === "LANDLORD") {
            U.toast("The student board is for students only.", "warn");
            location.replace("dashboard.html");
            return;
        }
        try {
            post = await U.api("/api/posts/" + encodeURIComponent(id));
            render(post);
        } catch (error) {
            root.innerHTML = '<div class="empty" style="margin-top:20px"><strong>We could not open this post.</strong>' +
                U.escapeHtml(U.networkMessage(error)) + '<br><br><a class="btn btn-outline" href="posts.html">Back to the board</a></div>';
        }
    }

    load();
})();
