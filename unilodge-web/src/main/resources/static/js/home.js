/* Home page: live featured listings and the hero search hand-off. */
(function () {
    "use strict";
    const U = window.UniLodge;

    function card(item, savedIds) {
        const saved = savedIds.includes(item.id);
        return '<article class="property-card">' +
            '<div class="property-photo">' +
            '<a href="listing.html?id=' + encodeURIComponent(item.id) + '"><img src="' + U.photo(item.coverImageUrl) + '" alt="' + U.escapeHtml(item.title) + '" loading="lazy"></a>' +
            (item.status && item.status !== "APPROVED" ? U.statusBadge(item.status) : '<span class="badge badge-approved">' + U.escapeHtml(U.label(item.propertyType)) + '</span>') +
            '<button type="button" class="save-button ' + (saved ? "saved" : "") + '" data-save="' + U.escapeHtml(item.id) + '" aria-pressed="' + saved + '" aria-label="Save ' + U.escapeHtml(item.title) + '">' + (saved ? "♥" : "♡") + '</button>' +
            '</div>' +
            '<div class="property-body">' +
            '<h3><a href="listing.html?id=' + encodeURIComponent(item.id) + '">' + U.escapeHtml(item.title) + '</a></h3>' +
            '<p class="property-location">' + U.escapeHtml(item.city || "Ghana") + ' · ' + U.escapeHtml(item.universityName || "Near campus") + '</p>' +
            '<div class="property-meta">' +
            '<span>' + U.escapeHtml(U.label(item.propertyType)) + '</span>' +
            '<span>' + U.escapeHtml(U.label(item.furnished)) + '</span>' +
            (item.distanceToCampusKm != null ? '<span>' + Number(item.distanceToCampusKm).toFixed(1) + ' km to campus</span>' : '') +
            '</div>' +
            '<div class="property-foot">' +
            '<span class="property-price">' + U.money(item.rentAmount) + ' <small>/ month</small></span>' +
            '<a href="listing.html?id=' + encodeURIComponent(item.id) + '">View place</a>' +
            '</div></div></article>';
    }

    async function load() {
        const grid = document.getElementById("featured-grid");
        const empty = document.getElementById("featured-empty");
        const count = document.getElementById("featured-count");
        let savedIds = [];
        try {
            if (U.token()) {
                savedIds = await U.api("/api/properties/saved/ids").catch(() => []);
            }
            const listings = await U.api("/api/properties/featured", { auth: false });
            if (!listings || !listings.length) {
                grid.innerHTML = "";
                empty.hidden = false;
                count.textContent = "0";
                return;
            }
            grid.innerHTML = listings.map((item) => card(item, savedIds)).join("");
            count.textContent = String(listings.length);
            grid.querySelectorAll("[data-save]").forEach((button) => {
                button.addEventListener("click", async () => {
                    if (!U.token()) {
                        U.toast("Sign in to save listings.", "warn");
                        setTimeout(() => U.goLogin(), 700);
                        return;
                    }
                    button.disabled = true;
                    try {
                        const result = await U.api("/api/properties/" + encodeURIComponent(button.dataset.save) + "/save", { method: "POST" });
                        button.classList.toggle("saved", result.saved);
                        button.setAttribute("aria-pressed", String(result.saved));
                        button.textContent = result.saved ? "♥" : "♡";
                        U.toast(result.saved ? "Saved to your dashboard." : "Removed from saved listings.");
                    } catch (error) {
                        U.toast(U.networkMessage(error), "error");
                    } finally {
                        button.disabled = false;
                    }
                });
            });
        } catch (error) {
            grid.innerHTML = "";
            empty.hidden = false;
            empty.innerHTML = "<strong>We could not load listings right now.</strong>" + U.escapeHtml(U.networkMessage(error));
        }
    }

    document.getElementById("search-form").addEventListener("submit", (event) => {
        event.preventDefault();
        const q = document.getElementById("campus-search").value.trim();
        const budget = document.getElementById("budget-search").value;
        const params = {};
        if (q) params.q = q;
        if (budget && budget !== "all") params.maxRent = budget;
        location.href = "browse.html" + U.buildQuery(params);
    });

    load();
})();
