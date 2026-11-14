/* Listing detail: gallery, landlord contact, save state and owner controls. */
(function () {
    "use strict";
    const U = window.UniLodge;
    const root = document.getElementById("listing-root");
    const id = U.query("id");
    let property = null;
    let map = null;
    let marker = null;

    function whatsapp(phone) {
        const digits = String(phone || "").replace(/[^\d]/g, "");
        if (!digits) return null;
        return "https://wa.me/" + (digits.startsWith("0") ? "233" + digits.slice(1) : digits);
    }

    function gallery(p) {
        const images = p.images && p.images.length ? p.images.slice() : [];
        // Keep the landlord-selected cover photo first in both the hero and the strip.
        images.sort((a, b) => (b.cover ? 1 : 0) - (a.cover ? 1 : 0) || (a.sortOrder - b.sortOrder));
        const main = p.coverImageUrl || (images.length ? images[0].url : null);
        return '<div class="gallery-main">' +
            '<img id="gallery-image" src="' + U.photo(main) + '" alt="' + U.escapeHtml(p.title) + '">' +
            '<button type="button" class="save-button ' + (p.saved ? "saved" : "") + '" id="save-listing" aria-pressed="' + p.saved + '" aria-label="Save this listing">' + (p.saved ? "♥" : "♡") + '</button>' +
            '</div>' +
            (images.length > 1
                ? '<div class="gallery-thumbs" id="gallery-thumbs">' + images.map((image, index) =>
                    '<button type="button" data-url="' + U.escapeHtml(image.url) + '" class="' + (index === 0 ? "is-active" : "") + '" aria-label="Photo ' + (index + 1) + '">' +
                    '<img src="' + U.escapeHtml(image.url) + '" alt=""></button>').join("") + '</div>'
                : "");
    }

    function contactCard(p) {
        const landlord = p.landlord || {};
        const signedIn = !!U.token();
        const hasContact = signedIn && (landlord.phone || landlord.email);
        let actions = "";
        if (hasContact) {
            if (landlord.phone) {
                actions += '<a class="btn btn-primary" href="tel:' + U.escapeHtml(landlord.phone) + '">☎ Call ' + U.escapeHtml(landlord.phone) + '</a>';
                const wa = whatsapp(landlord.phone);
                if (wa) actions += '<a class="btn btn-outline" href="' + wa + '" target="_blank" rel="noopener">WhatsApp landlord</a>';
            }
            if (landlord.email) {
                actions += '<a class="btn btn-outline" href="mailto:' + U.escapeHtml(landlord.email) + '?subject=' +
                    encodeURIComponent("UniLodge enquiry: " + p.title) + '">✉ Email landlord</a>';
            }
        } else {
            actions = '<a class="btn btn-primary" href="' + U.loginUrl() + '">Sign in to see contact details</a>' +
                '<a class="btn btn-outline" href="login.html?mode=register">Create a free account</a>';
        }
        return '<aside class="detail-side">' +
            '<div class="contact-card panel">' +
            '<div class="contact-person">' + U.avatarMarkup({ fullName: landlord.fullName || "Landlord", avatarUrl: landlord.avatarUrl }) +
            '<span><strong>' + U.escapeHtml(landlord.fullName || "Listing owner") + '</strong>' +
            '<small>' + U.escapeHtml(landlord.businessName || "Verified UniLodge landlord") + '</small></span></div>' +
            '<div class="contact-actions">' + actions + '</div>' +
            '<p class="contact-note">UniLodge does not process rent or deposits. Always view a property and confirm details before paying anyone.</p>' +
            '</div>' +
            '<div class="panel">' +
            '<div class="panel-head"><div><h3>At a glance</h3><p>Key details for this home</p></div></div>' +
            '<div class="spec-grid" style="grid-template-columns:1fr 1fr;margin:0;border:0;padding:0">' +
            stat("Monthly rent", U.money(p.rentAmount) + (p.negotiable ? " (negotiable)" : "")) +
            stat("Move-in", U.formatDate(p.moveInDate)) +
            stat("Bedrooms", String(p.bedrooms)) +
            stat("Bathrooms", String(p.bathrooms)) +
            stat("Distance", p.distanceToCampusKm != null ? Number(p.distanceToCampusKm).toFixed(1) + " km" : "—") +
            stat("Views", String(p.viewCount || 0)) +
            '</div></div>' +
            '</aside>';
    }

    function stat(label, value) {
        return '<div><span>' + U.escapeHtml(label) + '</span><strong>' + U.escapeHtml(value) + '</strong></div>';
    }

    function ownerControls(p, me) {
        if (!me) return "";
        const owner = p.landlord && me.id === p.landlord.id;
        const admin = me.role === "ADMIN";
        if (!owner && !admin) return "";
        let html = '<div class="owner-actions">';
        if (owner) {
            if (p.status !== "REMOVED") {
                html += '<a class="btn btn-sm btn-outline" href="listing-editor.html?id=' + encodeURIComponent(p.id) + '">Edit listing</a>';
                if (p.status === "PENDING_APPROVAL") {
                    html += '<span class="badge badge-pending">Awaiting administrator review</span>';
                }
                html += '<button type="button" class="btn btn-sm btn-danger" id="delete-listing">Remove listing</button>';
            } else {
                html += '<button type="button" class="btn btn-sm btn-primary" id="restore-listing">Restore listing</button>';
            }
        }
        if (admin) {
            html += '<a class="btn btn-sm btn-ghost" href="admin.html#properties">Review in admin workspace</a>';
        }
        return html + "</div>";
    }

    function render(p) {
        const statusBanner = p.status !== "APPROVED"
            ? '<div class="alert ' + (p.status === "REJECTED" || p.status === "REMOVED" ? "alert-danger" : "alert-warn") + '" style="margin-bottom:16px">' +
              '<strong>This listing is ' + U.escapeHtml(U.label(p.status).toLowerCase()) + '.</strong>' +
              (p.adminNote ? "Administrator note: " + U.escapeHtml(p.adminNote) : "Only you and administrators can currently see it.") +
              '</div>'
            : "";

        root.innerHTML =
            '<p class="small" style="margin:0 0 13px"><a href="browse.html" style="color:var(--green);text-decoration:none;font-weight:700">← Back to all listings</a></p>' +
            statusBanner +
            '<div class="detail-layout">' +
            '<div>' +
            '<div>' + gallery(p) + '</div>' +
            '<article class="detail-card panel">' +
            '<div class="row" style="gap:7px">' + U.statusBadge(p.status) + '<span class="badge badge-muted">' + U.escapeHtml(U.label(p.propertyType)) + '</span>' +
            (p.utilitiesIncluded ? '<span class="badge badge-approved">Utilities included</span>' : "") + '</div>' +
            '<h1>' + U.escapeHtml(p.title) + '</h1>' +
            '<p class="detail-sub">' + U.escapeHtml(p.address) + ', ' + U.escapeHtml(p.city) + (p.region ? " · " + U.escapeHtml(p.region) : "") + '</p>' +
            '<p class="detail-price">' + U.money(p.rentAmount) + ' <small>/ month' + (p.negotiable ? " · negotiable" : "") + '</small></p>' +
            '<p class="detail-sub">' + U.escapeHtml(p.universityName || "Near campus") + (p.distanceToCampusKm != null ? " · " + Number(p.distanceToCampusKm).toFixed(1) + " km to campus" : "") + '</p>' +
            '<div class="spec-grid">' +
            '<div><span>Type</span><strong>' + U.escapeHtml(U.label(p.propertyType)) + '</strong></div>' +
            '<div><span>Furnishing</span><strong>' + U.escapeHtml(U.label(p.furnished)) + '</strong></div>' +
            '<div><span>Bedrooms</span><strong>' + p.bedrooms + '</strong></div>' +
            '<div><span>Bathrooms</span><strong>' + p.bathrooms + '</strong></div>' +
            '</div>' +
            '<section class="detail-section"><h2>About this home</h2><p class="prose">' + U.escapeHtml(p.description) + '</p></section>' +
            (p.amenities && p.amenities.length
                ? '<section class="detail-section"><h2>Amenities</h2><div class="amenity-list">' +
                  p.amenities.map((a) => '<span>' + U.escapeHtml(a) + '</span>').join("") + '</div></section>'
                : "") +
            '<section class="detail-section"><h2>Move-in and lease</h2>' +
            '<p class="prose">Available from ' + U.formatDate(p.moveInDate) + '. ' +
            U.escapeHtml(p.leaseTerms || "Lease terms can be agreed directly with the landlord.") + '</p>' +
            (p.utilitiesIncluded ? '<p class="prose">Utilities included' + (p.utilitiesDescription ? ": " + U.escapeHtml(p.utilitiesDescription) : ".") + '</p>' : "") +
            '</section>' +
            ownerControls(p, U.currentUser()) +
            '</article>' +
            (p.latitude != null && p.longitude != null
                ? '<section class="detail-section"><h2>Where you would live</h2><div class="detail-map" id="detail-map"></div>' +
                  '<p class="small muted" style="margin:7px 0 0">Approximate location. Confirm the exact address with the landlord before visiting.</p></section>'
                : "") +
            '</div>' +
            contactCard(p) +
            '</div>';

        const save = document.getElementById("save-listing");
        save.addEventListener("click", async () => {
            if (!U.token()) {
                U.toast("Sign in to save this listing.", "warn");
                setTimeout(() => U.goLogin(), 650);
                return;
            }
            save.disabled = true;
            try {
                const result = await U.api("/api/properties/" + encodeURIComponent(p.id) + "/save", { method: "POST" });
                save.classList.toggle("saved", result.saved);
                save.setAttribute("aria-pressed", String(result.saved));
                save.textContent = result.saved ? "♥" : "♡";
                U.toast(result.saved ? "Saved to your dashboard." : "Removed from saved listings.");
            } catch (error) {
                U.toast(U.networkMessage(error), "error");
            } finally {
                save.disabled = false;
            }
        });

        const thumbs = document.getElementById("gallery-thumbs");
        if (thumbs) {
            thumbs.addEventListener("click", (event) => {
                const button = event.target.closest("button[data-url]");
                if (!button) return;
                document.getElementById("gallery-image").src = button.dataset.url;
                thumbs.querySelectorAll("button").forEach((item) => item.classList.toggle("is-active", item === button));
            });
        }

        const remove = document.getElementById("delete-listing");
        if (remove) {
            remove.addEventListener("click", async () => {
                const answer = await U.confirmDialog({
                    title: "Remove this listing?",
                    message: "Students will no longer see it. You can restore it later from your dashboard.",
                    confirmText: "Remove listing", danger: true
                });
                if (!answer) return;
                try {
                    const result = await U.api("/api/properties/" + encodeURIComponent(p.id), { method: "DELETE" });
                    U.toast(result.message || "Listing removed.");
                    setTimeout(() => location.reload(), 600);
                } catch (error) { U.toast(U.networkMessage(error), "error"); }
            });
        }

        const restore = document.getElementById("restore-listing");
        if (restore) {
            restore.addEventListener("click", async () => {
                if (!await U.confirmDialog({ title: "Restore this listing?", message: "It will be sent back to administrators for review before students see it again.", confirmText: "Restore listing" })) return;
                try {
                    await U.api("/api/properties/" + encodeURIComponent(p.id) + "/restore", { method: "POST" });
                    U.toast("Listing restored and sent for review.");
                    setTimeout(() => location.reload(), 600);
                } catch (error) { U.toast(U.networkMessage(error), "error"); }
            });
        }

        if (p.latitude != null && p.longitude != null && window.L) {
            map = L.map("detail-map", { scrollWheelZoom: false }).setView([p.latitude, p.longitude], 15);
            L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
                maxZoom: 19, attribution: "&copy; OpenStreetMap contributors"
            }).addTo(map);
            marker = L.marker([p.latitude, p.longitude]).addTo(map);
            marker.bindPopup('<strong>' + U.escapeHtml(p.title) + '</strong>' + U.money(p.rentAmount) + ' / month');
            setTimeout(() => map.invalidateSize(), 100);
        }
    }

    async function load() {
        if (!id) {
            root.innerHTML = '<div class="empty"><strong>Listing not found.</strong>Choose a home from the <a href="browse.html">browse page</a>.</div>';
            return;
        }
        try {
            property = await U.api("/api/properties/" + encodeURIComponent(id));
            render(property);
        } catch (error) {
            root.innerHTML = '<div class="empty" style="margin-top:20px"><strong>We could not open this listing.</strong>' +
                U.escapeHtml(U.networkMessage(error)) + '<br><br><a class="btn btn-outline" href="browse.html">Browse other homes</a></div>';
        }
    }

    load();
})();
