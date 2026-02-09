package io.regulyn.scanner.website;

import io.regulyn.scanner.adapter.model.Finding;

import java.util.ArrayList;
import java.util.List;

public class WebsiteCrawlResult {

    private final List<WebsitePageSummary> pages = new ArrayList<>();
    private final List<Finding> findings = new ArrayList<>();
    private boolean partial;
    private String partialReason;
    private int failures;
    private boolean failed;
    private String failureReason;

    public static WebsiteCrawlResult empty() {
        return new WebsiteCrawlResult();
    }

    public List<WebsitePageSummary> getPages() {
        return pages;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public String getPartialReason() {
        return partialReason;
    }

    public void setPartialReason(String partialReason) {
        this.partialReason = partialReason;
    }

    public int getFailures() {
        return failures;
    }

    public void setFailures(int failures) {
        this.failures = failures;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getFetchedPages() {
        return pages.size();
    }
}