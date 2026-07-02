package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.ClientDeliveryDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClientDeliveryService {

    private final JdbcTemplate jdbcTemplate;

    public ClientDeliveryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ClientDeliveryDto getDelivery(UUID projectId) {
        List<Map<String, Object>> requested = new ArrayList<>();
        if (tableExists("wishlist")) {
            requested = jdbcTemplate.queryForList(
                "SELECT * FROM wishlist WHERE project_id = ? AND source = 'client'", projectId
            );
        } else if (tableExists("wishlist_items")) {
            requested = jdbcTemplate.queryForList(
                "SELECT * FROM wishlist_items WHERE project_id = ? AND type = 'client_wish'", projectId
            );
        }

        List<Map<String, Object>> delivered = new ArrayList<>();
        if (tableExists("tasks")) {
            delivered = jdbcTemplate.queryForList(
                "SELECT * FROM tasks WHERE project_id = ? AND status = 'done'", projectId
            );
        }

        List<String> screenshots = new ArrayList<>();
        if (tableExists("pr_reviews")) {
            List<Map<String, Object>> reviews = jdbcTemplate.queryForList(
                "SELECT screenshot_urls FROM pr_reviews"
            );
            for (Map<String, Object> review : reviews) {
                Object urls = review.get("screenshot_urls");
                if (urls instanceof String[]) {
                    screenshots.addAll(Arrays.asList((String[]) urls));
                } else if (urls instanceof String) {
                    screenshots.add((String) urls);
                }
            }
        }

        List<String> prLinks = new ArrayList<>();
        if (tableExists("jules_sessions")) {
            prLinks = jdbcTemplate.queryForList("SELECT pr_url FROM jules_sessions WHERE pr_url IS NOT NULL", String.class);
        } else if (tableExists("pr_reviews")) {
            prLinks = jdbcTemplate.queryForList("SELECT pr_url FROM pr_reviews WHERE pr_url IS NOT NULL", String.class);
        }

        String testSummary = "Data source not yet available";
        if (tableExists("github_access_status")) {
            List<Map<String, Object>> status = jdbcTemplate.queryForList("SELECT ci_status FROM github_access_status LIMIT 1");
            if (!status.isEmpty()) {
                testSummary = "Last CI Status: " + status.get(0).get("ci_status");
            }
        }

        return new ClientDeliveryDto(requested, delivered, screenshots, prLinks, testSummary);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = ? OR table_name = ?",
            Integer.class, tableName.toLowerCase(), tableName.toUpperCase()
        );
        return count != null && count > 0;
    }
}
