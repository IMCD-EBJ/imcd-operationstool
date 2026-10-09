let currentPage = 1;
let cancelledPage = 1;
let unmatchedPage = 1;
let sortColumn = "fechaEntrega";
let sortDirection = "desc";
let onTimeChart = null;
let selectedProducts = [];
let selectedAccounts = [];
let selectedFiles = [];
let fileIdentifiers = [];
let fileIdentifiersLoaded = false;
let productSearchTimer = null;
let accountSearchTimer = null;

window.onOperationsMenuLoaded = function () {
    const link = document.querySelector("#sidebar a[href='dashboard.html']");
    if (link) {
        link.classList.add("active");
    }
};

$(function () {
    loadOperationsChrome();
    applyDefaultDates();
    updateSortIcons();
    loadFilters();
    loadDashboard();

    $(document).on("click", ".tp-sort", function () {
        const column = $(this).data("sort");
        if (sortColumn === column) {
            sortDirection = sortDirection === "asc" ? "desc" : "asc";
        } else {
            sortColumn = column;
            sortDirection = column === "fechaEntrega" || column === "promisedDate"
                || column === "transmitDate" || column === "earliestViableDate" || column === "daysLate"
                || column === "weight"
                ? "desc"
                : "asc";
        }
        currentPage = 1;
        cancelledPage = 1;
        updateSortIcons();
        loadDeliveries();
    });

    $("#btnApply").on("click", function () {
        currentPage = 1;
        cancelledPage = 1;
        unmatchedPage = 1;
        loadDashboard(true);
    });

    $("#btnClear").on("click", function () {
        applyDefaultDates();
        $("#accountOwner, #carrierZone, #pickNumber, #orderNumber, #weightBand").val("");
        $("#result").val("All");
        $("#pageSize").val("10");
        $("#cancelledPageSize, #unmatchedPageSize").val("10");
        sortColumn = "fechaEntrega";
        sortDirection = "desc";
        updateSortIcons();
        clearSearch("#productSearch", "#productSuggestions", "product");
        clearSearch("#accountSearch", "#accountSuggestions", "account");
        clearFiles();
        currentPage = 1;
        cancelledPage = 1;
        unmatchedPage = 1;
        loadDashboard();
    });

    $("#pageSize").on("change", function () {
        currentPage = 1;
        loadDeliveries();
    });

    $("#cancelledPageSize").on("change", function () {
        cancelledPage = 1;
        loadDeliveries(false);
    });

    $("#unmatchedPageSize").on("change", function () {
        unmatchedPage = 1;
        loadUnmatched();
    });

    $("#btnExport").on("click", exportExcel);

    bindSearch("#productSearch", "#productSuggestions", "product");
    bindSearch("#accountSearch", "#accountSuggestions", "account");
    bindFilePicker();

    $(document).on("click", function (event) {
        if (!$(event.target).closest(".tp-search").length) {
            $(".tp-suggestions").attr("hidden", true);
        }
        if (!$(event.target).closest(".tp-file-menu").length) {
            closeFileMenu();
        }
    });
});

function applyDefaultDates() {
    const range = defaultRange();
    $("#fromDate").val(range.from);
    $("#toDate").val(range.to);
}

function defaultRange() {
    const to = new Date();
    const from = new Date(to.getFullYear(), to.getMonth() - 3, to.getDate());
    return { from: toInputDate(from), to: toInputDate(to) };
}

function toInputDate(date) {
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return date.getFullYear() + "-" + month + "-" + day;
}

function filterQuery() {
    const query = {
        fromDate: $("#fromDate").val(),
        toDate: $("#toDate").val(),
        accountOwner: $("#accountOwner").val(),
        carrierZone: $("#carrierZone").val(),
        result: $("#result").val(),
        pickNumber: $("#pickNumber").val(),
        orderNumber: $("#orderNumber").val(),
        weightBand: $("#weightBand").val(),
        sortColumn: sortColumn,
        sortDirection: sortDirection
    };
    if (selectedProducts.length) {
        query.products = JSON.stringify(selectedProducts.map(function (product) {
            return {
                n: product.productName || "",
                s: product.segmentNumber || ""
            };
        }));
    }
    if (selectedAccounts.length) {
        query.accounts = JSON.stringify(selectedAccounts);
    }
    if (selectedFiles.length) {
        query.files = JSON.stringify(selectedFiles);
    }
    return query;
}

function loadFilters() {
    $.get(URLBACKEND + "transport-performance/account-owners")
        .done(function (owners) {
            fillSelect("#accountOwner", "All owners", owners);
        })
        .fail(function () {
            showPageAlert("Account owners could not be loaded.");
        });

    $.get(URLBACKEND + "transport-performance/zones")
        .done(function (zones) {
            fillSelect("#carrierZone", "All zones", zones);
        })
        .fail(function () {
            showPageAlert("Postal codes could not be loaded.");
        });

    $.get(URLBACKEND + "transport-performance/file-identifiers")
        .done(function (files) {
            fileIdentifiers = files || [];
            fileIdentifiersLoaded = true;
            renderFileMenu();
        })
        .fail(function () {
            fileIdentifiers = [];
            fileIdentifiersLoaded = true;
            renderFileMenu();
            showPageAlert("Import files could not be loaded.");
        });
}

function bindSearch(inputSelector, listSelector, kind) {
    $(inputSelector).on("input", function () {
        const term = $(this).val().trim();
        window.clearTimeout(kind === "product" ? productSearchTimer : accountSearchTimer);
        if (term.length < 2) {
            $(listSelector).attr("hidden", true).empty();
            return;
        }
        const timer = window.setTimeout(function () {
            searchSuggestions(kind, term, listSelector);
        }, 300);
        if (kind === "product") {
            productSearchTimer = timer;
        } else {
            accountSearchTimer = timer;
        }
    });

    $(inputSelector).on("keydown", function (event) {
        if (event.key === "Escape") {
            $(listSelector).attr("hidden", true);
        }
        if (event.key === "Backspace" && $(this).val() === "") {
            const items = kind === "product" ? selectedProducts : selectedAccounts;
            if (items.length) {
                items.pop();
                renderChips(kind);
            }
        }
    });
}

function searchSuggestions(kind, term, listSelector) {
    const url = kind === "product"
        ? URLBACKEND + "transport-performance/products"
        : URLBACKEND + "transport-performance/accounts";
    $.get(url, { term: term })
        .done(function (rows) {
            const current = $(kind === "product" ? "#productSearch" : "#accountSearch").val().trim();
            if (current !== term) {
                return;
            }
            renderSuggestions(kind, rows || [], listSelector);
        })
        .fail(function () {
            $(listSelector).attr("hidden", true).empty();
        });
}

function renderSuggestions(kind, rows, listSelector) {
    const list = $(listSelector);
    list.empty();
    const available = (rows || []).filter(function (row) {
        return !isSelected(kind, row);
    });
    if (!available.length) {
        list.append('<div class="tp-suggestion-empty">No matches</div>');
        list.removeAttr("hidden");
        return;
    }
    available.forEach(function (row) {
        const button = $("<button type='button'></button>");
        if (kind === "product") {
            button.text(productLabel(row));
            button.on("click", function () {
                addProduct(row);
                list.attr("hidden", true).empty();
            });
        } else {
            button.text(row);
            button.on("click", function () {
                addAccount(row);
                list.attr("hidden", true).empty();
            });
        }
        list.append(button);
    });
    list.removeAttr("hidden");
}

function addProduct(row) {
    const item = {
        segmentNumber: row.segmentNumber || "",
        productName: row.productName || ""
    };
    if (isSelected("product", item)) {
        return;
    }
    window.clearTimeout(productSearchTimer);
    selectedProducts.push(item);
    $("#productSearch").val("").trigger("focus");
    renderChips("product");
}

function addAccount(name) {
    if (!name || isSelected("account", name)) {
        return;
    }
    window.clearTimeout(accountSearchTimer);
    selectedAccounts.push(name);
    $("#accountSearch").val("").trigger("focus");
    renderChips("account");
}

function isSelected(kind, row) {
    if (kind === "product") {
        const key = productKey(row);
        return selectedProducts.some(function (item) { return productKey(item) === key; });
    }
    return selectedAccounts.indexOf(row) !== -1;
}

function productKey(item) {
    return (item.segmentNumber || "") + "\u001f" + (item.productName || "");
}

function renderChips(kind) {
    const isProduct = kind === "product";
    const host = $(isProduct ? "#productChips" : "#accountChips");
    const items = isProduct ? selectedProducts : selectedAccounts;
    const input = $(isProduct ? "#productSearch" : "#accountSearch");
    host.empty();
    items.forEach(function (item, index) {
        const labelText = isProduct ? productLabel(item) : item;
        const chip = $('<span class="tp-chip"></span>');
        const label = $('<span class="tp-chip-label"></span>').text(labelText).attr("title", labelText);
        const remove = $('<button type="button" class="tp-chip-remove" aria-label="Remove">&times;</button>');
        remove.on("click", function () {
            items.splice(index, 1);
            renderChips(kind);
            input.trigger("focus");
        });
        chip.append(label, remove);
        host.append(chip);
    });
    if (items.length) {
        input.attr("placeholder", isProduct ? "Add another product" : "Add another account");
    } else {
        input.attr("placeholder", isProduct ? "Search by name or segment" : "Search account name");
    }
}

function productLabel(item) {
    const segment = item.segmentNumber || "";
    const name = item.productName || "";
    if (segment && name && segment !== name) {
        return name + " (" + segment + ")";
    }
    return name || segment;
}

function clearSearch(inputSelector, listSelector, kind) {
    $(inputSelector).val("");
    $(listSelector).attr("hidden", true).empty();
    if (kind === "product") {
        selectedProducts = [];
    } else {
        selectedAccounts = [];
    }
    renderChips(kind);
}

function bindFilePicker() {
    $("#fileMenuButton").on("click", function () {
        if ($("#fileMenu").is("[hidden]")) {
            $("#fileMenu").removeAttr("hidden");
            $(this).attr("aria-expanded", "true");
        } else {
            closeFileMenu();
        }
    });

    $("#fileMenuButton").on("keydown", function (event) {
        if (event.key === "Escape") {
            closeFileMenu();
        }
    });
}

function renderFileMenu() {
    const list = $("#fileMenu");
    list.empty();
    if (!fileIdentifiers.length) {
        list.append('<div class="tp-file-empty">'
            + (fileIdentifiersLoaded ? "No import files" : "Loading import files…")
            + "</div>");
        updateFileMenuLabel();
        return;
    }
    fileIdentifiers.forEach(function (identifier) {
        const option = $('<label class="tp-file-option"></label>');
        const box = $('<input type="checkbox">').val(identifier);
        box.prop("checked", selectedFiles.indexOf(identifier) !== -1);
        box.on("change", function () {
            if (this.checked) {
                if (selectedFiles.indexOf(identifier) === -1) {
                    selectedFiles.push(identifier);
                }
            } else {
                selectedFiles = selectedFiles.filter(function (item) {
                    return item !== identifier;
                });
            }
            updateFileMenuLabel();
        });
        option.append(box, $("<span></span>").text(identifier));
        list.append(option);
    });
    updateFileMenuLabel();
}

function updateFileMenuLabel() {
    const text = selectedFiles.length ? selectedFiles.join(", ") : "All import files";
    $("#fileMenuLabel").text(text);
    $("#fileMenuButton").attr("title", text);
}

function closeFileMenu() {
    $("#fileMenu").attr("hidden", true);
    $("#fileMenuButton").attr("aria-expanded", "false");
}

function clearFiles() {
    selectedFiles = [];
    $("#fileMenu input[type=checkbox]").prop("checked", false);
    updateFileMenuLabel();
    closeFileMenu();
}

function fillSelect(selector, placeholder, values) {
    const select = $(selector);
    const current = select.val();
    select.empty();
    select.append($("<option>", { value: "", text: placeholder }));
    (values || []).forEach(function (value) {
        select.append($("<option>", { value: value, text: value }));
    });
    if (current) {
        select.val(current);
    }
}

function updateSortIcons() {
    $(".tp-sort").each(function () {
        const column = $(this).data("sort");
        const icon = $(this).find(".tp-sort-icon");
        $(this).removeClass("is-active");
        icon.attr("class", "fas fa-sort tp-sort-icon");
        if (column === sortColumn) {
            $(this).addClass("is-active");
            icon.attr("class", "fas tp-sort-icon " + (sortDirection === "asc" ? "fa-sort-up" : "fa-sort-down"));
        }
    });
}

function loadDashboard(recordFilters) {
    const fromDate = $("#fromDate").val();
    const toDate = $("#toDate").val();
    if (fromDate && toDate && fromDate > toDate) {
        showPageAlert("From date must be on or before to date.");
        return;
    }
    clearPageAlert();
    loadDeliveries(true, !!recordFilters);
    loadUnmatched();
}

function loadDeliveries(refreshChart, recordFilters) {
    $("#btnApply").prop("disabled", true);
    $("#deliveriesBody").html('<tr><td colspan="14" class="text-muted">Loading…</td></tr>');

    const query = filterQuery();
    if (recordFilters) {
        query.recordFilters = true;
    }
    query.page = currentPage;
    query.pageSize = Number($("#pageSize").val()) || 10;
    query.cancelledPage = cancelledPage;
    query.cancelledPageSize = Number($("#cancelledPageSize").val()) || 10;
    $("#cancelledBody").html('<tr><td colspan="14" class="text-muted">Loading…</td></tr>');

    $.ajax({
        url: URLBACKEND + "transport-performance/consult",
        data: query,
        traditional: true
    })
        .done(function (data) {
            if (refreshChart) {
                renderSummary(data.summary || {});
                renderChart(data.months || [], data.summary || {});
            }
            renderTable(data.rows || [], data.total || 0, query.pageSize);
            renderCancelled(data.cancelledRows || [], data.cancelledTotal || 0, query.cancelledPageSize);
        })
        .fail(function () {
            if (refreshChart) {
                renderSummary({});
                renderChart([], {});
            }
            $("#deliveriesBody").html('<tr><td colspan="14" class="text-danger">Deliveries could not be loaded.</td></tr>');
            $("#showingLabel").text("");
            $("#pager").empty();
            $("#cancelledBody").html('<tr><td colspan="14" class="text-danger">Cancelled picks could not be loaded.</td></tr>');
            $("#cancelledShowingLabel").text("");
            $("#cancelledPager").empty();
            showPageAlert("The dashboard could not be loaded.");
        })
        .always(function () {
            $("#btnApply").prop("disabled", false);
        });
}

function loadUnmatched() {
    $("#unmatchedBody").html('<tr><td colspan="7" class="text-muted">Loading…</td></tr>');
    const pageSize = Number($("#unmatchedPageSize").val()) || 10;
    const query = filterQuery();
    $.get(URLBACKEND + "transport-performance/unmatched", {
        fromDate: query.fromDate,
        toDate: query.toDate,
        pickNumber: query.pickNumber,
        files: query.files,
        page: unmatchedPage,
        pageSize: pageSize
    })
        .done(function (data) {
            renderUnmatched(data.rows || [], data.total || 0, pageSize);
        })
        .fail(function () {
            $("#unmatchedBody").html('<tr><td colspan="7" class="text-danger">Unmatched picks could not be loaded.</td></tr>');
            $("#unmatchedShowingLabel").text("");
            $("#unmatchedPager").empty();
            showPageAlert("Picks without a sales order could not be loaded.");
        });
}

function renderUnmatched(rows, total, pageSize) {
    const body = $("#unmatchedBody");
    body.empty();
    if (!rows.length) {
        body.append('<tr><td colspan="7" class="text-muted">Every pick in this period matches a sales order.</td></tr>');
    } else {
        rows.forEach(function (row) {
            body.append(
                "<tr>"
                + cell(row.pickNumber)
                + cell(row.searchedNumber)
                + cell(row.carrierCode)
                + cell(formatDate(row.fechaEntrega))
                + cell(row.clientName)
                + cell(row.postalCode)
                + cell(row.country)
                + "</tr>"
            );
        });
    }

    if (total === 0) {
        $("#unmatchedShowingLabel").text("Showing 0 of 0");
    } else {
        const start = (unmatchedPage - 1) * pageSize + 1;
        const end = Math.min(unmatchedPage * pageSize, total);
        $("#unmatchedShowingLabel").text(
            "Showing " + formatInteger(start) + "-" + formatInteger(end) + " of " + formatInteger(total)
        );
    }
    renderUnmatchedPager(total, pageSize);
}

function renderUnmatchedPager(total, pageSize) {
    const pages = Math.max(1, Math.ceil(total / pageSize));
    if (total > 0 && unmatchedPage > pages) {
        unmatchedPage = pages;
        loadUnmatched();
        return;
    }
    const pager = $('<ul class="pagination pagination-sm mb-0"></ul>');
    pager.append(pageItem("Previous", unmatchedPage <= 1, function () {
        unmatchedPage -= 1;
        loadUnmatched();
    }));

    const windowStart = Math.max(1, unmatchedPage - 2);
    const windowEnd = Math.min(pages, windowStart + 4);
    for (let page = windowStart; page <= windowEnd; page++) {
        const item = pageItem(String(page), false, function () {
            unmatchedPage = page;
            loadUnmatched();
        });
        if (page === unmatchedPage) {
            item.addClass("active");
        }
        pager.append(item);
    }

    pager.append(pageItem("Next", unmatchedPage >= pages || total === 0, function () {
        unmatchedPage += 1;
        loadUnmatched();
    }));
    $("#unmatchedPager").empty().append(pager);
}

function renderSummary(summary) {
    $("#kpiTotal").text(formatInteger(summary.totalCount));
    $("#kpiOnTime").text(formatPercent(summary.onTimePercent));
    $("#kpiOnTimeCount").text(formatInteger(summary.onTimeCount) + " deliveries");
    $("#kpiLate").text(formatPercent(summary.latePercent));
    $("#kpiLateCount").text(formatInteger(summary.lateCount) + " deliveries");
    $("#kpiNcr").text(formatInteger(summary.timingNcrCount));
}

function renderChart(months, summary) {
    if (onTimeChart) {
        onTimeChart.destroy();
        onTimeChart = null;
    }
    const host = document.getElementById("onTimeChart");
    host.innerHTML = "";
    if (!months.length) {
        host.innerHTML = '<div class="text-muted">No deliveries in this period.</div>';
        return;
    }

    const categories = months.map(function (month) { return month.monthLabel; });
    const percents = months.map(function (month) {
        return month.onTimePercent === null || month.onTimePercent === undefined
            ? null
            : Number(month.onTimePercent);
    });
    const target = summary.targetPercentage === null || summary.targetPercentage === undefined
        ? null
        : Number(summary.targetPercentage);
    const targets = categories.map(function () { return target; });
    const plotted = percents.filter(function (value) { return value !== null && !isNaN(value); });
    if (target !== null && !isNaN(target)) {
        plotted.push(target);
    }
    const lowest = plotted.length ? Math.min.apply(null, plotted) : 0;
    const yMin = lowest >= 90 ? 90 : Math.max(0, Math.floor(lowest / 10) * 10);

    onTimeChart = new ApexCharts(host, {
        chart: {
            type: "line",
            height: 320,
            toolbar: { show: false },
            fontFamily: "system-ui, Segoe UI, Arial, sans-serif"
        },
        series: [
            { name: "On time", type: "column", data: percents },
            { name: "Target", type: "line", data: targets }
        ],
        colors: ["#1f6df0", "#cc8b00"],
        stroke: { width: [0, 2], dashArray: [0, 6] },
        plotOptions: { bar: { columnWidth: "42%", borderRadius: 3 } },
        dataLabels: {
            enabled: true,
            enabledOnSeries: [0],
            formatter: function (value) {
                return value === null || value === undefined || isNaN(value) ? "" : formatDecimal(value, 2) + "%";
            },
            offsetY: -18,
            style: { fontSize: "11px", colors: ["#0a2b6b"] }
        },
        xaxis: { categories: categories },
        yaxis: {
            min: yMin,
            max: 100,
            labels: {
                formatter: function (value) { return formatDecimal(value, 2) + "%"; }
            }
        },
        legend: { position: "top" },
        grid: { borderColor: "#e6eefc" },
        tooltip: {
            y: {
                formatter: function (value) {
                    return value === null || value === undefined || isNaN(value) ? "—" : formatDecimal(value, 2) + "%";
                }
            }
        }
    });
    onTimeChart.render();
}

function renderTable(rows, total, pageSize) {
    const body = $("#deliveriesBody");
    body.empty();
    if (!rows.length) {
        body.append('<tr><td colspan="14" class="text-muted">No deliveries match these filters.</td></tr>');
    } else {
        rows.forEach(function (row) {
            body.append(deliveryRow(row));
        });
    }

    if (total === 0) {
        $("#showingLabel").text("Showing 0 of 0");
    } else {
        const start = (currentPage - 1) * pageSize + 1;
        const end = Math.min(currentPage * pageSize, total);
        $("#showingLabel").text(
            "Showing " + formatInteger(start) + "-" + formatInteger(end) + " of " + formatInteger(total)
        );
    }
    renderPager(total, pageSize);
}

function renderCancelled(rows, total, pageSize) {
    const body = $("#cancelledBody");
    body.empty();
    if (!rows.length) {
        body.append('<tr><td colspan="14" class="text-muted">No picks with all cancelled lines match these filters.</td></tr>');
    } else {
        rows.forEach(function (row) {
            body.append(deliveryRow(row));
        });
    }
    if (total === 0) {
        $("#cancelledShowingLabel").text("Showing 0 of 0");
    } else {
        const start = (cancelledPage - 1) * pageSize + 1;
        const end = Math.min(cancelledPage * pageSize, total);
        $("#cancelledShowingLabel").text(
            "Showing " + formatInteger(start) + "-" + formatInteger(end) + " of " + formatInteger(total)
        );
    }
    renderCancelledPager(total, pageSize);
}

function renderCancelledPager(total, pageSize) {
    const pages = Math.max(1, Math.ceil(total / pageSize));
    if (total > 0 && cancelledPage > pages) {
        cancelledPage = pages;
        loadDeliveries(false);
        return;
    }
    const pager = $('<ul class="pagination pagination-sm mb-0"></ul>');
    pager.append(pageItem("Previous", cancelledPage <= 1, function () {
        cancelledPage -= 1;
        loadDeliveries(false);
    }));
    const windowStart = Math.max(1, cancelledPage - 2);
    const windowEnd = Math.min(pages, windowStart + 4);
    for (let page = windowStart; page <= windowEnd; page++) {
        const item = pageItem(String(page), false, function () {
            cancelledPage = page;
            loadDeliveries(false);
        });
        if (page === cancelledPage) {
            item.addClass("active");
        }
        pager.append(item);
    }
    pager.append(pageItem("Next", cancelledPage >= pages || total === 0, function () {
        cancelledPage += 1;
        loadDeliveries(false);
    }));
    $("#cancelledPager").empty().append(pager);
}

function deliveryRow(row) {
    const result = row.result
        ? '<span class="tp-pill ' + (row.result === "Late" ? "tp-pill-late" : "tp-pill-ontime") + '">'
            + escapeHtml(row.result) + "</span>"
        : "";
    const daysLate = row.daysLate === null || row.daysLate === undefined
        ? ""
        : escapeHtml(formatInteger(row.daysLate));
    const pickWeight = row.pickWeightKg === null || row.pickWeightKg === undefined
        ? ""
        : escapeHtml(formatInteger(row.pickWeightKg));
    const fechaClass = row.result === "Late" && !row.ncrOnly ? " tp-late-date" : "";
    return "<tr>"
        + cell(row.pickNumber)
        + cell(row.orderNumber)
        + '<td class="tp-col-product">' + escapeHtml(row.product) + "</td>"
        + cell(row.accountName)
        + cell(row.accountOwner)
        + cell(formatDate(row.promisedDate))
        + cell(formatDateTime(row.transmitDateTime))
        + '<td class="tp-col-compact">' + escapeHtml(formatDate(row.earliestViableDate)) + "</td>"
        + '<td class="tp-col-compact' + fechaClass + '">' + escapeHtml(formatDate(row.fechaEntrega)) + "</td>"
        + '<td class="tp-col-tight">' + (row.timingNcr ? '<span class="tp-ncr-yes">Yes</span>' : "No") + "</td>"
        + '<td class="tp-col-tight">' + daysLate + "</td>"
        + '<td class="tp-col-compact">' + pickWeight + "</td>"
        + "<td>" + result + "</td>"
        + "<td class=\"tp-col-eye\">" + pickDetailsLink(row.pickNumber) + "</td>"
        + "</tr>";
}

function renderPager(total, pageSize) {
    const pages = Math.max(1, Math.ceil(total / pageSize));
    if (total > 0 && currentPage > pages) {
        currentPage = pages;
        loadDeliveries();
        return;
    }
    const pager = $('<ul class="pagination pagination-sm mb-0"></ul>');
    pager.append(pageItem("Previous", currentPage <= 1, function () {
        currentPage -= 1;
        loadDeliveries(false);
    }));

    const windowStart = Math.max(1, currentPage - 2);
    const windowEnd = Math.min(pages, windowStart + 4);
    for (let page = windowStart; page <= windowEnd; page++) {
        const item = pageItem(String(page), false, function () {
            currentPage = page;
            loadDeliveries(false);
        });
        if (page === currentPage) {
            item.addClass("active");
        }
        pager.append(item);
    }

    pager.append(pageItem("Next", currentPage >= pages || total === 0, function () {
        currentPage += 1;
        loadDeliveries(false);
    }));
    $("#pager").empty().append(pager);
}

function pageItem(label, disabled, onClick) {
    const item = $('<li class="page-item"></li>');
    const link = $('<a class="page-link" href="#"></a>').text(label);
    if (disabled) {
        item.addClass("disabled");
    } else {
        link.on("click", function (event) {
            event.preventDefault();
            onClick();
        });
    }
    return item.append(link);
}

function cell(value) {
    return "<td>" + escapeHtml(value) + "</td>";
}

function pickDetailsLink(pickNumber) {
    const match = String(pickNumber || "").trim().match(/^\d+/);
    if (!match) {
        return "";
    }
    const pick = String(parseInt(match[0], 10));
    return '<a class="tp-eye" href="pick/' + pick + '/details" title="Pick details">'
        + '<i class="fas fa-eye"></i></a>';
}

function exportExcel() {
    clearPageAlert();
    const fromDate = $("#fromDate").val();
    const toDate = $("#toDate").val();
    if (fromDate && toDate && fromDate > toDate) {
        showPageAlert("From date must be on or before to date.");
        return;
    }
    $("#btnExport").prop("disabled", true);
    fetch(URLBACKEND + "transport-performance/export?" + $.param(filterQuery(), true), { credentials: "same-origin" })
        .then(function (response) {
            if (!response.ok) {
                throw new Error("export failed");
            }
            const disposition = response.headers.get("Content-Disposition") || "";
            const match = disposition.match(/filename="([^"]+)"/);
            const fileName = match ? match[1] : "TransportPerformance.xlsx";
            return response.blob().then(function (blob) { return { blob: blob, fileName: fileName }; });
        })
        .then(function (file) {
            const url = URL.createObjectURL(file.blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = file.fileName;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        })
        .catch(function () {
            showPageAlert("The Excel file could not be downloaded.");
        })
        .finally(function () {
            $("#btnExport").prop("disabled", false);
        });
}

function formatInteger(value) {
    if (value === null || value === undefined || String(value).trim() === "" || isNaN(Number(value))) {
        return "—";
    }
    return Number(value).toLocaleString("es-ES", {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    });
}

function formatDecimal(value, decimals) {
    if (value === null || value === undefined || String(value).trim() === "" || isNaN(Number(value))) {
        return "—";
    }
    return Number(value).toLocaleString("es-ES", {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals
    });
}

function formatPercent(value) {
    if (value === null || value === undefined || String(value).trim() === "" || isNaN(Number(value))) {
        return "—";
    }
    return formatDecimal(value, 2) + "%";
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

function showPageAlert(message) {
    $("#pageAlert").html(
        '<div class="alert alert-danger" role="alert">' + escapeHtml(message) + "</div>"
    );
}

function clearPageAlert() {
    $("#pageAlert").empty();
}
