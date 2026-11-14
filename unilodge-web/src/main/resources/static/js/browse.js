/* Browse page: filtered catalogue, saved state, pagination and map view. */
(function () {
    "use strict";
    const U = window.UniLodge;

    const filters = {
        q: document.getElementById("filter-q"),
        universityId: document.getElementById("filter-university"),
        minRent: document.getElementById("filter-min"),
        maxRent: document.getElementById("filter-max"),
        propertyType: document.getElementById("filter-type"),
        furnished: document.getElementById("filter-furnished"),
        minBedrooms: document.getElementById("filter-bedrooms"),
        maxDistanceKm: document.getElementById("filter-distance"),
        sort: document.getElementById("filter-sort")
    };

    const grid = document.getElementById("results-grid");
    const empty = document.getElementById("results-empty");
    const pagination = document.getElementById("pagination");
    const countEl = document.getElementById("result-count");
    const caption = document.getElementById("results-caption");
    const gridView = document.getElementById("grid-view");
    const mapView = document.getElementById("map-view");
    const mapList = document.getElementById("map-list");

    let page = 0;
    let current = { content: [], totalPages: 0, totalElements: 0 };
    let savedIds = [];
    let view = "grid";
    let map = null;
    let markers = [];

    function readFilters() {
        const p = new URLSearchParams();
        Object.keys(filters).forEach((key) => {
            const value = filters[key].value.trim ? filters[key].value.trim() : filters[key].value;
            if (value && value !== "all") p.set(key, value);
        });
        return p;
    }

    function writeFilters(url) {
        history.replaceState(null, "", "browse.html" + (url || ""));
    }

    function card(item) {
        const saved = savedIds.includes(item.id);
        return '<article class="property-card">' +
            '<div class="property-photo">' +
            '<a href="listing.html?id=' + encodeURIComponent(item.id) + '"><img src="' + U.photo(item.coverImageUrl) + '" alt="' + U.escapeHtml(item.title) + '" loading="lazy"></a>' +
            '<span class="badge badge-approved">' + U.escapeHtml(U.label(item.propertyType)) + '</span>' +
            '<button type="button" class="save-button ' + (saved ? "saved" : "") + '" data-save="' + U.escapeHtml(item.id) + '" aria-pressed="' + saved + '" aria-label="Save ' + U.escapeHtml(item.title) + '">' + (saved ? "♥" : "♡") + '</button>' +
            '</div>' +
            '<div class="property-body">' +
            '<h3><a href="listing.html?id=' + encodeURIComponent(item.id) + '">' + U.escapeHtml(item.title) + '</a></h3>' +
            '<p class="property-location">' + U.escapeHtml(item.city || "Ghana") + ' · ' + U.escapeHtml(item.universityName || "Near campus") + '</p>' +
            '<div class="property-meta">' +
            '<span>' + U.escapeHtml(U.label(item.furnished)) + '</span>' +
            '<span>' + item.bedrooms + ' bed · ' + item.bathrooms + ' bath</span>' +
            (item.distanceToCampusKm != null ? '<span>' + Number(item.distanceToCampusKm).toFixed(1) + ' km to campus</span>' : '') +
            '</div>' +
            '<div class="property-foot">' +
            '<span class="property-price">' + U.money(item.rentAmount) + ' <small>/ month</small></span>' +
            '<a href="listing.html?id=' + encodeURIComponent(item.id) + '">View place</a>' +
            '</div></div></article>';
    }

    function bindSaves(root) {
        root.querySelectorAll("[data-save]").forEach((button) => {
            button.addEventListener("click", async () => {
                if (!U.token()) {
                    U.toast("Sign in to save listings.", "warn");
                    setTimeout(() => U.goLogin(), 650);
                    return;
                }
                button.disabled = true;
                try {
                    const result = await U.api("/api/properties/" + encodeURIComponent(button.dataset.save) + "/save", { method: "POST" });
                    button.classList.toggle("saved", result.saved);
                    button.setAttribute("aria-pressed", String(result.saved));
                    button.textContent = result.saved ? "♥" : "♡";
                    if (result.saved) savedIds.push(button.dataset.save);
                    else savedIds = savedIds.filter((id) => id !== button.dataset.save);
                    U.toast(result.saved ? "Saved to your dashboard." : "Removed from saved listings.");
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                } finally {
                    button.disabled = false;
                }
            });
        });
    }

    function renderPagination() {
        const total = current.totalPages;
        pagination.innerHTML = "";
        pagination.hidden = total <= 1;
        if (total <= 1) return;

        const add = (label, target, options) => {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = label;
            if (options && options.active) button.classList.add("is-active");
            if (options && options.disabled) button.disabled = true;
            button.addEventListener("click", () => { page = target; load(); });
            pagination.appendChild(button);
        };
        add("‹", Math.max(0, page - 1), { disabled: page === 0 });
        const pages = new Set([0, total - 1, page, page - 1, page + 1]);
        const ordered = [...pages].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
        let previous = -2;
        ordered.forEach((p) => {
            if (p - previous > 1) {
                const gap = document.createElement("span");
                gap.textContent = "…";
                gap.style.cssText = "color:#9aa49d;font-size:10px;padding:0 4px";
                pagination.appendChild(gap);
            }
            add(String(p + 1), p, { active: p === page });
            previous = p;
        });
        add("›", Math.min(total - 1, page + 1), { disabled: page >= total - 1 });
    }

    async function load() {
        const query = readFilters();
        query.set("page", String(page));
        query.set("size", "9");
        grid.innerHTML = '<div class="skeleton-card"></div><div class="skeleton-card"></div><div class="skeleton-card"></div>';
        empty.hidden = true;
        try {
            current = await U.api("/api/properties?" + query.toString(), { auth: false });
            countEl.textContent = String(current.totalElements);
            caption.textContent = current.totalElements === 0
                ? "No results"
                : "Showing " + (page * current.size + 1) + "–" + Math.min((page + 1) * current.size, current.totalElements) + " of " + current.totalElements;
            grid.innerHTML = current.content.map(card).join("");
            empty.hidden = current.content.length > 0;
            bindSaves(grid);
            renderPagination();
            writeFilters("?" + query.toString());
            if (view === "map") renderMap();
        } catch (error) {
            grid.innerHTML = "";
            empty.hidden = false;
            empty.innerHTML = "<strong>We could not load listings.</strong>" + U.escapeHtml(U.networkMessage(error));
            countEl.textContent = "—";
            caption.textContent = "";
            pagination.hidden = true;
        }
    }

    async function renderMap() {
        if (!window.L) {
            mapList.innerHTML = '<div class="empty">The map library did not load. Check your connection and refresh.</div>';
            return;
        }
        if (!map) {
            map = L.map("map", { scrollWheelZoom: true }).setView([5.8936, 0.0378], 12);
            L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
                maxZoom: 19,
                attribution: "&copy; OpenStreetMap contributors"
            }).addTo(map);
        }
        markers.forEach((marker) => marker.remove());
        markers = [];
        mapList.innerHTML = '<div class="loading-line"><span class="spinner"></span> Placing listings on the map…</div>';

        const details = await Promise.allSettled(current.content.map((item) =>
            U.api("/api/properties/" + encodeURIComponent(item.id), { auth: false })));
        const located = [];
        details.forEach((result, index) => {
            if (result.status === "fulfilled" && result.value && result.value.latitude != null && result.value.longitude != null) {
                located.push({ summary: current.content[index], detail: result.value });
            }
        });

        if (!located.length) {
            mapList.innerHTML = '<div class="empty"><strong>No pins for this page.</strong>The listings on this page did not include map coordinates.</div>';
            return;
        }

        const bounds = [];
        mapList.innerHTML = located.map(({ summary, detail }) => {
            const id = "map-card-" + summary.id;
            return '<a class="map-list-card" id="' + id + '" href="listing.html?id=' + encodeURIComponent(summary.id) + '">' +
                '<img src="' + U.photo(summary.coverImageUrl) + '" alt="">' +
                '<span><strong>' + U.escapeHtml(summary.title) + '</strong>' +
                '<small>' + U.money(summary.rentAmount) + ' / month · ' + U.escapeHtml(summary.city || "") + '</small></span></a>';
        }).join("");

        located.forEach(({ summary, detail }) => {
            const marker = L.marker([detail.latitude, detail.longitude]).addTo(map);
            marker.bindPopup('<strong>' + U.escapeHtml(summary.title) + '</strong>' +
                U.money(summary.rentAmount) + ' / month<br>' +
                '<a href="listing.html?id=' + encodeURIComponent(summary.id) + '">View place →</a>');
            marker.on("click", () => marker.openPopup());
            markers.push(marker);
            bounds.push([detail.latitude, detail.longitude]);
        });
        if (bounds.length === 1) map.setView(bounds[0], 15);
        else map.fitBounds(bounds, { padding: [36, 36] });
        setTimeout(() => map.invalidateSize(), 60);

        mapList.querySelectorAll(".map-list-card").forEach((link) => {
            link.addEventListener("mouseenter", () => {
                const id = link.id.replace("map-card-", "");
                const found = located.find((entry) => entry.summary.id === id);
                if (found) map.panTo([found.detail.latitude, found.detail.longitude]);
            });
        });
    }

    function setView(next) {
        view = next;
        gridView.hidden = next !== "grid";
        mapView.hidden = next !== "map";
        document.querySelectorAll("[data-view]").forEach((button) => {
            const active = button.dataset.view === next;
            button.classList.toggle("is-active", active);
            button.setAttribute("aria-pressed", String(active));
        });
        if (next === "map") {
            renderMap();
            setTimeout(() => { if (map) map.invalidateSize(); }, 100);
        }
    }

    async function init() {
        try {
            const list = await U.api("/api/universities", { auth: false });
            filters.universityId.innerHTML = '<option value="">All universities</option>' +
                list.map((item) => '<option value="' + U.escapeHtml(item.id) + '">' + U.escapeHtml(item.name) + '</option>').join("");
        } catch (error) { /* the catalogue still works without the list */ }

        const params = new URLSearchParams(location.search);
        Object.keys(filters).forEach((key) => {
            if (params.has(key)) filters[key].value = params.get(key);
        });
        if (params.has("page")) page = Math.max(0, Number(params.get("page")) || 0);

        if (U.token()) {
            savedIds = await U.api("/api/properties/saved/ids").catch(() => []);
        }
        if (params.get("view") === "map") setView("map");
        load();
    }

    document.getElementById("filter-form").addEventListener("submit", (event) => {
        event.preventDefault();
        page = 0;
        load();
    });

    document.getElementById("filter-reset").addEventListener("click", () => {
        Object.values(filters).forEach((control) => {
            control.value = control === filters.sort ? "newest" : "";
        });
        page = 0;
        load();
    });

    document.querySelectorAll("[data-view]").forEach((button) => {
        button.addEventListener("click", () => setView(button.dataset.view));
    });

    init();
})();
