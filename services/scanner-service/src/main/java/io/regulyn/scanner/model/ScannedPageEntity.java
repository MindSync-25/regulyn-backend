package io.regulyn.scanner.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "scanned_pages", schema = "scanner")
public class ScannedPageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "page_id")
    private UUID pageId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "depth", nullable = false)
    private Integer depth = 0;

    @Column(name = "fetched_at")
    private Instant fetchedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "title")
    private String title;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "page_summary_hash", length = 64)
    private String pageSummaryHash;

    @Column(name = "cookie_count", nullable = false)
    private Integer cookieCount = 0;

    @Column(name = "form_count", nullable = false)
    private Integer formCount = 0;

    @Column(name = "tracker_count", nullable = false)
    private Integer trackerCount = 0;

    @Column(name = "has_pii_form_fields", nullable = false)
    private Boolean hasPiiFormFields = false;

    @Type(JsonBinaryType.class)
    @Column(name = "data_collection_summary", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> dataCollectionSummary = new HashMap<>();

    @Column(name = "sample_text")
    private String sampleText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getPageId() {
        return pageId;
    }

    public void setPageId(UUID pageId) {
        this.pageId = pageId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public void setUrlHash(String urlHash) {
        this.urlHash = urlHash;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getPageSummaryHash() {
        return pageSummaryHash;
    }

    public void setPageSummaryHash(String pageSummaryHash) {
        this.pageSummaryHash = pageSummaryHash;
    }

    public Integer getCookieCount() {
        return cookieCount;
    }

    public void setCookieCount(Integer cookieCount) {
        this.cookieCount = cookieCount;
    }

    public Integer getFormCount() {
        return formCount;
    }

    public void setFormCount(Integer formCount) {
        this.formCount = formCount;
    }

    public Integer getTrackerCount() {
        return trackerCount;
    }

    public void setTrackerCount(Integer trackerCount) {
        this.trackerCount = trackerCount;
    }

    public Boolean getHasPiiFormFields() {
        return hasPiiFormFields;
    }

    public void setHasPiiFormFields(Boolean hasPiiFormFields) {
        this.hasPiiFormFields = hasPiiFormFields;
    }

    public Map<String, Object> getDataCollectionSummary() {
        return dataCollectionSummary;
    }

    public void setDataCollectionSummary(Map<String, Object> dataCollectionSummary) {
        this.dataCollectionSummary = dataCollectionSummary;
    }

    public String getSampleText() {
        return sampleText;
    }

    public void setSampleText(String sampleText) {
        this.sampleText = sampleText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}