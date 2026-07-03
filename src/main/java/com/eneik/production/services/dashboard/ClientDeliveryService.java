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
        if (tableExists("wishlist") && columnExists("wishlist", "project_id")) {
            requested = jdbcTemplate.queryForList(
                "SELECT * FROM wishlist WHERE project_id = ? AND source = 'client'", projectId
            );
        } else if (tableExists("wishlist_items") && columnExists("wishlist_items", "project_id")) {
            requested = jdbcTemplate.queryForList(
                "SELECT * FROM wishlist_items WHERE project_id = ? AND type = 'client_wish'", projectId
            );
        }

        List<Map<String, Object>> delivered = new ArrayList<>();
        if (tableExists("tasks") && columnExists("tasks", "project_id")) {
            delivered = jdbcTemplate.queryForList(
                "SELECT * FROM tasks WHERE project_id = ? AND status = 'done'", projectId
            );
        }

        List<String> screenshots = new ArrayList<>();
        if (tableExists("pr_reviews")) {
            if (columnExists("pr_reviews", "project_id")) {
                List<Map<String, Object>> reviews = jdbcTemplate.queryForList(
                    "SELECT screenshot_urls FROM pr_reviews WHERE project_id = ?", projectId
                );
                for (Map<String, Object> review : reviews) {
                    addScreenshots(screenshots, review.get("screenshot_urls"));
                }
            } else if (columnExists("pr_reviews", "pr_url") && tableExists("jules_sessions") && columnExists("jules_sessions", "project_id")) {
                // Try to join via pr_url if project_id is missing in pr_reviews but exists in jules_sessions
                List<Map<String, Object>> reviews = jdbcTemplate.queryForList(
                    "SELECT r.screenshot_urls FROM pr_reviews r JOIN jules_sessions s ON r.pr_url = s.pr_url WHERE s.project_id = ?", projectId
                );
                for (Map<String, Object> review : reviews) {
                    addScreenshots(screenshots, review.get("screenshot_urls"));
                }
            }
        }

        List<String> prLinks = new ArrayList<>();
        if (tableExists("jules_sessions") && columnExists("jules_sessions", "project_id")) {
            prLinks = jdbcTemplate.queryForList("SELECT pr_url FROM jules_sessions WHERE project_id = ? AND pr_url IS NOT NULL", String.class, projectId);
        } else if (tableExists("pr_reviews") && columnExists("pr_reviews", "project_id")) {
            prLinks = jdbcTemplate.queryForList("SELECT pr_url FROM pr_reviews WHERE project_id = ? AND pr_url IS NOT NULL", String.class, projectId);
        }

        String testSummary = "Data source not yet available";
        if (tableExists("github_access_status") && columnExists("github_access_status", "project_id")) {
            String orderBy = columnExists("github_access_status", "checked_at") ? "checked_at" : "id";
            List<Map<String, Object>> status = jdbcTemplate.queryForList("SELECT ci_status FROM github_access_status WHERE project_id = ? ORDER BY " + orderBy + " DESC LIMIT 1", projectId);
            if (!status.isEmpty()) {
                testSummary = "Last CI Status: " + status.get(0).get("ci_status");
            }
        }

        return new ClientDeliveryDto(requested, delivered, screenshots, prLinks, testSummary);
    }

    private void addScreenshots(List<String> screenshots, Object urls) {
        if (urls instanceof String[]) {
            screenshots.addAll(Arrays.asList((String[]) urls));
        } else if (urls instanceof String) {
            String s = (String) urls;
            if (s.startsWith("[") && s.endsWith("]")) {
                // Simple JSON array parsing attempt if it's stored as stringified JSON
                s = s.substring(1, s.length() - 1);
                for (String part : s.split(",")) {
                    screenshots.add(part.trim().replace("\"", ""));
                }
            } else {
                screenshots.add(s);
            }
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = ? OR table_name = ?",
            Integer.class, tableName.toLowerCase(), tableName.toUpperCase()
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE (table_name = ? OR table_name = ?) AND (column_name = ? OR column_name = ?)",
            Integer.class, tableName.toLowerCase(), tableName.toUpperCase(), columnName.toLowerCase(), columnName.toUpperCase()
        );
        return count != null && count > 0;
    }
}
