package com.regulyn.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulyn.evidence.entity.EvidenceBundle;
import com.regulyn.evidence.entity.EvidenceBundleItem;
import com.regulyn.evidence.entity.EvidenceRecord;
import com.regulyn.evidence.repository.EvidenceBundleItemRepository;
import com.regulyn.evidence.repository.EvidenceRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ExportService {

    private final EvidenceBundleItemRepository itemRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${evidence.export.baseDir:./data/evidence-exports}")
    private String exportBaseDir;

    public ExportService(
            EvidenceBundleItemRepository itemRepository,
            EvidenceRecordRepository evidenceRecordRepository,
            ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.objectMapper = objectMapper;
    }

    public byte[] generateExportZip(EvidenceBundle bundle) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add manifest.json
            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            String manifestJson = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(bundle.getManifestJson());
            zos.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add checksums.txt
            StringBuilder checksums = new StringBuilder();
            checksums.append("# Evidence Bundle Export Checksums\n");
            checksums.append("bundle_id: ").append(bundle.getBundleId()).append("\n");
            checksums.append("bundle_hash: ").append(bundle.getBundleHash()).append("\n\n");

            // Add evidence records
            List<EvidenceBundleItem> items = itemRepository.findByBundleIdAndTenantId(
                    bundle.getBundleId(), 
                    bundle.getTenantId()
            );

            for (EvidenceBundleItem item : items) {
                if ("EVIDENCE".equals(item.getItemType())) {
                    EvidenceRecord evidence = evidenceRecordRepository
                            .findByEvidenceIdAndTenantId(item.getEvidenceId().toString(), bundle.getTenantId())
                            .orElseThrow();

                    // Create evidence/<evidenceId>.json
                    String evidencePath = "evidence/" + evidence.getEvidenceId() + ".json";
                    ZipEntry evidenceEntry = new ZipEntry(evidencePath);
                    zos.putNextEntry(evidenceEntry);
                    
                    Map<String, Object> evidenceData = new LinkedHashMap<>();
                    evidenceData.put("evidenceId", evidence.getEvidenceId().toString());
                    evidenceData.put("evidenceType", evidence.getEvidenceType());
                    evidenceData.put("evidenceHash", evidence.getEvidenceHash());
                    evidenceData.put("metadata", evidence.getMetadata());
                    evidenceData.put("createdAt", evidence.getCreatedAt().toString());
                    
                    String evidenceJson = objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(evidenceData);
                    zos.write(evidenceJson.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();

                    checksums.append(evidencePath).append(": ").append(evidence.getEvidenceHash()).append("\n");
                }
                
                // TODO: Add artifacts when artifact storage is implemented
            }

            // Add checksums.txt
            ZipEntry checksumsEntry = new ZipEntry("checksums.txt");
            zos.putNextEntry(checksumsEntry);
            zos.write(checksums.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    public String saveExport(UUID tenantId, UUID exportId, byte[] zipBytes) throws Exception {
        Path tenantDir = Paths.get(exportBaseDir, tenantId.toString());
        Files.createDirectories(tenantDir);
        
        Path exportPath = tenantDir.resolve(exportId + ".zip");
        Files.write(exportPath, zipBytes);
        
        return exportPath.toString();
    }

    public byte[] readExport(UUID tenantId, UUID exportId) throws Exception {
        Path exportPath = Paths.get(exportBaseDir, tenantId.toString(), exportId + ".zip");
        return Files.readAllBytes(exportPath);
    }
}
