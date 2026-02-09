package io.regulyn.scanner.website;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.regulyn.scanner.adapter.model.Finding;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class WebsiteCrawler {

    private static final Logger log = LoggerFactory.getLogger(WebsiteCrawler.class);
    private static final int SAMPLE_TEXT_LIMIT = 300;
    private static final int SUMMARY_CAP = 50;
    private static final Set<String> PII_TOKENS = Set.of(
        "name", "first_name", "first-name", "lastname", "last_name", "last-name",
        "email", "phone", "mobile", "address", "dob", "birth", "ssn", "pan", "aadhaar", "passport"
    );

    private static final Map<String, String> TRACKER_VENDORS = Map.ofEntries(
        Map.entry("googletagmanager.com", "Google Tag Manager"),
        Map.entry("google-analytics.com", "Google Analytics"),
        Map.entry("doubleclick.net", "DoubleClick"),
        Map.entry("facebook.net", "Facebook"),
        Map.entry("hotjar.com", "Hotjar"),
        Map.entry("mixpanel.com", "Mixpanel"),
        Map.entry("segment.com", "Segment"),
        Map.entry("clarity.ms", "Microsoft Clarity"),
        Map.entry("intercomcdn.com", "Intercom")
    );

    private final ObjectMapper canonicalMapper;

    public WebsiteCrawler() {
        this.canonicalMapper = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public WebsiteCrawlResult crawl(WebsiteCrawlConfig config) {
        WebsiteCrawlResult result = new WebsiteCrawlResult();

        if (config.getStartUrls().isEmpty()) {
            result.setFailed(true);
            result.setFailureReason("FAILED: NO_START_URLS");
            return result;
        }

        Instant deadline = Instant.now().plusMillis(config.getTotalTimeoutMs());
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(config.getPerRequestTimeoutMs()))
            .build();

        String primaryHost = hostOf(config.getStartUrls().get(0));
        Set<String> visited = new HashSet<>();
        Deque<UrlTask> queue = new ArrayDeque<>();
        for (String startUrl : config.getStartUrls()) {
            String normalized = normalizeUrl(startUrl);
            if (normalized != null) {
                queue.add(new UrlTask(normalized, 0));
            }
        }

        RobotsRules robotsRules = config.isRespectRobots()
            ? fetchRobots(client, config.getStartUrls().get(0), config.getPerRequestTimeoutMs())
            : RobotsRules.allowAll();

        int failures = 0;
        List<String> partialReasons = new ArrayList<>();

        while (!queue.isEmpty() && result.getPages().size() < config.getMaxPages()) {
            if (Instant.now().isAfter(deadline)) {
                partialReasons.add("TOTAL_TIMEOUT");
                break;
            }

            UrlTask task = queue.poll();
            String normalizedUrl = normalizeUrl(task.url);
            if (normalizedUrl == null) {
                continue;
            }
            String urlHash = sha256(normalizedUrl);
            if (!visited.add(urlHash)) {
                continue;
            }

            if (config.isRespectRobots() && !robotsRules.isAllowed(normalizedUrl)) {
                continue;
            }

            long startTime = System.nanoTime();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedUrl))
                    .timeout(Duration.ofMillis(config.getPerRequestTimeoutMs()))
                    .header("User-Agent", config.getUserAgent())
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                long durationMs = Duration.ofNanos(System.nanoTime() - startTime).toMillis();

                WebsitePageSummary pageSummary = new WebsitePageSummary();
                pageSummary.setUrl(normalizedUrl);
                pageSummary.setUrlHash(urlHash);
                pageSummary.setDepth(task.depth);
                pageSummary.setFetchedAt(Instant.now());
                pageSummary.setHttpStatus(response.statusCode());
                pageSummary.setDurationMs(durationMs);

                String contentType = response.headers().firstValue("Content-Type").orElse(null);
                pageSummary.setContentType(contentType);

                if (contentType != null && contentType.contains("text/html")) {
                    Document document = Jsoup.parse(response.body(), normalizedUrl);
                    pageSummary.setTitle(document.title());

                    ExtractionResult extraction = extractFromDocument(document, response, normalizedUrl, primaryHost, config.isAllowExternalDomains());
                    pageSummary.setCookieCount(extraction.cookies.size());
                    pageSummary.setFormCount(extraction.forms.size());
                    pageSummary.setTrackerCount(extraction.trackerCount);
                    pageSummary.setHasPiiFormFields(extraction.hasPiiFormFields);
                    pageSummary.setSampleText(extraction.sampleText);
                    pageSummary.setDataCollectionSummary(extraction.summary);
                    pageSummary.setPageSummaryHash(hashJson(extraction.summary));

                    result.getFindings().addAll(extraction.findings);

                    if (task.depth < config.getMaxDepth()) {
                        for (String link : extraction.sameDomainLinks) {
                            if (!visited.contains(sha256(link))) {
                                queue.add(new UrlTask(link, task.depth + 1));
                            }
                        }
                    }
                } else {
                    pageSummary.setCookieCount(0);
                    pageSummary.setFormCount(0);
                    pageSummary.setTrackerCount(0);
                    pageSummary.setHasPiiFormFields(false);
                    pageSummary.setDataCollectionSummary(Map.of());
                    pageSummary.setPageSummaryHash(hashJson(Map.of()));
                }

                result.getPages().add(pageSummary);

                if (Instant.now().isAfter(deadline)) {
                    partialReasons.add("TOTAL_TIMEOUT");
                    break;
                }
            } catch (HttpTimeoutException timeout) {
                failures++;
                if (failures >= config.getMaxFailures()) {
                    if (result.getPages().isEmpty()) {
                        result.setFailed(true);
                        result.setFailureReason("FAILED: REQUEST_TIMEOUT");
                    } else {
                        partialReasons.add("REQUEST_TIMEOUT");
                    }
                    break;
                }
            } catch (Exception ex) {
                failures++;
                log.warn("Website crawl failed for {}: {}", normalizedUrl, ex.getMessage());
                if (failures >= config.getMaxFailures()) {
                    if (result.getPages().isEmpty()) {
                        result.setFailed(true);
                        result.setFailureReason("FAILED: REQUEST_FAILURES");
                    } else {
                        partialReasons.add("REQUEST_FAILURES");
                    }
                    break;
                }
            }
        }

        if (!queue.isEmpty() && result.getPages().size() >= config.getMaxPages()) {
            partialReasons.add("MAX_PAGES_REACHED");
        }

        result.setFailures(failures);
        if (!partialReasons.isEmpty()) {
            if (result.getPages().isEmpty()) {
                result.setFailed(true);
                result.setFailureReason("FAILED: " + String.join("; ", partialReasons));
                return result;
            }
            result.setPartial(true);
            String reason = "PARTIAL: " + String.join("; ", partialReasons)
                + "; fetchedPages=" + result.getPages().size()
                + "; failures=" + failures;
            result.setPartialReason(reason);
        }

        return result;
    }

    private ExtractionResult extractFromDocument(Document document,
                                                HttpResponse<String> response,
                                                String pageUrl,
                                                String primaryHost,
                                                boolean allowExternalDomains) {
        ExtractionResult result = new ExtractionResult();

        List<Map<String, Object>> cookiesSummary = new ArrayList<>();
        for (String setCookie : response.headers().allValues("Set-Cookie")) {
            WebsiteFindingFactory.CookieInfo cookieInfo = parseCookie(setCookie);
            if (cookieInfo == null) {
                continue;
            }
            Map<String, Object> cookieSummary = new HashMap<>();
            cookieSummary.put("name", cookieInfo.getName());
            cookieSummary.put("category", cookieInfo.getCategory());
            cookieSummary.put("attributes", cookieInfo.getAttributes());
            cookiesSummary.add(cookieSummary);
            result.cookies.add(cookieInfo);

            Finding finding = WebsiteFindingFactory.cookieFinding(pageUrl, cookieInfo);
            result.findings.add(finding);
        }

        Elements forms = document.select("form");
        List<Map<String, Object>> formsSummary = new ArrayList<>();
        int formIndex = 0;
        for (Element form : forms) {
            String action = normalizeUrl(form.absUrl("action"));
            if (action == null || action.isBlank()) {
                action = pageUrl;
            }
            String method = form.attr("method");
            if (method == null || method.isBlank()) {
                method = "GET";
            }

            List<Map<String, String>> fields = new ArrayList<>();
            boolean hasPii = false;
            Elements inputs = form.select("input,select,textarea");
            for (Element input : inputs) {
                String name = input.attr("name");
                String id = input.id();
                String placeholder = input.attr("placeholder");
                String type = input.attr("type");
                fields.add(Map.of(
                    "name", safeValue(name),
                    "type", safeValue(type)
                ));

                if (containsPiiToken(name) || containsPiiToken(id) || containsPiiToken(placeholder)) {
                    hasPii = true;
                }
            }

            if (hasPii) {
                result.hasPiiFormFields = true;
                Finding finding = WebsiteFindingFactory.formPiiFinding(pageUrl, action, formIndex);
                result.findings.add(finding);
            }

            if (action.startsWith("http://")) {
                Finding finding = WebsiteFindingFactory.insecureFormFinding(pageUrl, action, formIndex);
                result.findings.add(finding);
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("action", action);
            summary.put("method", method.toUpperCase());
            summary.put("pii", hasPii);
            summary.put("fields", capList(fields));
            formsSummary.add(summary);
            formIndex++;
        }
        result.forms.addAll(formsSummary);

        Elements scriptTags = document.select("script[src]");
        List<Map<String, Object>> trackersSummary = new ArrayList<>();
        Set<String> trackerDomains = new HashSet<>();
        for (Element script : scriptTags) {
            String src = script.absUrl("src");
            String host = hostOf(src);
            if (host == null) {
                continue;
            }
            String vendor = detectTrackerVendor(host);
            if (vendor != null) {
                trackerDomains.add(host);
                trackersSummary.add(Map.of("domain", host, "vendor", vendor));

                Finding finding = WebsiteFindingFactory.trackerFinding(pageUrl, host, vendor);
                result.findings.add(finding);
            }
        }

        result.trackerCount = trackersSummary.size();

        Set<String> unknownThirdParties = new HashSet<>();
        Elements links = document.select("a[href]");
        for (Element link : links) {
            String href = normalizeUrl(link.absUrl("href"));
            if (href == null) {
                continue;
            }
            String host = hostOf(href);
            if (host == null) {
                continue;
            }
            if (primaryHost != null && !primaryHost.equalsIgnoreCase(host)) {
                unknownThirdParties.add(host);
                if (allowExternalDomains) {
                    result.sameDomainLinks.add(href);
                }
            } else {
                result.sameDomainLinks.add(href);
            }
        }

        for (String domain : unknownThirdParties) {
            Finding finding = WebsiteFindingFactory.unknownThirdPartyFinding(pageUrl, domain);
            result.findings.add(finding);
        }

        String text = document.body() != null ? document.body().text() : "";
        result.sampleText = text.length() > SAMPLE_TEXT_LIMIT ? text.substring(0, SAMPLE_TEXT_LIMIT) : text;

        Map<String, Object> summary = new HashMap<>();
        summary.put("cookies", capList(cookiesSummary));
        summary.put("forms", capList(formsSummary));
        summary.put("trackers", capList(trackersSummary));
        summary.put("unknownThirdParties", capList(new ArrayList<>(unknownThirdParties)));
        result.summary = summary;

        return result;
    }

    private WebsiteFindingFactory.CookieInfo parseCookie(String setCookie) {
        if (setCookie == null || setCookie.isBlank()) {
            return null;
        }
        String[] parts = setCookie.split(";");
        String[] nameValue = parts[0].split("=", 2);
        if (nameValue.length == 0) {
            return null;
        }
        String name = nameValue[0].trim();
        if (name.isBlank()) {
            return null;
        }
        Map<String, String> attributes = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String[] attr = part.split("=", 2);
            String key = attr[0].trim().toLowerCase(Locale.ROOT);
            String value = attr.length > 1 ? attr[1].trim() : "true";
            attributes.put(key, value);
        }
        return new WebsiteFindingFactory.CookieInfo(name, categorizeCookie(name), attributes);
    }

    private String categorizeCookie(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("_ga") || lower.contains("_gid") || lower.contains("_gat")) {
            return "ANALYTICS";
        }
        if (lower.contains("fb") || lower.contains("_fbp")) {
            return "MARKETING";
        }
        if (lower.contains("session") || lower.contains("csrf") || lower.contains("xsrf")) {
            return "NECESSARY";
        }
        return "UNKNOWN";
    }

    private boolean containsPiiToken(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String token : PII_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String detectTrackerVendor(String host) {
        if (host == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : TRACKER_VENDORS.entrySet()) {
            if (host.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : null;
            int port = uri.getPort();
            String path = uri.getPath() != null ? uri.getPath() : "/";
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            URI normalized = new URI(scheme, null, host, port, path, uri.getQuery(), null);
            return normalized.normalize().toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String hashJson(Map<String, Object> payload) {
        try {
            String json = canonicalMapper.writeValueAsString(payload);
            return sha256(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<?> capList(List<?> list) {
        if (list == null) {
            return List.of();
        }
        return list.size() > SUMMARY_CAP ? list.subList(0, SUMMARY_CAP) : list;
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private RobotsRules fetchRobots(HttpClient client, String baseUrl, int timeoutMs) {
        try {
            URI base = URI.create(baseUrl);
            URI robotsUri = new URI(base.getScheme(), null, base.getHost(), base.getPort(), "/robots.txt", null, null);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(robotsUri)
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return RobotsRules.parse(response.body());
            }
        } catch (Exception ex) {
            log.debug("Failed to fetch robots.txt: {}", ex.getMessage());
        }
        return RobotsRules.allowAll();
    }

    private static class UrlTask {
        private final String url;
        private final int depth;

        private UrlTask(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }
    }

    private static class ExtractionResult {
        private final List<WebsiteFindingFactory.CookieInfo> cookies = new ArrayList<>();
        private final List<Map<String, Object>> forms = new ArrayList<>();
        private final List<String> sameDomainLinks = new ArrayList<>();
        private final List<Finding> findings = new ArrayList<>();
        private boolean hasPiiFormFields = false;
        private int trackerCount = 0;
        private String sampleText = "";
        private Map<String, Object> summary = Map.of();
    }

    private static class RobotsRules {
        private final List<String> disallow = new ArrayList<>();

        private boolean isAllowed(String url) {
            try {
                URI uri = new URI(url);
                String path = uri.getPath() != null ? uri.getPath() : "/";
                for (String rule : disallow) {
                    if (!rule.isBlank() && path.startsWith(rule)) {
                        return false;
                    }
                }
            } catch (URISyntaxException ignored) {
                return true;
            }
            return true;
        }

        private static RobotsRules allowAll() {
            return new RobotsRules();
        }

        private static RobotsRules parse(String body) {
            RobotsRules rules = new RobotsRules();
            boolean apply = false;
            for (String line : body.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith("user-agent:")) {
                    String agent = trimmed.substring("user-agent:".length()).trim();
                    apply = "*".equals(agent);
                } else if (apply && trimmed.toLowerCase(Locale.ROOT).startsWith("disallow:")) {
                    String path = trimmed.substring("disallow:".length()).trim();
                    if (!path.isBlank()) {
                        rules.disallow.add(path);
                    }
                }
            }
            return rules;
        }
    }
}