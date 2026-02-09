package io.regulyn.scanner.website;

import io.regulyn.scanner.model.ScanSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WebsiteCrawlConfig {

    private List<String> startUrls = new ArrayList<>();
    private int maxDepth = 2;
    private int maxPages = 50;
    private int perRequestTimeoutMs = 3000;
    private int totalTimeoutMs = 20000;
    private String userAgent = "RegulynScanner/2.0";
    private boolean allowExternalDomains = false;
    private boolean respectRobots = false;
    private int maxFailures = 10;

    public static WebsiteCrawlConfig fromSource(ScanSource source) {
        WebsiteCrawlConfig config = new WebsiteCrawlConfig();
        Map<String, Object> metadata = source.getMetadata();

        List<String> startUrls = readStringList(metadata, "startUrls");
        if (startUrls.isEmpty() && source.getBaseUrl() != null && !source.getBaseUrl().isBlank()) {
            startUrls = List.of(source.getBaseUrl());
        }
        config.setStartUrls(startUrls);

        config.setMaxDepth(readInt(metadata, "maxDepth", config.getMaxDepth()));
        config.setMaxPages(readInt(metadata, "maxPages", config.getMaxPages()));
        config.setPerRequestTimeoutMs(readInt(metadata, "perRequestTimeoutMs", config.getPerRequestTimeoutMs()));
        config.setTotalTimeoutMs(readInt(metadata, "totalTimeoutMs", config.getTotalTimeoutMs()));
        config.setUserAgent(readString(metadata, "userAgent", config.getUserAgent()));
        config.setAllowExternalDomains(readBoolean(metadata, "allowExternalDomains", config.isAllowExternalDomains()));
        config.setRespectRobots(readBoolean(metadata, "respectRobots", config.isRespectRobots()));
        config.setMaxFailures(readInt(metadata, "maxFailures", config.getMaxFailures()));

        return config;
    }

    private static String readString(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata != null ? metadata.get(key) : null;
        if (value == null) {
            return defaultValue;
        }
        return value.toString();
    }

    private static int readInt(Map<String, Object> metadata, String key, int defaultValue) {
        Object value = metadata != null ? metadata.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean readBoolean(Map<String, Object> metadata, String key, boolean defaultValue) {
        Object value = metadata != null ? metadata.get(key) : null;
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private static List<String> readStringList(Map<String, Object> metadata, String key) {
        Object value = metadata != null ? metadata.get(key) : null;
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    public List<String> getStartUrls() {
        return startUrls;
    }

    public void setStartUrls(List<String> startUrls) {
        this.startUrls = startUrls != null ? startUrls : new ArrayList<>();
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getPerRequestTimeoutMs() {
        return perRequestTimeoutMs;
    }

    public void setPerRequestTimeoutMs(int perRequestTimeoutMs) {
        this.perRequestTimeoutMs = perRequestTimeoutMs;
    }

    public int getTotalTimeoutMs() {
        return totalTimeoutMs;
    }

    public void setTotalTimeoutMs(int totalTimeoutMs) {
        this.totalTimeoutMs = totalTimeoutMs;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isAllowExternalDomains() {
        return allowExternalDomains;
    }

    public void setAllowExternalDomains(boolean allowExternalDomains) {
        this.allowExternalDomains = allowExternalDomains;
    }

    public boolean isRespectRobots() {
        return respectRobots;
    }

    public void setRespectRobots(boolean respectRobots) {
        this.respectRobots = respectRobots;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }
}