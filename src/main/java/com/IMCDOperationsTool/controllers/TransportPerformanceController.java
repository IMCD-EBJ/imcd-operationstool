package com.IMCDOperationsTool.controllers;

import com.imcd.platformlib.excel.ExcelExportService;
import com.imcd.platformlib.excel.model.ExcelColumnType;
import com.imcd.platformlib.excel.model.ExcelExportRequest;
import com.imcd.platformlib.excel.model.ExcelExportResult;
import com.imcd.platformlib.excel.model.ExcelLabelValue;
import com.imcd.platformlib.excel.model.ExcelMetadataSection;
import com.imcd.platformlib.excel.model.ExcelSheetRequest;
import com.imcd.platformlib.excel.model.ExcelTableSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.CallableStatement;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transport Performance Dashboard. Matching, dates, and KPIs come from
 * {@code dbo.TransportPerformance_Consult}.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/transport-performance/")
public class TransportPerformanceController {

    private static final Logger LOG = LoggerFactory.getLogger(TransportPerformanceController.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbcTemplate;
    private final ExcelExportService excelExportService;

    public TransportPerformanceController(JdbcTemplate jdbcTemplate, ExcelExportService excelExportService) {
        this.jdbcTemplate = jdbcTemplate;
        this.excelExportService = excelExportService;
    }

    @GetMapping("account-owners")
    public ResponseEntity<Object> accountOwners() {
        return stringList("{call dbo.TransportPerformance_AccountOwners_Consult()}", "AccountOwner");
    }

    @GetMapping("zones")
    public ResponseEntity<Object> zones() {
        return stringList("{call dbo.TransportPerformance_Zones_Consult()}", "CarrierZone");
    }

    @GetMapping("products")
    public ResponseEntity<Object> products(@RequestParam(defaultValue = "") String term) {
        if (term == null || term.trim().length() < 2) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            jdbcTemplate.query(
                    "{call dbo.TransportPerformance_Product_Search(?)}",
                    ps -> ps.setString(1, term.trim()),
                    rs -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("segmentNumber", rs.getString("SegmentNumber"));
                        row.put("productName", rs.getString("ProductName"));
                        rows.add(row);
                    });
            return new ResponseEntity<>(rows, HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.error("Transport performance product search failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("accounts")
    public ResponseEntity<Object> accounts(@RequestParam(defaultValue = "") String term) {
        if (term == null || term.trim().length() < 2) {
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
        try {
            List<String> names = new ArrayList<>();
            jdbcTemplate.query(
                    "{call dbo.TransportPerformance_Account_Search(?)}",
                    ps -> ps.setString(1, term.trim()),
                    rs -> {
                        String name = rs.getString("AccountName");
                        if (name != null && !name.isBlank()) {
                            names.add(name);
                        }
                    });
            return new ResponseEntity<>(names, HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.error("Transport performance account search failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("consult")
    public ResponseEntity<Object> consult(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productSegment,
            @RequestParam(required = false) String accountOwner,
            @RequestParam(required = false) String accountName,
            @RequestParam(required = false) String carrierZone,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String pickNumber,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            DashboardData data = load(fromDate, toDate, productName, productSegment, accountOwner, accountName,
                    carrierZone, result, pickNumber, orderNumber, sortColumn, sortDirection, page, pageSize);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("summary", data.summary);
            body.put("months", data.months);
            body.put("rows", data.rows);
            body.put("total", data.total);
            return new ResponseEntity<>(body, HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.error("Transport performance consult failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("unmatched")
    public ResponseEntity<Object> unmatched(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String pickNumber,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            return new ResponseEntity<>(loadUnmatched(fromDate, toDate, pickNumber, page, pageSize), HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.error("Transport performance unmatched consult failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("picks/{pick}")
    public ResponseEntity<Object> pick(@PathVariable int pick) {
        try {
            Map<String, Object> body = loadPick(pick);
            if (body == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(body, HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.error("Transport performance pick consult failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("export")
    public ResponseEntity<Object> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productSegment,
            @RequestParam(required = false) String accountOwner,
            @RequestParam(required = false) String accountName,
            @RequestParam(required = false) String carrierZone,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String pickNumber,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(required = false) String sortDirection) {
        try {
            DashboardData data = load(fromDate, toDate, productName, productSegment, accountOwner, accountName,
                    carrierZone, result, pickNumber, orderNumber, sortColumn, sortDirection, 1, 0);
            ExcelExportResult file = excelExportService.export(workbook(
                    fromDate, toDate, productName, productSegment, accountOwner, accountName, carrierZone, result,
                    pickNumber, orderNumber, data));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .body(file.content());
        } catch (RuntimeException e) {
            LOG.error("Transport performance export failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            LOG.error("Transport performance export failed", e);
            return new ResponseEntity<>("The Excel file could not be created", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ResponseEntity<Object> stringList(String call, String column) {
        try {
            List<String> values = new ArrayList<>();
            jdbcTemplate.query(call, rs -> {
                String value = rs.getString(column);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            });
            return new ResponseEntity<>(values, HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.error("Transport performance lookup failed", e);
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private DashboardData load(LocalDate fromDate, LocalDate toDate, String productName, String productSegment,
                               String accountOwner, String accountName, String carrierZone, String result,
                               String pickNumber, String orderNumber, String sortColumn, String sortDirection,
                               int page, int pageSize) {
        return jdbcTemplate.execute((ConnectionCallback<DashboardData>) connection -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{call dbo.TransportPerformance_Consult(?,?,?,?,?,?,?,?,?,?,?,?,?,?)}")) {
                setDate(cs, 1, fromDate);
                setDate(cs, 2, toDate);
                setString(cs, 3, productName);
                setString(cs, 4, productSegment);
                setString(cs, 5, accountOwner);
                setString(cs, 6, accountName);
                setString(cs, 7, carrierZone);
                setString(cs, 8, result);
                setString(cs, 9, sortColumn(sortColumn));
                setString(cs, 10, sortDirection(sortDirection));
                cs.setInt(11, Math.max(page, 1));
                cs.setInt(12, Math.max(pageSize, 0));
                setString(cs, 13, pickNumber);
                setString(cs, 14, orderNumber);

                DashboardData data = new DashboardData();
                boolean hasResults = cs.execute();
                if (hasResults) {
                    try (ResultSet rs = cs.getResultSet()) {
                        if (rs.next()) {
                            data.summary = mapSummary(rs);
                            data.total = rs.getInt("TotalCount");
                        }
                    }
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            data.months.add(mapMonth(rs));
                        }
                    }
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            data.rows.add(mapRow(rs));
                        }
                    }
                }
                if (data.summary == null) {
                    data.summary = emptySummary();
                }
                return data;
            }
        });
    }

    private Map<String, Object> loadUnmatched(LocalDate fromDate, LocalDate toDate, String pickNumber,
                                              int page, int pageSize) {
        return jdbcTemplate.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{call dbo.TransportPerformance_Unmatched_Consult(?,?,?,?,?)}")) {
                setDate(cs, 1, fromDate);
                setDate(cs, 2, toDate);
                cs.setInt(3, Math.max(page, 1));
                cs.setInt(4, Math.max(pageSize, 0));
                setString(cs, 5, pickNumber);

                int total = 0;
                List<Map<String, Object>> rows = new ArrayList<>();
                boolean hasResults = cs.execute();
                if (hasResults) {
                    try (ResultSet rs = cs.getResultSet()) {
                        if (rs.next()) {
                            total = rs.getInt("TotalCount");
                        }
                    }
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            rows.add(mapUnmatched(rs));
                        }
                    }
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("total", total);
                body.put("rows", rows);
                return body;
            }
        });
    }

    private Map<String, Object> loadPick(int pick) {
        return jdbcTemplate.execute((ConnectionCallback<Map<String, Object>>) connection -> {
            try (CallableStatement cs = connection.prepareCall(
                    "{call dbo.TransportPerformance_Pick_Consult(?)}")) {
                cs.setInt(1, pick);
                Map<String, Object> header = null;
                List<Map<String, Object>> lines = new ArrayList<>();
                List<Map<String, Object>> carrier = new ArrayList<>();
                List<Map<String, Object>> edi = new ArrayList<>();
                List<Map<String, Object>> cases = new ArrayList<>();
                boolean hasResults = cs.execute();
                if (hasResults) {
                    try (ResultSet rs = cs.getResultSet()) {
                        if (rs.next()) {
                            header = mapPickHeader(rs);
                        }
                    }
                }
                if (header == null) {
                    return null;
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            lines.add(mapPickLine(rs));
                        }
                    }
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            carrier.add(mapPickCarrier(rs));
                        }
                    }
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            edi.add(mapPickEdi(rs));
                        }
                    }
                }
                if (cs.getMoreResults()) {
                    try (ResultSet rs = cs.getResultSet()) {
                        while (rs.next()) {
                            cases.add(mapPickCase(rs));
                        }
                    }
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("header", header);
                body.put("lines", lines);
                body.put("carrier", carrier);
                body.put("edi", edi);
                body.put("cases", cases);
                return body;
            }
        });
    }

    private ExcelExportRequest workbook(LocalDate fromDate, LocalDate toDate, String productName,
                                        String productSegment, String accountOwner, String accountName,
                                        String carrierZone, String result, String pickNumber, String orderNumber,
                                        DashboardData data) {
        List<ExcelLabelValue> metadata = new ArrayList<>();
        metadata.add(new ExcelLabelValue("From promised date", fromDate == null ? "" : DAY.format(fromDate)));
        metadata.add(new ExcelLabelValue("To promised date", toDate == null ? "" : DAY.format(toDate)));
        metadata.add(new ExcelLabelValue("Product", productLabel(productSegment, productName)));
        metadata.add(new ExcelLabelValue("Account owner", blankToAll(accountOwner)));
        metadata.add(new ExcelLabelValue("Account name", blankToAll(accountName)));
        metadata.add(new ExcelLabelValue("Carrier zone", blankToAll(carrierZone)));
        metadata.add(new ExcelLabelValue("Result", blankToAll(result)));
        metadata.add(new ExcelLabelValue("Pick number", blankToAll(pickNumber)));
        metadata.add(new ExcelLabelValue("Order", blankToAll(orderNumber)));
        metadata.add(new ExcelLabelValue("Total deliveries", data.summary.get("totalCount"), ExcelColumnType.INTEGER));
        metadata.add(new ExcelLabelValue("On time %", data.summary.get("onTimePercent"), ExcelColumnType.DECIMAL));
        metadata.add(new ExcelLabelValue("Late %", data.summary.get("latePercent"), ExcelColumnType.DECIMAL));
        metadata.add(new ExcelLabelValue("Timing NCR", data.summary.get("timingNcrCount"), ExcelColumnType.INTEGER));
        metadata.add(new ExcelLabelValue("Target %", data.summary.get("targetPercentage"), ExcelColumnType.DECIMAL));

        List<String> headers = List.of(
                "Pick number",
                "Order",
                "Product",
                "Account name",
                "Account owner",
                "Promised date",
                "Transmit date",
                "Earliest viable date",
                "Carrier Fecha Entrega",
                "Timing NCR",
                "Days late",
                "Result");
        List<ExcelColumnType> types = List.of(
                ExcelColumnType.STRING,
                ExcelColumnType.STRING,
                ExcelColumnType.STRING,
                ExcelColumnType.STRING,
                ExcelColumnType.STRING,
                ExcelColumnType.DATE,
                ExcelColumnType.STRING,
                ExcelColumnType.DATE,
                ExcelColumnType.DATE,
                ExcelColumnType.STRING,
                ExcelColumnType.INTEGER,
                ExcelColumnType.STRING);

        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : data.rows) {
            rows.add(List.of(
                    text(row.get("pickNumber")),
                    text(row.get("orderNumber")),
                    text(row.get("product")),
                    text(row.get("accountName")),
                    text(row.get("accountOwner")),
                    parseDate(row.get("promisedDate")),
                    formatTransmit(row.get("transmitDateTime")),
                    parseDate(row.get("earliestViableDate")),
                    parseDate(row.get("fechaEntrega")),
                    Boolean.TRUE.equals(row.get("timingNcr")) ? "Yes" : "No",
                    row.get("daysLate") == null ? "" : row.get("daysLate"),
                    text(row.get("result"))));
        }

        ExcelSheetRequest sheet = new ExcelSheetRequest(
                "Deliveries",
                new ExcelMetadataSection("Transport Performance Dashboard", metadata),
                new ExcelTableSection("Deliveries", headers, types, rows));
        return new ExcelExportRequest("TransportPerformance_" + LocalDate.now().format(FILE_DAY), List.of(sheet));
    }

    private static Map<String, Object> mapSummary(ResultSet rs) throws SQLException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", rs.getInt("TotalCount"));
        summary.put("onTimeCount", rs.getInt("OnTimeCount"));
        summary.put("onTimePercent", readDecimal(rs, "OnTimePercent"));
        summary.put("lateCount", rs.getInt("LateCount"));
        summary.put("latePercent", readDecimal(rs, "LatePercent"));
        summary.put("timingNcrCount", rs.getInt("TimingNcrCount"));
        summary.put("targetPercentage", readDecimal(rs, "TargetPercentage"));
        return summary;
    }

    private static Map<String, Object> emptySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", 0);
        summary.put("onTimeCount", 0);
        summary.put("onTimePercent", null);
        summary.put("lateCount", 0);
        summary.put("latePercent", null);
        summary.put("timingNcrCount", 0);
        summary.put("targetPercentage", null);
        return summary;
    }

    private static Map<String, Object> mapMonth(ResultSet rs) throws SQLException {
        Map<String, Object> month = new LinkedHashMap<>();
        month.put("monthLabel", rs.getString("MonthLabel"));
        month.put("monthYear", rs.getInt("MonthYear"));
        month.put("onTimePercent", readDecimal(rs, "OnTimePercent"));
        return month;
    }

    private static Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("product", rs.getString("Product"));
        row.put("pickNumber", rs.getString("PickNumber"));
        row.put("orderNumber", rs.getString("OrderNumber"));
        row.put("accountName", rs.getString("AccountName"));
        row.put("accountOwner", rs.getString("AccountOwner"));
        row.put("promisedDate", readDate(rs, "PromisedDate"));
        row.put("transmitDateTime", readDateTime(rs, "TransmitDateTime"));
        row.put("earliestViableDate", readDate(rs, "EarliestViableDate"));
        row.put("fechaEntrega", readDate(rs, "FechaEntrega"));
        row.put("timingNcr", rs.getBoolean("TimingNcr"));
        int daysLate = rs.getInt("DaysLate");
        row.put("daysLate", rs.wasNull() ? null : daysLate);
        row.put("ncrOnly", rs.getBoolean("NcrOnly"));
        row.put("result", rs.getString("Result"));
        return row;
    }

    private static Map<String, Object> mapPickHeader(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pickInt", rs.getInt("PickInt"));
        row.put("pickNumber", rs.getString("PickNumber"));
        row.put("orderNumber", rs.getString("OrderNumber"));
        row.put("accountName", rs.getString("AccountName"));
        row.put("accountOwner", rs.getString("AccountOwner"));
        row.put("shipToCountry", rs.getString("ShipToCountry"));
        row.put("shipToPostal", rs.getString("ShipToPostal"));
        row.put("carrierZone", rs.getString("CarrierZone"));
        int transitDays = rs.getInt("TransitDays");
        row.put("transitDays", rs.wasNull() ? null : transitDays);
        row.put("promisedDate", readDate(rs, "PromisedDate"));
        row.put("transmitDateTime", readDateTime(rs, "TransmitDateTime"));
        row.put("warehouseArrival", readDate(rs, "WarehouseArrival"));
        row.put("earliestViableDate", readDate(rs, "EarliestViableDate"));
        row.put("fechaEntrega", readDate(rs, "FechaEntrega"));
        row.put("comparisonDate", readDate(rs, "ComparisonDate"));
        int daysLate = rs.getInt("DaysLate");
        row.put("daysLate", rs.wasNull() ? null : daysLate);
        row.put("timingNcr", rs.getBoolean("TimingNcr"));
        row.put("result", rs.getString("Result"));
        return row;
    }

    private static Map<String, Object> mapPickLine(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", rs.getString("ProductName"));
        row.put("segmentNumber", rs.getString("SegmentNumber"));
        row.put("orderNumber", rs.getString("OrderNumber"));
        row.put("promisedDate", readDate(rs, "PromisedDate"));
        return row;
    }

    private static Map<String, Object> mapPickCarrier(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("importId", rs.getInt("ImportId"));
        row.put("importDate", readDateTime(rs, "ImportDate"));
        row.put("carrierCode", rs.getString("CarrierCode"));
        row.put("status", rs.getString("Status"));
        row.put("sku", rs.getString("Sku"));
        row.put("fechaEntrega", readDate(rs, "FechaEntrega"));
        row.put("clientName", rs.getString("ClientName"));
        row.put("address", rs.getString("Address"));
        row.put("locality", rs.getString("Locality"));
        row.put("postalCode", rs.getString("PostalCode"));
        row.put("country", rs.getString("Country"));
        return row;
    }

    private static Map<String, Object> mapPickEdi(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ediDateTime", readDateTime(rs, "EdiDateTime"));
        row.put("source", rs.getString("Source"));
        row.put("reportingDate", readDate(rs, "ReportingDate"));
        row.put("countryCode", rs.getString("CountryCode"));
        row.put("recordType", rs.getString("RecordType"));
        row.put("processed", rs.getString("Processed"));
        return row;
    }

    private static Map<String, Object> mapPickCase(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseNumber", rs.getString("CaseNumber"));
        row.put("caseStatus", rs.getString("CaseStatus"));
        row.put("attributed", rs.getString("Attributed"));
        row.put("openedDate", readDate(rs, "OpenedDate"));
        row.put("closedDate", readDate(rs, "ClosedDate"));
        row.put("subject", rs.getString("Subject"));
        row.put("reason", rs.getString("Reason"));
        row.put("description", rs.getString("Description"));
        row.put("carrierName", rs.getString("CarrierName"));
        row.put("cause", rs.getString("Cause"));
        return row;
    }

    private static Map<String, Object> mapUnmatched(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pickNumber", rs.getString("PickNumber"));
        int searchedNumber = rs.getInt("SearchedNumber");
        row.put("searchedNumber", rs.wasNull() ? null : searchedNumber);
        row.put("carrierCode", rs.getString("CarrierCode"));
        row.put("fechaEntrega", readDate(rs, "FechaEntrega"));
        row.put("clientName", rs.getString("ClientName"));
        row.put("postalCode", rs.getString("PostalCode"));
        row.put("country", rs.getString("Country"));
        return row;
    }

    private static Object readDecimal(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value;
    }

    private static String readDate(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().toLocalDate().toString();
    }

    private static String readDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static void setDate(CallableStatement cs, int index, LocalDate value) throws SQLException {
        if (value == null) {
            cs.setNull(index, Types.DATE);
        } else {
            cs.setDate(index, Date.valueOf(value));
        }
    }

    private static void setString(CallableStatement cs, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            cs.setNull(index, Types.NVARCHAR);
        } else {
            cs.setString(index, value.trim());
        }
    }

    private static String sortColumn(String value) {
        if (value == null) {
            return "fechaEntrega";
        }
        return switch (value) {
            case "pick", "order", "product", "accountName", "accountOwner",
                    "promisedDate", "transmitDate", "earliestViableDate", "fechaEntrega",
                    "timingNcr", "daysLate", "result" -> value;
            default -> "fechaEntrega";
        };
    }

    private static String sortDirection(String value) {
        return "asc".equalsIgnoreCase(value) ? "ASC" : "DESC";
    }

    private static String blankToAll(String value) {
        return value == null || value.isBlank() ? "All" : value.trim();
    }

    private static String productLabel(String segment, String name) {
        boolean hasSegment = segment != null && !segment.isBlank();
        boolean hasName = name != null && !name.isBlank();
        if (hasSegment && hasName) {
            return name.trim() + " (" + segment.trim() + ")";
        }
        if (hasName) {
            return name.trim();
        }
        if (hasSegment) {
            return segment.trim();
        }
        return "All";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Object parseDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "";
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static String formatTransmit(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "";
        }
        return LocalDateTime.parse(String.valueOf(value)).format(DAY_TIME);
    }

    private static final class DashboardData {
        private Map<String, Object> summary;
        private final List<Map<String, Object>> months = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private int total;
    }
}
