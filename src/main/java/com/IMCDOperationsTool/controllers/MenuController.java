package com.IMCDOperationsTool.controllers;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic sidebar. {@code dbo.Menu_Consult} returns the menu rows the user may see.
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MenuController {

    private static final Logger LOG = LoggerFactory.getLogger(MenuController.class);

    private final JdbcTemplate jdbcTemplate;

    public MenuController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param uotId local AD user, mail, or display name stored on the Operations Tool session
     */
    @GetMapping("utils/getMenu")
    public ResponseEntity<List<Map<String, String>>> getMenu(@RequestParam("UOT_Id") String uotId) {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            jdbcTemplate.query(
                    "{call dbo.Menu_Consult(?)}",
                    ps -> ps.setString(1, uotId),
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
            LOG.error("Menu consult failed", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
