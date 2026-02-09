package io.regulyn.scanner.website;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class WebsitePageSummary {

    private String url;
    private String urlHash;
    private int depth;
    private Instant fetchedAt;
    private Integer httpStatus;
    private String contentType;
    private String title;
    private Long durationMs;
    private String pageSummaryHash;
    private int cookieCount;
    private int formCount;
    private int trackerCount;
    private boolean hasPiiFormFields;
    private Map<String, Object> dataCollectionSummary = new HashMap<>();
    private String sampleText;

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

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
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

    public int getCookieCount() {
        return cookieCount;
    }

    public void setCookieCount(int cookieCount) {
        this.cookieCount = cookieCount;
    }

    public int getFormCount() {
        return formCount;
    }

    public void setFormCount(int formCount) {
        this.formCount = formCount;
    }

    public int getTrackerCount() {
        return trackerCount;
    }

    public void setTrackerCount(int trackerCount) {
        this.trackerCount = trackerCount;
    }

    public boolean isHasPiiFormFields() {
        return hasPiiFormFields;
    }

    public void setHasPiiFormFields(boolean hasPiiFormFields) {
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
}