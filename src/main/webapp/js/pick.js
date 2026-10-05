const session = JSON.parse(localStorage.getItem("operationstoolusuario") || "null");
if (!session) {
    window.location.href = "login.html";
}

$(function () {
    loadOperationsChrome();
    const pick = pickFromPath();
    if (!pick) {
        $("#pickPage").html('<p class="text-danger mb-0">This pick address is not valid.</p>');
        return;
    }
    $.get(URLBACKEND + "transport-performance/picks/" + pick)
        .done(renderPick)
        .fail(function (xhr) {
            const message = xhr.status === 404
                ? "Pick " + pick + " was not found."
                : "Pick details could not be loaded.";
            $("#pickPage").html('<p class="text-danger mb-0">' + escapeHtml(message) + "</p>");
        });
});

function pickFromPath() {
    const match = window.location.pathname.match(/\/pick\/(\d+)\/details\/?$/);
    return match ? match[1] : "";
}

function renderPick(data) {
    const header = data.header || {};
    const ship = [header.shipToCountry, header.shipToPostal].filter(Boolean).join(" ");
    const zone = header.carrierZone
        ? header.carrierZone + (header.transitDays == null ? "" : " · " + header.transitDays + " transit days")
        : "";
    const result = header.result
        ? '<div class="tp-result ' + (header.result === "Late" ? "tp-result-late" : "tp-result-ontime") + '">'
        + escapeHtml(header.result) + "</div>"
        : "";

    $("#pickHero").html(
        '<div class="card page-hero mb-3">'
        + '<div class="card-body d-flex align-items-center justify-content-between flex-wrap">'
        + '<div class="d-flex align-items-center mb-2 mb-md-0">'
        + result
        + '<div class="page-hero-badge mr-3"><i class="fas fa-box-open"></i></div>'
        + "<div>"
        + '<h1 class="page-hero-title mb-0">Pick ' + escapeHtml(header.pickInt) + "</h1>"
        + '<div class="page-hero-subtitle">Carrier file ' + escapeHtml(header.pickNumber || "—")
        + " · Order " + escapeHtml(header.orderNumber || "—") + "</div>"
        + "</div></div>"
        + '<a href="dashboard.html" class="btn btn-outline-secondary mt-2 mt-sm-0">'
        + '<i class="fas fa-arrow-left mr-1"></i>Dashboard</a>'
        + "</div></div>"
    );
    $("#pickPage").html(
        facts(header, ship, zone)
        + section("Why this result", "fas fa-clock", timingTable(header))
        + section("Sales Order lines", "fas fa-list-ul", linesTable(data.lines || []))
        + section("Carrier file", "fas fa-truck", carrierTable(data.carrier || []))
        + section("EDI", "fas fa-exchange-alt", ediTable(data.edi || []))
        + section("NCR Cases", "fas fa-folder-open", casesTable(data.cases || []))
    );
}

function facts(header, ship, zone) {
    return '<div class="row">'
        + fact("Account", header.accountName, "fas fa-building")
        + fact("Owner", header.accountOwner, "fas fa-user")
        + fact("Ship to", ship, "fas fa-map-marker-alt")
        + fact("Zone", zone, "fas fa-route")
        + "</div>";
}

function fact(label, value, icon) {
    return '<div class="col-md-6 col-xl-3 mb-3"><div class="card tp-kpi tp-kpi-total h-100">'
        + '<div class="card-body d-flex justify-content-between align-items-center">'
        + "<div>"
        + '<div class="tp-kpi-label">' + escapeHtml(label) + "</div>"
        + '<div class="tp-kpi-fact">' + escapeHtml(value || "—") + "</div>"
        + '</div><div class="tp-kpi-icon"><i class="' + icon + '"></i></div>'
        + "</div></div></div>";
}

function section(title, icon, body) {
    return '<div class="card mb-3"><div class="card-body tp-section">'
        + '<h5 class="header-table mb-3"><i class="' + icon + ' mr-2"></i>' + escapeHtml(title) + "</h5>"
        + body + "</div></div>";
}

function timingTable(header) {
    const late = header.result === "Late";
    return '<p class="text-muted small mb-3">Late when Fecha Entrega is after the retained date, or a Delivery Timing case exists.</p>'
        + '<div class="tp-dates">'
        + dateTile("Promised date", formatDate(header.promisedDate), "Date promised to the customer")
        + dateTile("Transmit (EDI)", formatDateTime(header.transmitDateTime), "Earliest inbound message")
        + dateTile("Effective Warehouse arrival", formatDate(header.warehouseArrival), "After 13:30 this rolls to the next working day")
        + dateTile("Earliest viable", formatDate(header.earliestViableDate), "Arrival plus transit days")
        + dateTile("Retained date", formatDate(header.comparisonDate), "Later of promised and earliest viable", "tp-date-key")
        + dateTile("Carrier Delivery Date", formatDate(header.fechaEntrega), "Latest import of this pick", late ? "tp-date-late" : "")
        + dateTile("Days late", header.daysLate == null ? "" : String(header.daysLate), "Zero when the pick is on time", late ? "tp-date-late" : "")
        + dateTile("Timing NCR", header.timingNcr ? "Yes" : "No", "A Delivery Timing case on this order", header.timingNcr ? "tp-date-late" : "")
        + "</div>";
}

function dateTile(label, value, note, extra) {
    return '<div class="tp-date ' + (extra || "") + '">'
        + '<div class="tp-date-label">' + escapeHtml(label) + "</div>"
        + '<div class="tp-date-value">' + escapeHtml(value || "—") + "</div>"
        + (note ? '<div class="tp-date-note">' + escapeHtml(note) + "</div>" : "")
        + "</div>";
}

function linesTable(rows) {
    if (!rows.length) {
        return '<div class="text-muted">No sales lines for this pick.</div>';
    }
    return table(["Product", "Full Segments Number", "Order", "Promised", "Weight (kg)", "Income type"], rows.map(function (row) {
        const name = row.productName || "";
        return [
            name,
            row.segmentNumber,
            row.orderNumber,
            formatDate(row.promisedDate),
            formatKg(row.quantityKg),
            row.incomeType
        ];
    }));
}

function carrierTable(rows) {
    if (!rows.length) {
        return '<div class="text-muted">No carrier rows for this pick.</div>';
    }
    return table(
        ["Pick number", "Imported", "File identifier", "Code", "Status", "SKU", "Fecha Entrega", "Client", "Address", "Locality", "Postal code", "Country"],
        rows.map(function (row) {
            return [
                row.pickNumber,
                formatDateTime(row.importDate),
                row.fileIdentifier,
                row.carrierCode,
                row.status,
                row.sku,
                formatDate(row.fechaEntrega),
                row.clientName,
                row.address,
                row.locality,
                row.postalCode,
                row.country
            ];
        })
    );
}

function ediTable(rows) {
    if (!rows.length) {
        return '<div class="text-muted">No EDI messages for this order.</div>';
    }
    return table(["Datetime", "Source", "Reporting date", "Country", "Record type", "Processed"], rows.map(function (row) {
        return [
            formatDateTime(row.ediDateTime),
            row.source,
            formatDate(row.reportingDate),
            row.countryCode,
            row.recordType,
            row.processed
        ];
    }));
}

function casesTable(rows) {
    if (!rows.length) {
        return '<div class="text-muted">No Delivery Timing cases for this order.</div>';
    }
    const body = rows.map(function (row) {
        const extra = [row.carrierName ? "Carrier: " + row.carrierName : "", row.cause ? "Cause: " + row.cause : "", row.description || ""]
            .filter(Boolean)
            .join(" · ");
        return "<tr>"
            + cell(row.caseNumber)
            + cell(row.caseStatus)
            + cell(row.attributed)
            + cell(formatDate(row.openedDate))
            + cell(formatDate(row.closedDate))
            + "<td>" + escapeHtml(row.subject || row.reason || "")
            + (extra ? '<div class="tp-date-note">' + escapeHtml(extra) + "</div>" : "")
            + "</td></tr>";
    }).join("");
    return '<div class="table-responsive"><table class="table table-striped table-hover mb-0 tp-deliveries"><thead><tr>'
        + ["Case", "Status", "Attributed", "Opened", "Closed", "Subject"].map(function (name) {
            return "<th>" + name + "</th>";
        }).join("")
        + "</tr></thead><tbody>" + body + "</tbody></table></div>";
}

function table(headers, rows) {
    return '<div class="table-responsive"><table class="table table-striped table-hover mb-0 tp-deliveries"><thead><tr>'
        + headers.map(function (name) { return "<th>" + escapeHtml(name) + "</th>"; }).join("")
        + "</tr></thead><tbody>"
        + rows.map(function (row) {
            return "<tr>" + row.map(cell).join("") + "</tr>";
        }).join("")
        + "</tbody></table></div>";
}

function cell(value) {
    return "<td>" + escapeHtml(value) + "</td>";
}

function formatKg(value) {
    if (value == null || value === "") {
        return "";
    }
    const number = Number(value);
    if (!Number.isFinite(number)) {
        return "";
    }
    return Math.round(number).toLocaleString("es-ES", {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    });
}

function formatDate(value) {
    if (!value) {
        return "";
    }
    const parts = String(value).substring(0, 10).split("-");
    if (parts.length !== 3) {
        return String(value);
    }
    return parts[2] + "/" + parts[1] + "/" + parts[0];
}

function formatDateTime(value) {
    if (!value) {
        return "";
    }
    const text = String(value);
    const time = text.length >= 16 ? text.substring(11, 16) : "";
    return time ? formatDate(text) + " " + time : formatDate(text);
}

function escapeHtml(value) {
    return String(value == null ? "" : value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}
