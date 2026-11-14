/* Dashboard: role-aware saved homes, board activity and landlord listings. */
(function () {
    "use strict";
    const U = window.UniLodge;
    const content = document.getElementById("dash-content");
    const nav = document.getElementById("dash-nav");
    const profileBox = document.getElementById("dash-profile");
    const heading = document.getElementById("dash-heading");
    const subheading = document.getElementById("dash-sub");
    const actions = document.getElementById("dash-actions");

    let me = null;
    let section = "overview";
    const state = {
        savedListings: [],
        savedPosts: [],
        myPosts: [],
        myListings: []
    };

    const SECTIONS = ["overview", "saved-listings", "saved-posts", "my-posts", "my-listings"];

    function isLandlord() {
        return me && me.role === "LANDLORD";
    }

    function listingStatusCount(status) {
        return state.myListings.filter((item) => item.status === status).length;
    }

    function statCard(label, value, hint) {
        return '<div class="stat-card"><span>' + U.escapeHtml(label) + '</span><strong>' +
            U.escapeHtml(String(value)) + '</strong><small>' + U.escapeHtml(hint || "") + '</small></div>';
    }

    function listAvatar(author) {
        const name = author && author.fullName ? author.fullName : "Student";
        const url = author && author.avatarUrl;
        const inner = url
            ? '<img src="' + U.escapeHtml(url) + '" alt="" style="width:100%;height:100%;object-fit:cover">'
            : U.escapeHtml(U.initials(name));
        return '<span class="avatar" style="width:42px;height:42px;flex:0 0 42px">' + inner + '</span>';
    }

    function emptyState(title, text, actionHtml) {
        return '<div class="empty"><strong>' + U.escapeHtml(title) + '</strong>' +
            U.escapeHtml(text) + (actionHtml ? '<br><br>' + actionHtml : "") + '</div>';
    }

    function savedListingRow(item) {
        return '<div class="my-listing">' +
            '<a href="listing.html?id=' + encodeURIComponent(item.id) + '">' +
            '<img src="' + U.photo(item.coverImageUrl) + '" alt="' + U.escapeHtml(item.title) + '" loading="lazy"></a>' +
            '<div class="my-listing-main">' +
            '<strong><a href="listing.html?id=' + encodeURIComponent(item.id) + '" style="color:inherit;text-decoration:none">' + U.escapeHtml(item.title) + '</a></strong>' +
            '<small>' + U.escapeHtml(item.city || "Ghana") + ' · ' + U.escapeHtml(item.universityName || "Near campus") + ' · ' + U.money(item.rentAmount) + ' / month</small>' +
            '<div class="row" style="gap:6px;margin-top:7px">' + U.statusBadge(item.status || "APPROVED") +
            '<span class="badge badge-muted">' + U.escapeHtml(U.label(item.furnished)) + '</span></div>' +
            '</div>' +
            '<div class="my-listing-actions">' +
            '<a class="btn btn-sm btn-outline" href="listing.html?id=' + encodeURIComponent(item.id) + '">View</a>' +
            '<button type="button" class="btn btn-sm btn-ghost" data-unsave-listing="' + U.escapeHtml(item.id) + '">Remove</button>' +
            '</div></div>';
    }

    function myListingRow(item) {
        const removed = item.status === "REMOVED";
        let actionsHtml = '<a class="btn btn-sm btn-outline" href="listing.html?id=' + encodeURIComponent(item.id) + '">View</a>';
        if (removed) {
            actionsHtml += '<button type="button" class="btn btn-sm btn-primary" data-restore-listing="' + U.escapeHtml(item.id) + '">Restore</button>';
        } else {
            actionsHtml += '<a class="btn btn-sm btn-outline" href="listing-editor.html?id=' + encodeURIComponent(item.id) + '">Edit</a>' +
                '<button type="button" class="btn btn-sm btn-danger" data-remove-listing="' + U.escapeHtml(item.id) + '">Remove</button>';
        }
        return '<div class="my-listing">' +
            '<a href="listing.html?id=' + encodeURIComponent(item.id) + '">' +
            '<img src="' + U.photo(item.coverImageUrl) + '" alt="' + U.escapeHtml(item.title) + '" loading="lazy"></a>' +
            '<div class="my-listing-main">' +
            '<strong>' + U.escapeHtml(item.title) + '</strong>' +
            '<small>' + U.escapeHtml(item.city || "Ghana") + ' · ' + U.money(item.rentAmount) + ' / month · listed ' + U.formatDate(item.createdAt) + '</small>' +
            '<div class="row" style="gap:6px;margin-top:7px">' + U.statusBadge(item.status) +
            (item.distanceToCampusKm != null ? '<span class="badge badge-muted">' + Number(item.distanceToCampusKm).toFixed(1) + ' km</span>' : "") +
            '</div></div>' +
            '<div class="my-listing-actions">' + actionsHtml + '</div></div>';
    }

    function postRow(post, options) {
        const mode = options && options.mode;
        let actionsHtml = '<a class="btn btn-sm btn-outline" href="post.html?id=' + encodeURIComponent(post.id) + '">Open</a>';
        if (mode === "saved") {
            actionsHtml += '<button type="button" class="btn btn-sm btn-ghost" data-unsave-post="' + U.escapeHtml(post.id) + '">Remove</button>';
        } else if (mode === "mine") {
            actionsHtml += '<a class="btn btn-sm btn-outline" href="posts.html?edit=' + encodeURIComponent(post.id) + '">Edit</a>' +
                '<button type="button" class="btn btn-sm btn-danger" data-delete-post="' + U.escapeHtml(post.id) + '">Delete</button>';
        }
        return '<div class="my-listing">' + listAvatar(post.author || me) +
            '<div class="my-listing-main">' +
            '<strong>' + U.escapeHtml(post.title) + '</strong>' +
            '<small>' + U.escapeHtml(post.universityName || "Ghana") + ' · ' + U.timeAgo(post.createdAt) +
            ' · ' + (post.commentCount || 0) + ' comment' + ((post.commentCount || 0) === 1 ? "" : "s") + '</small>' +
            '<div class="row" style="gap:6px;margin-top:7px">' + U.statusBadge(post.status) +
            (post.budgetMax ? '<span class="badge badge-muted">Up to ' + U.money(post.budgetMax) + '</span>' : "") +
            '</div></div>' +
            '<div class="my-listing-actions">' + actionsHtml + '</div></div>';
    }

    function attentionListings() {
        return state.myListings.filter((item) => ["PENDING_APPROVAL", "REJECTED", "SUSPENDED", "REMOVED"].includes(item.status));
    }

    function overviewHtml() {
        let html = '<div class="stat-grid">';
        if (isLandlord()) {
            html += statCard("Total listings", state.myListings.length, "Homes you have published")
                + statCard("Approved", listingStatusCount("APPROVED"), "Visible to students")
                + statCard("Awaiting review", listingStatusCount("PENDING_APPROVAL"), "With administrators now")
                + statCard("Saved homes", state.savedListings.length, "Places you bookmarked");
        } else {
            html += statCard("Saved homes", state.savedListings.length, "Listings you bookmarked")
                + statCard("Saved posts", state.savedPosts.length, "Conversations to revisit")
                + statCard("My posts", state.myPosts.length, "Roommate requests you started")
                + statCard("Account", U.label(me.status), me.emailVerified ? "Email verified" : "Email not verified");
        }
        html += '</div>';

        if (isLandlord() && me.status !== "ACTIVE") {
            html += '<div class="alert alert-warn" style="margin-top:18px"><span><strong>Your landlord account is ' +
                U.escapeHtml(U.label(me.status).toLowerCase()) + '.</strong>Administrators review new landlord applications before listings can be published.' +
                (me.adminNote ? " Note from the team: " + U.escapeHtml(me.adminNote) : "") + '</span></div>';
        }

        if (isLandlord()) {
            const attention = attentionListings();
            html += '<div class="dash-section"><div class="row-between"><h2>Listings that need attention</h2>' +
                '<a class="btn btn-sm btn-outline" href="listing-editor.html">+ Add listing</a></div>';
            html += attention.length
                ? '<div class="list-stack">' + attention.map(myListingRow).join("") + '</div>'
                : emptyState("Everything looks good.", "No listings are waiting on review, changes or a restore.", '<a class="btn btn-sm btn-outline" href="listing-editor.html">Create a listing</a>');
            html += '</div>';
        }

        html += '<div class="dash-section"><div class="row-between"><h2>Saved for later</h2>' +
            '<button type="button" class="btn btn-sm btn-ghost" data-goto="saved-listings">See all ' + state.savedListings.length + '</button></div>';
        html += state.savedListings.length
            ? '<div class="list-stack">' + state.savedListings.slice(0, 3).map(savedListingRow).join("") + '</div>'
            : emptyState("No saved homes yet.", "Tap the heart on any listing to keep it here for later.", '<a class="btn btn-sm btn-primary" href="browse.html">Browse homes</a>');
        html += '</div>';

        html += '<div class="dash-section"><div class="row-between"><h2>From the student board</h2>' +
            '<button type="button" class="btn btn-sm btn-ghost" data-goto="saved-posts">Saved posts</button></div>';
        html += state.savedPosts.length
            ? '<div class="list-stack">' + state.savedPosts.slice(0, 3).map((post) => postRow(post, { mode: "saved" })).join("") + '</div>'
            : emptyState("Nothing saved from the board.", "Save roommate requests and housing conversations to find them quickly again.", '<a class="btn btn-sm btn-outline" href="posts.html">Visit the student board</a>');
        html += '</div>';

        return html;
    }

    function sectionHtml(name) {
        if (name === "overview") return overviewHtml();

        if (name === "saved-listings") {
            return '<div class="dash-section" style="margin-top:0"><div class="panel-head"><div><h2>Saved listings</h2>' +
                '<p>Homes you bookmarked. Removing one here does not affect the landlord.</p></div>' +
                '<a class="btn btn-sm btn-outline" href="browse.html">Find more</a></div>' +
                (state.savedListings.length
                    ? '<div class="list-stack">' + state.savedListings.map(savedListingRow).join("") + '</div>'
                    : emptyState("No saved listings yet.", "Tap the heart on a home to save it for later.", '<a class="btn btn-sm btn-primary" href="browse.html">Browse homes</a>')) +
                '</div>';
        }

        if (name === "saved-posts") {
            return '<div class="dash-section" style="margin-top:0"><div class="panel-head"><div><h2>Saved posts</h2>' +
                '<p>Roommate requests and conversations you wanted to revisit.</p></div>' +
                '<a class="btn btn-sm btn-outline" href="posts.html">Student board</a></div>' +
                (state.savedPosts.length
                    ? '<div class="list-stack">' + state.savedPosts.map((post) => postRow(post, { mode: "saved" })).join("") + '</div>'
                    : emptyState("No saved posts yet.", "Open a post on the student board and tap the heart to save it.", '<a class="btn btn-sm btn-primary" href="posts.html">Visit the board</a>')) +
                '</div>';
        }

        if (name === "my-posts") {
            return '<div class="dash-section" style="margin-top:0"><div class="panel-head"><div><h2>My posts</h2>' +
                '<p>Your roommate requests and housing conversations.</p></div>' +
                '<a class="btn btn-sm btn-primary" href="posts.html">+ Start a post</a></div>' +
                (state.myPosts.length
                    ? '<div class="list-stack">' + state.myPosts.map((post) => postRow(post, { mode: "mine" })).join("") + '</div>'
                    : emptyState("You have not posted yet.", "Tell students what you are looking for and start a conversation.", '<a class="btn btn-sm btn-primary" href="posts.html">Start a post</a>')) +
                '</div>';
        }

        if (name === "my-listings") {
            return '<div class="dash-section" style="margin-top:0"><div class="panel-head"><div><h2>My listings</h2>' +
                '<p>Every home you manage, including ones waiting for review.</p></div>' +
                '<a class="btn btn-sm btn-primary" href="listing-editor.html">+ Add listing</a></div>' +
                (state.myListings.length
                    ? '<div class="list-stack">' + state.myListings.map(myListingRow).join("") + '</div>'
                    : emptyState("No listings yet.", "Add your first home and send it to administrators for review.", '<a class="btn btn-sm btn-primary" href="listing-editor.html">Create a listing</a>')) +
                '</div>';
        }

        return emptyState("Section not found.", "Use the menu to choose a dashboard section.");
    }

    function bindSection(name) {
        content.querySelectorAll("[data-goto]").forEach((button) => {
            button.addEventListener("click", () => show(button.dataset.goto));
        });

        content.querySelectorAll("[data-unsave-listing]").forEach((button) => {
            button.addEventListener("click", async () => {
                button.disabled = true;
                try {
                    await U.api("/api/properties/" + encodeURIComponent(button.dataset.unsaveListing) + "/save", { method: "POST" });
                    state.savedListings = state.savedListings.filter((item) => item.id !== button.dataset.unsaveListing);
                    U.toast("Removed from saved listings.");
                    render();
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                    button.disabled = false;
                }
            });
        });

        content.querySelectorAll("[data-unsave-post]").forEach((button) => {
            button.addEventListener("click", async () => {
                button.disabled = true;
                try {
                    await U.api("/api/posts/" + encodeURIComponent(button.dataset.unsavePost) + "/save", { method: "POST" });
                    state.savedPosts = state.savedPosts.filter((item) => item.id !== button.dataset.unsavePost);
                    U.toast("Removed from saved posts.");
                    render();
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                    button.disabled = false;
                }
            });
        });

        content.querySelectorAll("[data-remove-listing]").forEach((button) => {
            button.addEventListener("click", async () => {
                const answer = await U.confirmDialog({
                    title: "Remove this listing?",
                    message: "Students will no longer see it. You can restore it later from this dashboard.",
                    confirmText: "Remove listing",
                    danger: true
                });
                if (!answer) return;
                try {
                    await U.api("/api/properties/" + encodeURIComponent(button.dataset.removeListing), { method: "DELETE" });
                    await loadListings();
                    U.toast("Listing removed.");
                    render();
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                }
            });
        });

        content.querySelectorAll("[data-restore-listing]").forEach((button) => {
            button.addEventListener("click", async () => {
                const answer = await U.confirmDialog({
                    title: "Restore this listing?",
                    message: "It will go back to administrators for review before students see it again.",
                    confirmText: "Send for review"
                });
                if (!answer) return;
                try {
                    await U.api("/api/properties/" + encodeURIComponent(button.dataset.restoreListing) + "/restore", { method: "POST" });
                    await loadListings();
                    U.toast("Listing restored and sent for review.");
                    render();
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                }
            });
        });

        content.querySelectorAll("[data-delete-post]").forEach((button) => {
            button.addEventListener("click", async () => {
                const answer = await U.confirmDialog({
                    title: "Delete this post?",
                    message: "The conversation and its comments will be removed from the student board.",
                    confirmText: "Delete post",
                    danger: true
                });
                if (!answer) return;
                try {
                    await U.api("/api/posts/" + encodeURIComponent(button.dataset.deletePost), { method: "DELETE" });
                    state.myPosts = state.myPosts.filter((item) => item.id !== button.dataset.deletePost);
                    U.toast("Post deleted.");
                    render();
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                }
            });
        });

        nav.querySelectorAll("[data-section]").forEach((button) => {
            button.classList.toggle("is-active", button.dataset.section === name);
        });
    }

    function render() {
        content.innerHTML = sectionHtml(section);
        bindSection(section);
    }

    function show(next) {
        if (!SECTIONS.includes(next)) next = "overview";
        if (next === "my-listings" && !isLandlord()) next = "overview";
        section = next;
        history.replaceState(null, "", "dashboard.html#" + section);
        render();
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    async function loadListings() {
        if (!isLandlord()) {
            state.myListings = [];
            return;
        }
        const result = await U.api("/api/properties/mine?size=100");
        state.myListings = result.content || [];
    }

    async function loadData() {
        content.innerHTML = '<div class="loading-line"><span class="spinner"></span> Loading your dashboard…</div>';
        try {
            const [savedListings, savedPosts, myPosts] = await Promise.all([
                U.api("/api/properties/saved").catch(() => []),
                U.api("/api/posts/saved").catch(() => []),
                U.api("/api/posts?authorId=" + encodeURIComponent(me.id) + "&size=50").catch(() => ({ content: [] }))
            ]);
            state.savedListings = savedListings || [];
            state.savedPosts = savedPosts || [];
            state.myPosts = myPosts.content || [];
            await loadListings();
            render();
        } catch (error) {
            content.innerHTML = emptyState("We could not load your dashboard.", U.networkMessage(error),
                '<button type="button" class="btn btn-outline" id="dash-retry">Try again</button>');
            const retry = document.getElementById("dash-retry");
            if (retry) retry.addEventListener("click", loadData);
        }
    }

    function paintProfile() {
        const name = me.fullName || "UniLodge account";
        profileBox.innerHTML = U.avatarMarkup(me) +
            '<span><strong>' + U.escapeHtml(name) + '</strong><small>' + U.escapeHtml(me.email) + '</small></span>';

        heading.textContent = "Welcome back, " + name.split(" ")[0];
        subheading.textContent = isLandlord()
            ? "Manage your homes, follow their review status and keep track of saved places."
            : "Keep track of saved homes, saved conversations and the posts you have started.";

        let actionHtml = '<a class="btn btn-outline" href="browse.html">Browse homes</a>';
        if (isLandlord()) {
            if (me.status === "ACTIVE") {
                actionHtml = '<a class="btn btn-primary" href="listing-editor.html">+ Add listing</a>' + actionHtml;
            }
        } else {
            actionHtml = '<a class="btn btn-primary" href="posts.html">+ Start a post</a>' + actionHtml;
        }
        actions.innerHTML = actionHtml;

        document.getElementById("nav-my-listings").hidden = !isLandlord();
    }

    function bindNav() {
        nav.querySelectorAll("[data-section]").forEach((button) => {
            button.addEventListener("click", () => show(button.dataset.section));
        });
    }

    async function init() {
        me = await U.requireUser();
        if (!me) return;
        if (me.role === "ADMIN") {
            location.href = me.adminVerified ? "admin.html" : "admin-setup.html?verify=1";
            return;
        }
        paintProfile();
        bindNav();
        const initial = location.hash.replace("#", "");
        if (SECTIONS.includes(initial) && (initial !== "my-listings" || isLandlord())) section = initial;
        await loadData();
    }

    init();
})();
