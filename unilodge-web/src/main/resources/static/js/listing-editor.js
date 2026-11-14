/* Listing editor: create/update a home, pin it on the map and manage photos. */
(function () {
    "use strict";
    const U = window.UniLodge;
    const id = U.query("id");

    const layout = document.getElementById("editor-layout");
    const message = document.getElementById("editor-message");
    const statusBox = document.getElementById("editor-status");
    const form = document.getElementById("listing-form");
    const errorBox = document.getElementById("form-error");
    const saveButton = document.getElementById("save-button");
    const pageTitle = document.getElementById("editor-title");
    const pageSub = document.getElementById("editor-sub");
    const universitySelect = document.getElementById("f-university");
    const utilitiesToggle = document.getElementById("f-utilities");
    const utilitiesField = document.getElementById("utilities-field");
    const imageGrid = document.getElementById("image-grid");
    const imageEmpty = document.getElementById("image-empty");
    const uploadNote = document.getElementById("upload-note");
    const uploadCaption = document.getElementById("upload-caption");
    const imageInput = document.getElementById("image-input");
    const coordLabel = document.getElementById("coord-label");
    const geoQuery = document.getElementById("geo-q");
    const geoResults = document.getElementById("geo-results");

    const DEFAULT_CENTER = [5.8936, 0.0378];
    const MAX_IMAGES = 10;

    let me = null;
    let property = null;
    let universities = [];
    let map = null;
    let marker = null;
    let pendingFiles = [];
    let notice = null;
    let submitting = false;

    function showMessage(html) {
        layout.hidden = true;
        message.hidden = false;
        message.innerHTML = html;
    }

    function todayIso() {
        const now = new Date();
        const offset = now.getTimezoneOffset() * 60000;
        return new Date(now.getTime() - offset).toISOString().slice(0, 10);
    }

    function renderStatus() {
        let html = "";
        if (notice) {
            html += '<div class="alert alert-' + notice.kind + '" style="margin-bottom:16px"><span>' + notice.html + '</span></div>';
        }
        if (property && property.status !== "APPROVED") {
            const kind = property.status === "PENDING_APPROVAL" ? "warn" : "danger";
            html += '<div class="alert alert-' + kind + '" style="margin-bottom:16px"><span><strong>This listing is ' +
                U.escapeHtml(U.label(property.status).toLowerCase()) + '.</strong>' +
                (property.adminNote
                    ? "Administrator note: " + U.escapeHtml(property.adminNote)
                    : "Only you and administrators can see it until it is approved.") +
                '</span></div>';
        }
        statusBox.innerHTML = html;
    }

    function toggleUtilities() {
        utilitiesField.hidden = !utilitiesToggle.checked;
    }

    function setCoordinates(lat, lng, pan) {
        if (lat === null || lat === undefined || lng === null || lng === undefined ||
            Number.isNaN(Number(lat)) || Number.isNaN(Number(lng))) return;
        const latitude = Number(lat);
        const longitude = Number(lng);
        document.getElementById("f-latitude").value = String(latitude);
        document.getElementById("f-longitude").value = String(longitude);
        coordLabel.textContent = "Pinned at " + latitude.toFixed(5) + ", " + longitude.toFixed(5) + " — drag the pin to adjust.";
        if (!map) return;
        if (!marker) {
            marker = L.marker([latitude, longitude], { draggable: true }).addTo(map);
            marker.on("dragend", () => {
                const position = marker.getLatLng();
                setCoordinates(position.lat, position.lng, false);
            });
        } else {
            marker.setLatLng([latitude, longitude]);
        }
        if (pan) map.setView([latitude, longitude], Math.max(map.getZoom(), 15));
    }

    function initMap(lat, lng) {
        if (!window.L) {
            document.getElementById("map-picker").innerHTML =
                '<div class="empty" style="padding:20px">The map library could not load. Check your connection and refresh.</div>';
            return;
        }
        const center = lat != null && lng != null ? [lat, lng] : DEFAULT_CENTER;
        map = L.map("map-picker", { scrollWheelZoom: true }).setView(center, lat != null ? 15 : 13);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);
        map.on("click", (event) => setCoordinates(event.latlng.lat, event.latlng.lng, false));
        if (lat != null && lng != null) setCoordinates(lat, lng, false);
        setTimeout(() => map.invalidateSize(), 100);
    }

    function fillForm(p) {
        document.getElementById("f-title").value = p.title || "";
        document.getElementById("f-description").value = p.description || "";
        if (p.universityId && !universities.some((item) => item.id === p.universityId)) {
            const option = document.createElement("option");
            option.value = p.universityId;
            option.textContent = p.universityName || "Current university";
            universitySelect.appendChild(option);
        }
        universitySelect.value = p.universityId || "";
        document.getElementById("f-type").value = p.propertyType || "SINGLE_ROOM";
        document.getElementById("f-rent").value = p.rentAmount != null ? p.rentAmount : "";
        document.getElementById("f-furnished").value = p.furnished || "UNFURNISHED";
        document.getElementById("f-bedrooms").value = p.bedrooms != null ? p.bedrooms : 1;
        document.getElementById("f-bathrooms").value = p.bathrooms != null ? p.bathrooms : 1;
        document.getElementById("f-address").value = p.address || "";
        document.getElementById("f-city").value = p.city || "";
        document.getElementById("f-region").value = p.region || "";
        document.getElementById("f-move-in").value = p.moveInDate || todayIso();
        document.getElementById("f-lease").value = p.leaseTerms || "";
        document.getElementById("f-negotiable").checked = !!p.negotiable;
        utilitiesToggle.checked = !!p.utilitiesIncluded;
        document.getElementById("f-utilities-description").value = p.utilitiesDescription || "";
        document.getElementById("f-amenities").value = (p.amenities || []).join(", ");
        toggleUtilities();
        if (p.latitude != null && p.longitude != null) setCoordinates(p.latitude, p.longitude, false);
    }

    function prepareNew() {
        document.getElementById("f-move-in").value = todayIso();
        if (universities.length) universitySelect.value = universities[0].id;
        toggleUtilities();
    }

    function editingUi() {
        pageTitle.textContent = "Edit your listing";
        pageSub.textContent = "Updated details are sent back to administrators for review before students see them again.";
        saveButton.textContent = "Save changes";
        document.getElementById("geo-q").placeholder = "Search the address to adjust the pin";
    }

    function renderImages() {
        const images = property && property.images ? property.images : [];
        imageGrid.innerHTML = images.map((image) =>
            '<div class="image-tile' + (image.cover ? " is-cover" : "") + '">' +
            '<img src="' + U.escapeHtml(image.url) + '" alt="Listing photo">' +
            (image.cover ? '<span class="badge badge-approved cover-flag">Cover</span>' : "") +
            '<div class="image-actions">' +
            '<button type="button" data-cover="' + U.escapeHtml(image.id) + '">' + (image.cover ? "Cover" : "Make cover") + '</button>' +
            '<button type="button" data-delete-image="' + U.escapeHtml(image.id) + '">Delete</button>' +
            '</div></div>').join("");

        imageEmpty.hidden = images.length > 0;
        if (!property) {
            imageEmpty.textContent = "Photos make a big difference. You can add them after saving the listing.";
            uploadCaption.textContent = "Choose photos now — they upload as soon as you save.";
        } else if (images.length >= MAX_IMAGES) {
            imageEmpty.hidden = false;
            imageEmpty.textContent = "You have reached the maximum of " + MAX_IMAGES + " photos.";
            uploadCaption.textContent = "Remove a photo before adding another.";
        } else {
            imageEmpty.textContent = "Photos make a big difference. Add up to " + (MAX_IMAGES - images.length) + " more.";
            uploadCaption.textContent = "Add photos to this listing.";
        }

        imageGrid.querySelectorAll("[data-cover]").forEach((button) => {
            button.addEventListener("click", async () => {
                if (!property) return;
                button.disabled = true;
                try {
                    property.images = await U.api("/api/properties/" + encodeURIComponent(property.id) +
                        "/images/" + encodeURIComponent(button.dataset.cover) + "/cover", { method: "PUT" });
                    renderImages();
                    U.toast("Cover photo updated.");
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                    button.disabled = false;
                }
            });
        });

        imageGrid.querySelectorAll("[data-delete-image]").forEach((button) => {
            button.addEventListener("click", async () => {
                if (!property) return;
                const answer = await U.confirmDialog({
                    title: "Delete this photo?",
                    message: "This cannot be undone.",
                    confirmText: "Delete photo",
                    danger: true
                });
                if (!answer) return;
                button.disabled = true;
                try {
                    property.images = await U.api("/api/properties/" + encodeURIComponent(property.id) +
                        "/images/" + encodeURIComponent(button.dataset.deleteImage), { method: "DELETE" });
                    renderImages();
                    U.toast("Photo deleted.");
                } catch (error) {
                    U.toast(U.networkMessage(error), "error");
                    button.disabled = false;
                }
            });
        });
    }

    function pendingNote() {
        const count = pendingFiles.length;
        uploadNote.hidden = count === 0;
        uploadNote.textContent = count
            ? count + (count === 1 ? " photo" : " photos") + " ready — they upload as soon as the listing is saved."
            : "";
    }

    async function uploadFiles(files) {
        if (!property || !files.length) return;
        const images = property.images || [];
        const available = MAX_IMAGES - images.length;
        const queue = files.slice(0, Math.max(0, available));
        if (queue.length < files.length) {
            U.toast("Only " + available + " more photos fit on this listing.", "warn");
        }
        for (let index = 0; index < queue.length; index++) {
            uploadNote.hidden = false;
            uploadNote.textContent = "Uploading photo " + (index + 1) + " of " + queue.length + "…";
            const formData = new FormData();
            formData.append("file", queue[index]);
            try {
                property.images = await U.api("/api/properties/" + encodeURIComponent(property.id) + "/images", {
                    method: "POST",
                    form: formData
                });
                renderImages();
            } catch (error) {
                U.toast(U.networkMessage(error), "error");
                uploadNote.hidden = true;
                return;
            }
        }
        uploadNote.hidden = true;
        if (queue.length) U.toast(queue.length + (queue.length === 1 ? " photo uploaded." : " photos uploaded."));
    }

    imageInput.addEventListener("change", async () => {
        const files = Array.from(imageInput.files || []);
        imageInput.value = "";
        if (!files.length) return;
        if (!property) {
            pendingFiles = pendingFiles.concat(files).slice(0, MAX_IMAGES);
            pendingNote();
            renderImages();
            return;
        }
        uploadNote.hidden = true;
        await uploadFiles(files);
    });

    function collect() {
        const amenities = document.getElementById("f-amenities").value
            .split(/[\n,]/)
            .map((item) => item.trim())
            .filter(Boolean)
            .filter((item, index, all) => all.indexOf(item) === index);
        const rent = document.getElementById("f-rent").value;
        const latitude = document.getElementById("f-latitude").value;
        const longitude = document.getElementById("f-longitude").value;
        return {
            title: document.getElementById("f-title").value.trim(),
            description: document.getElementById("f-description").value.trim(),
            universityId: universitySelect.value || null,
            propertyType: document.getElementById("f-type").value,
            rentAmount: rent === "" ? null : Number(rent),
            negotiable: document.getElementById("f-negotiable").checked,
            utilitiesIncluded: utilitiesToggle.checked,
            utilitiesDescription: utilitiesToggle.checked
                ? document.getElementById("f-utilities-description").value.trim() || null
                : null,
            furnished: document.getElementById("f-furnished").value,
            bedrooms: Number(document.getElementById("f-bedrooms").value),
            bathrooms: Number(document.getElementById("f-bathrooms").value),
            address: document.getElementById("f-address").value.trim(),
            city: document.getElementById("f-city").value.trim(),
            region: document.getElementById("f-region").value.trim() || null,
            latitude: latitude === "" ? null : Number(latitude),
            longitude: longitude === "" ? null : Number(longitude),
            moveInDate: document.getElementById("f-move-in").value || null,
            leaseTerms: document.getElementById("f-lease").value.trim() || null,
            amenities
        };
    }

    function validate(body) {
        if (body.title.length < 5) return "Give your listing a title of at least 5 characters.";
        if (body.description.length < 20) return "Describe the home in at least 20 characters.";
        if (!body.universityId) return "Choose the university this home is near.";
        if (body.rentAmount === null || Number.isNaN(body.rentAmount) || body.rentAmount < 0) return "Enter the monthly rent in Ghana cedis.";
        if (Number.isNaN(body.bedrooms) || body.bedrooms < 0) return "Enter the number of bedrooms.";
        if (Number.isNaN(body.bathrooms) || body.bathrooms < 0) return "Enter the number of bathrooms.";
        if (!body.address) return "Add the street address or area name.";
        if (!body.city) return "Add the city or town.";
        if (body.latitude === null || body.longitude === null) return "Drop a pin on the map so students can see the approximate location.";
        if (!body.moveInDate) return "Choose the date the home is available from.";
        return null;
    }

    function showError(text) {
        errorBox.hidden = false;
        errorBox.textContent = text;
        errorBox.scrollIntoView({ behavior: "smooth", block: "center" });
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (submitting) return;
        errorBox.hidden = true;
        const body = collect();
        const problem = validate(body);
        if (problem) {
            showError(problem);
            return;
        }

        submitting = true;
        saveButton.disabled = true;
        const label = saveButton.textContent;
        saveButton.textContent = "Saving…";
        try {
            const wasNew = !property;
            property = await U.api(property ? "/api/properties/" + encodeURIComponent(property.id) : "/api/properties", {
                method: property ? "PUT" : "POST",
                body
            });
            if (wasNew) {
                history.replaceState(null, "", location.pathname + "?id=" + encodeURIComponent(property.id));
                editingUi();
            }
            renderImages();
            if (pendingFiles.length) {
                const files = pendingFiles;
                pendingFiles = [];
                pendingNote();
                await uploadFiles(files);
            }
            notice = {
                kind: "success",
                html: "<strong>" + (wasNew ? "Listing submitted for review." : "Changes saved and sent for review.") +
                    "</strong>An administrator will check it before it goes live. You can keep editing or add photos below."
            };
            renderStatus();
            U.toast(wasNew ? "Listing submitted for review." : "Changes saved and sent for review.");
            window.scrollTo({ top: 0, behavior: "smooth" });
        } catch (error) {
            const firstField = error && error.fields ? Object.values(error.fields)[0] : null;
            showError(firstField || U.networkMessage(error));
        } finally {
            submitting = false;
            saveButton.disabled = false;
            saveButton.textContent = property ? "Save changes" : label;
        }
    });

    utilitiesToggle.addEventListener("change", toggleUtilities);

    async function geocode() {
        const query = geoQuery.value.trim();
        if (query.length < 3) {
            U.toast("Type at least 3 characters to search an address.", "warn");
            return;
        }
        geoResults.innerHTML = '<div class="loading-line" style="padding:12px"><span class="spinner"></span> Searching…</div>';
        const button = document.getElementById("geo-search");
        button.disabled = true;
        try {
            const results = await U.api("/api/geo/search?q=" + encodeURIComponent(query), { auth: false });
            if (!results || !results.length) {
                geoResults.innerHTML = '<p class="help">No matches. Try a nearby landmark, town or postcode.</p>';
                return;
            }
            geoResults.innerHTML = results.map((result, index) =>
                '<button type="button" data-result="' + index + '">' + U.escapeHtml(result.displayName) + '</button>').join("");
            geoResults.querySelectorAll("[data-result]").forEach((resultButton) => {
                resultButton.addEventListener("click", () => {
                    const result = results[Number(resultButton.dataset.result)];
                    setCoordinates(result.latitude, result.longitude, true);
                    if (!document.getElementById("f-address").value.trim()) {
                        document.getElementById("f-address").value = result.displayName.split(",").slice(0, 2).join(", ").trim();
                    }
                    if (!document.getElementById("f-city").value.trim()) {
                        const parts = result.displayName.split(",").map((part) => part.trim());
                        if (parts.length > 1) document.getElementById("f-city").value = parts[1];
                    }
                    geoResults.innerHTML = "";
                    U.toast("Location pinned.");
                });
            });
        } catch (error) {
            geoResults.innerHTML = '<p class="help">' + U.escapeHtml(U.networkMessage(error)) + '</p>';
        } finally {
            button.disabled = false;
        }
    }

    document.getElementById("geo-search").addEventListener("click", geocode);
    geoQuery.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            event.preventDefault();
            geocode();
        }
    });

    async function init() {
        me = await U.requireUser(["LANDLORD"]);
        if (!me) return;

        if (me.status !== "ACTIVE") {
            pageTitle.textContent = "Listings are on hold";
            pageSub.textContent = "Your landlord account is being reviewed.";
            showMessage('<strong>Your landlord account is ' + U.escapeHtml(U.label(me.status).toLowerCase()) + '.</strong>' +
                'An administrator must approve your application before you can publish listings.' +
                (me.adminNote ? '<br><br>Note from the team: ' + U.escapeHtml(me.adminNote) : "") +
                '<br><br><a class="btn btn-outline" href="dashboard.html">Back to dashboard</a>');
            return;
        }

        try {
            universities = await U.api("/api/universities", { auth: false });
            universitySelect.innerHTML = universities.map((university) =>
                '<option value="' + U.escapeHtml(university.id) + '">' + U.escapeHtml(university.name) + '</option>').join("");
        } catch (error) {
            showMessage('<strong>We could not load universities.</strong>' + U.escapeHtml(U.networkMessage(error)) +
                '<br><br><button type="button" class="btn btn-outline" onclick="location.reload()">Try again</button>');
            return;
        }

        if (id) {
            try {
                property = await U.api("/api/properties/" + encodeURIComponent(id));
            } catch (error) {
                showMessage('<strong>We could not open this listing.</strong>' + U.escapeHtml(U.networkMessage(error)) +
                    '<br><br><a class="btn btn-outline" href="dashboard.html#my-listings">Back to my listings</a>');
                return;
            }
            if (!property.landlord || property.landlord.id !== me.id) {
                showMessage('<strong>That listing belongs to another landlord.</strong>' +
                    '<a class="btn btn-outline" href="dashboard.html#my-listings">Back to my listings</a>');
                return;
            }
            if (property.status === "REMOVED") {
                showMessage('<strong>This listing has been removed.</strong>' +
                    'Restore it from your dashboard before you edit it again.' +
                    '<br><br><a class="btn btn-outline" href="dashboard.html#my-listings">Restore from dashboard</a>');
                return;
            }
            editingUi();
        } else {
            prepareNew();
        }

        layout.hidden = false;
        message.hidden = true;
        document.getElementById("f-move-in").min = todayIso();
        initMap(property && property.latitude != null ? property.latitude : null,
            property && property.longitude != null ? property.longitude : null);
        if (property) fillForm(property);
        renderImages();
        renderStatus();

        if (U.query("created") === "1") {
            notice = { kind: "success", html: "<strong>Listing submitted for review.</strong>Add photos below while it waits for an administrator." };
            renderStatus();
        }
    }

    init();
})();
