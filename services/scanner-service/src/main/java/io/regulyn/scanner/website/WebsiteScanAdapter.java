package io.regulyn.scanner.website;

import io.regulyn.scanner.adapter.ScanAdapter;
import io.regulyn.scanner.adapter.model.Finding;
import io.regulyn.scanner.model.ScanSource;
import io.regulyn.scanner.model.ScannedPageEntity;
import io.regulyn.scanner.repository.ScannedPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component("WEBSITE")
public class WebsiteScanAdapter implements ScanAdapter {

    private static final Logger log = LoggerFactory.getLogger(WebsiteScanAdapter.class);

    private final WebsiteCrawler crawler;
    private final ScannedPageRepository scannedPageRepository;

    public WebsiteScanAdapter(WebsiteCrawler crawler, ScannedPageRepository scannedPageRepository) {
        this.crawler = crawler;
        this.scannedPageRepository = scannedPageRepository;
    }

    public WebsiteCrawlResult crawl(ScanSource source, Instant since, UUID tenantId, UUID runId, String scanMode) {
        if ("RETENTION_CANDIDATES".equals(scanMode)) {
            return WebsiteCrawlResult.empty();
        }

        WebsiteCrawlConfig config = WebsiteCrawlConfig.fromSource(source);
        WebsiteCrawlResult result = crawler.crawl(config);

        for (WebsitePageSummary page : result.getPages()) {
            ScannedPageEntity entity = new ScannedPageEntity();
            entity.setTenantId(tenantId);
            entity.setSourceId(source.getSourceId());
            entity.setRunId(runId);
            entity.setUrl(page.getUrl());
            entity.setUrlHash(page.getUrlHash());
            entity.setDepth(page.getDepth());
            entity.setFetchedAt(page.getFetchedAt());
            entity.setHttpStatus(page.getHttpStatus());
            entity.setContentType(page.getContentType());
            entity.setTitle(page.getTitle());
            entity.setDurationMs(page.getDurationMs());
            entity.setPageSummaryHash(page.getPageSummaryHash());
            entity.setCookieCount(page.getCookieCount());
            entity.setFormCount(page.getFormCount());
            entity.setTrackerCount(page.getTrackerCount());
            entity.setHasPiiFormFields(page.isHasPiiFormFields());
            entity.setDataCollectionSummary(page.getDataCollectionSummary());
            entity.setSampleText(page.getSampleText());

            try {
                scannedPageRepository.save(entity);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Duplicate scanned page ignored for run {}", runId);
            }
        }

        return result;
    }

    @Override
    public List<Finding> runInventory(ScanSource source, Instant since) {
        return Collections.emptyList();
    }

    @Override
    public List<Finding> runRetentionCandidates(ScanSource source, Instant since) {
        return Collections.emptyList();
    }
}