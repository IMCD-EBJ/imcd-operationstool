package com.IMCDOperationsTool.controllers;

import java.sql.CallableStatement;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.IMCDOperationsTool.services.ActivityLogService;
import com.IMCDOperationsTool.services.FileService;
import com.imcd.platformlib.excel.imports.ExcelImportException;
import com.imcd.platformlib.excel.imports.ExcelImportRequest;
import com.imcd.platformlib.excel.imports.ExcelImportResult;
import com.imcd.platformlib.excel.imports.ExcelImportService;

/**
 * Report import endpoints. Workbook loading is {@link ExcelImportService}.
 * This controller only lists the catalog and stores the original file.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/import-reports/")
public class ImportReportsController {

    private static final Logger LOG = LoggerFactory.getLogger(ImportReportsController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ExcelImportService excelImportService;
    private final FileService fileService;
    private final ActivityLogService activityLogService;

    public ImportReportsController(JdbcTemplate jdbcTemplate,
            ExcelImportService excelImportService,
            FileService fileService,
            ActivityLogService activityLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.excelImportService = excelImportService;
        this.fileService = fileService;
        this.activityLogService = activityLogService;
    }

    @GetMapping("getImportReportsCombo")
    public ResponseEntity<Object> getImportReports() {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            jdbcTemplate.setResultsMapCaseInsensitive(true);
            jdbcTemplate.query(
                    "{call dbo.Import_Reports_Consult(?)}",
                    ps -> ps.setInt(1, 0),
                    rs -> {
                        ResultSetMetaData metaData = rs.getMetaData();
                        while (rs.next()) {
                            Map<String, String> row = new LinkedHashMap<>();
                            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                                row.put(metaData.getColumnLabel(i), rs.getString(i));
                            }
                            rows.add(row);
                        }
                        return null;
                    });
            return new ResponseEntity<>(rows, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>("Database connection error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("import")
    public ResponseEntity<Object> processExcelFile(@RequestParam("file") MultipartFile file,
            @RequestParam("reportId") Integer reportId,
            @RequestParam("userLogged") String userLogged) {
        try {
            ExcelImportResult result = excelImportService.importWorkbook(
                    new ExcelImportRequest(file.getInputStream(), file.getOriginalFilename(), reportId, userLogged));
            fileService.saveFile(result.tableName() + ".xlsx", file);

            activityLogService.log(ActivityLogService.IMPORT_FILE, userLogged, null);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("logImport", result.logImport());
            body.put("tableName", result.tableName());
            return new ResponseEntity<>(body, HttpStatus.OK);
        } catch (ExcelImportException e) {
            return importError(e);
        } catch (Exception e) {
            LOG.error("Import failed", e);
            return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("alertMessage")
    public ResponseEntity<Object> alertMessage(@RequestParam("idMensaje") String idMensaje) {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            jdbcTemplate.query(
                    "{call dbo.Configuration_AlertMessage_Consult(?)}",
                    ps -> ps.setString(1, idMensaje),
                    rs -> {
                        while (rs.next()) {
                            Map<String, String> row = new LinkedHashMap<>();
                            row.put("title", rs.getString("CAM_Title"));
                            row.put("body", rs.getString("CAM_Body"));
                            row.put("icon", rs.getString("CAM_Icon"));
                            row.put("buttons", rs.getString("CAM_Buttons"));
                            row.put("button1", rs.getString("CAM_Button1"));
                            row.put("button2", rs.getString("CAM_Button2"));
                            row.put("dangerMode", rs.getString("CAM_DangerMode"));
                            row.put("type", rs.getString("CAM_Type"));
                            rows.add(row);
                        }
                        return null;
                    });
            return new ResponseEntity<>(rows, HttpStatus.OK);
        } catch (RuntimeException e) {
            LOG.warn("Alert message {} could not be loaded: {}", idMensaje, e.getMessage());
            return new ResponseEntity<>(List.of(), HttpStatus.OK);
        }
    }

    @GetMapping("branchPlants")
    public ResponseEntity<Object> branchPlants() {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            jdbcTemplate.query("{call dbo.BranchPlants_Consult}", rs -> {
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("BRP_Id", rs.getString("BRP_Id"));
                    row.put("BRP_Description", rs.getString("BRP_Description"));
                    rows.add(row);
                }
                return null;
            });
            return new ResponseEntity<>(rows, HttpStatus.OK);
        } catch (RuntimeException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("carrier")
    public ResponseEntity<Object> importCarrier(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "brpId", required = false) String brpId,
            @RequestParam(value = "fileIdentifier", required = false) String fileIdentifier,
            @RequestParam("localAdUser") String localAdUser,
            @RequestParam("mail") String mail,
            @RequestParam("userName") String userName) {
        try {
            if (brpId == null || brpId.isBlank()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            String identifier = fileIdentifier == null ? "" : fileIdentifier.trim();
            if (identifier.isEmpty()) {
                return errorBody(HttpStatus.BAD_REQUEST, "Enter a file identifier before uploading.");
            }
            if (identifier.length() > 100) {
                return errorBody(HttpStatus.BAD_REQUEST, "The file identifier must be 100 characters or fewer.");
            }
            int branchPlantId = Integer.parseInt(brpId.trim());
            LOG.info("Carrier import started for branch plant {} by {}", branchPlantId, localAdUser);
            int reportId = carrierReportId();
            ExcelImportResult staged = excelImportService.importWorkbook(
                    new ExcelImportRequest(file.getInputStream(), file.getOriginalFilename(), reportId, localAdUser));
            Map<String, Object> loaded = loadCarrierRows(staged.tableName(), branchPlantId, localAdUser, mail,
                    userName, identifier);
            try {
                fileService.saveFile(staged.tableName() + ".xlsx", file);
            } catch (Exception ignored) {
                // The master-file directory is optional for this import.
            }
            activityLogService.log(ActivityLogService.IMPORT_FILE, localAdUser, userName);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("importId", loaded.get("importId"));
            body.put("rowCount", loaded.get("rowCount"));
            body.put("tableName", staged.tableName());
            return new ResponseEntity<>(body, HttpStatus.OK);
        } catch (ExcelImportException e) {
            return importError(e);
        } catch (Exception e) {
            LOG.error("Carrier import failed", e);
            return errorBody(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private ResponseEntity<Object> importError(ExcelImportException e) {
        LOG.warn("Carrier import rejected: {}", e.getMessage());
        HttpStatus status = e.isBadRequest() ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return errorBody(status, e.getMessage());
    }

    private ResponseEntity<Object> errorBody(HttpStatus status, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", userMessage(message));
        return new ResponseEntity<>(body, status);
    }

    private String userMessage(String message) {
        if (message == null || message.isBlank()) {
            return "The file could not be imported.";
        }
        String trimmed = message.trim();
        if (trimmed.regionMatches(true, 0, "ERROR:", 0, 6)) {
            trimmed = trimmed.substring(6).trim();
        } else if (trimmed.regionMatches(true, 0, "WARNING:", 0, 8)) {
            trimmed = trimmed.substring(8).trim();
        }
        if ("The file columns do not match the expected configuration".equals(trimmed)) {
            return "The file is missing a required column, or a column name does not match. "
                    + "Use Carrier Code, Pick Number, Status, SKU, Delivery Date, Client, Address, Locality, Postal Code, and Country, in that order and with the correct case.";
        }
        if ("Header row was not found".equals(trimmed)) {
            return "The first row of the file must contain the column names.";
        }
        if (trimmed.startsWith("Column ") && trimmed.contains("does not match the expected column")) {
            return trimmed + " Keep the columns in the order shown on this page.";
        }
        return trimmed.isBlank() ? "The file could not be imported." : trimmed;
    }

    private int carrierReportId() {
        AtomicInteger reportId = new AtomicInteger();
        jdbcTemplate.query("{call dbo.CarrierImport_Report_Consult}", rs -> {
            if (rs.next()) {
                reportId.set(rs.getInt("RC_ReportId"));
            }
            return null;
        });
        if (reportId.get() == 0) {
            throw new ExcelImportException("Carrier import is not configured");
        }
        return reportId.get();
    }

    private Map<String, Object> loadCarrierRows(String tableName, Integer brpId, String localAdUser,
            String mail, String userName, String fileIdentifier) {
        List<SqlParameter> parameters = new ArrayList<>();
        parameters.add(new SqlParameter("tableName", Types.NVARCHAR));
        parameters.add(new SqlParameter("brpId", Types.INTEGER));
        parameters.add(new SqlParameter("localAdUser", Types.NVARCHAR));
        parameters.add(new SqlParameter("mail", Types.NVARCHAR));
        parameters.add(new SqlParameter("userName", Types.NVARCHAR));
        parameters.add(new SqlParameter("fileIdentifier", Types.NVARCHAR));
        parameters.add(new SqlOutParameter("errorMessage", Types.NVARCHAR));
        parameters.add(new SqlOutParameter("importId", Types.INTEGER));
        parameters.add(new SqlOutParameter("rowCount", Types.INTEGER));

        Map<String, Object> result = jdbcTemplate.call(connection -> {
            CallableStatement cs = connection.prepareCall("{call dbo.Carrier_Imported_Data_Load(?,?,?,?,?,?,?,?,?)}");
            cs.setString("tableName", tableName);
            cs.setInt("brpId", brpId);
            cs.setString("localAdUser", localAdUser);
            cs.setString("mail", mail);
            cs.setString("userName", userName);
            cs.setString("fileIdentifier", fileIdentifier);
            cs.registerOutParameter("errorMessage", Types.NVARCHAR);
            cs.registerOutParameter("importId", Types.INTEGER);
            cs.registerOutParameter("rowCount", Types.INTEGER);
            return cs;
        }, parameters);

        Object errorMessage = result.get("errorMessage");
        if (errorMessage != null) {
            String message = errorMessage.toString().trim();
            if (message.regionMatches(true, 0, "ERROR", 0, 5)) {
                throw new ExcelImportException(message);
            }
        }
        return result;
    }
}
