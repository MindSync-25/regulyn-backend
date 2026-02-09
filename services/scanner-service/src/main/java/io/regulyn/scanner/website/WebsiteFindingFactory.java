package io.regulyn.scanner.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.regulyn.scanner.adapter.model.Finding;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WebsiteFindingFactory {

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private WebsiteFindingFactory() {
    }

    public static Finding cookieFinding(String pageUrl, CookieInfo cookie) {
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("kind", WebsiteFindingKind.COOKIE_PRESENT.name());
        keyAttributes.put("url", pageUrl);
        keyAttributes.put("cookieName", cookie.getName());
        keyAttributes.put("category", cookie.getCategory());

        Map<String, Object> details = Map.of(
            "websiteFindingKind", WebsiteFindingKind.COOKIE_PRESENT.name(),
            "pageUrl", pageUrl,
            "cookieName", cookie.getName(),
            "category", cookie.getCategory()
        );

        Finding finding = baseFinding(pageUrl, keyAttributes, details);
        finding.setFieldName(cookie.getName());
        finding.setDataCategory("COOKIE");
        finding.setRiskLevel("MARKETING".equalsIgnoreCase(cookie.getCategory()) ? "MED" : "LOW");
        finding.setConfidence(80);
        return finding;
    }

    public static Finding formPiiFinding(String pageUrl, String action, int formIndex) {
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("kind", WebsiteFindingKind.FORM_PII_DETECTED.name());
        keyAttributes.put("url", pageUrl);
        keyAttributes.put("formAction", action);
        keyAttributes.put("formIndex", formIndex);

        Map<String, Object> details = Map.of(
            "websiteFindingKind", WebsiteFindingKind.FORM_PII_DETECTED.name(),
            "pageUrl", pageUrl,
            "formAction", action,
            "formIndex", formIndex
        );

        Finding finding = baseFinding(pageUrl, keyAttributes, details);
        finding.setFieldName("form#" + formIndex);
        finding.setDataCategory("FORM");
        finding.setRiskLevel("HIGH");
        finding.setConfidence(90);
        return finding;
    }

    public static Finding insecureFormFinding(String pageUrl, String action, int formIndex) {
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("kind", WebsiteFindingKind.INSECURE_FORM_ACTION_HTTP.name());
        keyAttributes.put("url", pageUrl);
        keyAttributes.put("formAction", action);
        keyAttributes.put("httpAction", true);
        keyAttributes.put("formIndex", formIndex);

        Map<String, Object> details = Map.of(
            "websiteFindingKind", WebsiteFindingKind.INSECURE_FORM_ACTION_HTTP.name(),
            "pageUrl", pageUrl,
            "formAction", action,
            "formIndex", formIndex
        );

        Finding finding = baseFinding(pageUrl, keyAttributes, details);
        finding.setFieldName("form#" + formIndex);
        finding.setDataCategory("FORM");
        finding.setRiskLevel("HIGH");
        finding.setConfidence(85);
        return finding;
    }

    public static Finding trackerFinding(String pageUrl, String domain, String vendor) {
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("kind", WebsiteFindingKind.TRACKER_DETECTED.name());
        keyAttributes.put("url", pageUrl);
        keyAttributes.put("trackerDomain", domain);
        keyAttributes.put("vendor", vendor);

        Map<String, Object> details = Map.of(
            "websiteFindingKind", WebsiteFindingKind.TRACKER_DETECTED.name(),
            "pageUrl", pageUrl,
            "trackerDomain", domain,
            "vendor", vendor
        );

        Finding finding = baseFinding(pageUrl, keyAttributes, details);
        finding.setFieldName(domain);
        finding.setDataCategory("TRACKER");
        finding.setRiskLevel("MED");
        finding.setConfidence(80);
        return finding;
    }

    public static Finding unknownThirdPartyFinding(String pageUrl, String domain) {
        Map<String, Object> keyAttributes = new HashMap<>();
        keyAttributes.put("kind", WebsiteFindingKind.UNKNOWN_THIRD_PARTY_ENDPOINT.name());
        keyAttributes.put("url", pageUrl);
        keyAttributes.put("domain", domain);

        Map<String, Object> details = Map.of(
            "websiteFindingKind", WebsiteFindingKind.UNKNOWN_THIRD_PARTY_ENDPOINT.name(),
            "pageUrl", pageUrl,
            "domain", domain
        );

        Finding finding = baseFinding(pageUrl, keyAttributes, details);
        finding.setFieldName(domain);
        finding.setDataCategory("TRACKER");
        finding.setRiskLevel("MED");
        finding.setConfidence(70);
        return finding;
    }

    private static Finding baseFinding(String pageUrl, Map<String, Object> keyAttributes, Map<String, Object> details) {
        Finding finding = new Finding();
        finding.setFindingType("FIELD");
        finding.setEntityType("WEB_PAGE");
        finding.setDetails(details);
        finding.setNormalizedSubject(normalizeUrl(pageUrl));
        finding.setKeyAttributes(keyAttributes);
        finding.setFindingFingerprintVersion((short) 1);

        String fingerprint = computeFingerprint(
            details.get("websiteFindingKind").toString(),
            finding.getNormalizedSubject(),
            keyAttributes
        );
        finding.setFindingFingerprint(fingerprint);
        return finding;
    }

    private static String computeFingerprint(String kind, String normalizedSubject, Map<String, Object> keyAttributes) {
        try {
            String keyJson = CANONICAL_MAPPER.writeValueAsString(keyAttributes);
            String raw = kind + "|" + normalizedSubject + "|" + keyJson;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.toLowerCase(Locale.ROOT);
    }

    public static class CookieInfo {
        private final String name;
        private final String category;
        private final Map<String, String> attributes;

        public CookieInfo(String name, String category, Map<String, String> attributes) {
            this.name = name;
            this.category = category;
            this.attributes = attributes;
        }

        public String getName() {
            return name;
        }

        public String getCategory() {
            return category;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }
    }
}