package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.upload.CsvProcessingService;
import ca.pharmaforecast.backend.upload.CsvUpload;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import ca.pharmaforecast.backend.upload.CsvUploadStatus;
import ca.pharmaforecast.backend.dispensing.DailyDispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordImportRepository;
import ca.pharmaforecast.backend.drug.DinNormalizer;
import ca.pharmaforecast.backend.drug.DinEnrichmentService;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.realtime.SupabaseRealtimeClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class CsvProcessingServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DinNormalizer dinNormalizer = new DinNormalizer();

    @Test
    void missingMandatoryColumnMarksUploadErrorWithSafeValidationDetails() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        CsvUpload upload = upload(uploadId, locationId);
        CsvUploadRepository uploadRepository = mock(CsvUploadRepository.class);
        DispensingRecordImportRepository importRepository = mock(DispensingRecordImportRepository.class);
        SupabaseRealtimeClient realtimeClient = mock(SupabaseRealtimeClient.class);
        DinEnrichmentService dinEnrichmentService = mock(DinEnrichmentService.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        CsvProcessingService service = new CsvProcessingService(
                uploadRepository,
                objectMapper,
                importRepository,
                realtimeClient,
                dinEnrichmentService,
                dinNormalizer,
                drugRepository
        );

        service.process(uploadId, locationId, """
                dispensed_date,din,quantity_on_hand,patient_id
                2026-04-19,00012345,20,patient-123
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(upload.getStatus()).isEqualTo(CsvUploadStatus.ERROR);
        assertThat(upload.getErrorMessage()).contains("Column 'quantity_dispensed' is missing");
        assertThat(upload.getErrorMessage()).contains("Dispensing History report");
        assertThat(upload.getErrorMessage()).doesNotContain("patient-123");

        JsonNode summary = objectMapper.readTree(upload.getValidationSummary());
        assertThat(summary.get("total_rows").asInt()).isEqualTo(1);
        assertThat(summary.get("valid_rows").asInt()).isZero();
        assertThat(summary.get("invalid_rows").asInt()).isEqualTo(1);

        verify(uploadRepository).save(upload);
        verify(importRepository, never()).upsertAll(anyList());
        verify(dinEnrichmentService, never()).enrichSync(anyList());
        verify(realtimeClient).broadcastUploadComplete(eq(locationId), eq(uploadId), eq(CsvUploadStatus.ERROR), eq(upload.getValidationSummary()));
    }

    @Test
    void invalidRowsCollectAllSafeErrorsAndRejectWholeUpload() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        CsvUpload upload = upload(uploadId, locationId);
        CsvUploadRepository uploadRepository = mock(CsvUploadRepository.class);
        DispensingRecordImportRepository importRepository = mock(DispensingRecordImportRepository.class);
        SupabaseRealtimeClient realtimeClient = mock(SupabaseRealtimeClient.class);
        DinEnrichmentService dinEnrichmentService = mock(DinEnrichmentService.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        CsvProcessingService service = new CsvProcessingService(
                uploadRepository,
                objectMapper,
                importRepository,
                realtimeClient,
                dinEnrichmentService,
                dinNormalizer,
                drugRepository
        );

        service.process(uploadId, locationId, """
                dispensed_date,din,quantity_dispensed,quantity_on_hand,cost_per_unit,patient_id
                19-04-2026,ABC12345,-1,8,-0.50,patient-123
                2026-04-20,00012345,0,7,1.25,patient-456
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(upload.getStatus()).isEqualTo(CsvUploadStatus.ERROR);
        assertThat(upload.getErrorMessage()).contains("Dates in column 'dispensed_date' must be in YYYY-MM-DD format");
        assertThat(upload.getErrorMessage()).contains("Row 2: DIN 'ABC12345' is invalid");
        assertThat(upload.getErrorMessage()).contains("Row 2: quantity_dispensed cannot be negative");
        assertThat(upload.getErrorMessage()).contains("cost_per_unit");
        assertThat(upload.getErrorMessage()).doesNotContain("patient-123");
        assertThat(upload.getErrorMessage()).doesNotContain("patient-456");

        JsonNode summary = objectMapper.readTree(upload.getValidationSummary());
        assertThat(summary.get("total_rows").asInt()).isEqualTo(2);
        assertThat(summary.get("valid_rows").asInt()).isEqualTo(1);
        assertThat(summary.get("invalid_rows").asInt()).isEqualTo(1);
        assertThat(summary.get("unique_dins").asInt()).isEqualTo(1);
        assertThat(summary.get("date_range_start").asText()).isEqualTo("2026-04-20");
        assertThat(summary.get("date_range_end").asText()).isEqualTo("2026-04-20");
        verify(importRepository, never()).upsertAll(anyList());
        verify(dinEnrichmentService, never()).enrichSync(anyList());
        verify(realtimeClient).broadcastUploadComplete(eq(locationId), eq(uploadId), eq(CsvUploadStatus.ERROR), eq(upload.getValidationSummary()));
    }

    @Test
    void validRowsAggregateByLocationDinAndDateBeforeImport() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        CsvUpload upload = upload(uploadId, locationId);
        CsvUploadRepository uploadRepository = mock(CsvUploadRepository.class);
        DispensingRecordImportRepository importRepository = mock(DispensingRecordImportRepository.class);
        SupabaseRealtimeClient realtimeClient = mock(SupabaseRealtimeClient.class);
        DinEnrichmentService dinEnrichmentService = mock(DinEnrichmentService.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        CsvProcessingService service = new CsvProcessingService(
                uploadRepository,
                objectMapper,
                importRepository,
                realtimeClient,
                dinEnrichmentService,
                dinNormalizer,
                drugRepository
        );

        service.process(uploadId, locationId, """
                dispensed_date,din,quantity_dispensed,quantity_on_hand,cost_per_unit,patient_id
                2026-04-19,00012345,3,20,1.25,patient-123
                2026-04-19,00012345,4,16,1.25,patient-456
                2026-04-20,00099999,0,8,,patient-789
                """.getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyDispensingRecord>> records = ArgumentCaptor.forClass((Class<List<DailyDispensingRecord>>) (Class<?>) List.class);
        verify(importRepository).upsertAll(records.capture());

        assertThat(records.getValue()).containsExactlyInAnyOrder(
                new DailyDispensingRecord(locationId, "00012345", LocalDate.parse("2026-04-19"), 7, 16, new BigDecimal("1.25")),
                new DailyDispensingRecord(locationId, "00099999", LocalDate.parse("2026-04-20"), 0, 8, null)
        );
        assertThat(upload.getStatus()).isEqualTo(CsvUploadStatus.SUCCESS);
        assertThat(upload.getRowCount()).isEqualTo(3);
        assertThat(upload.getDrugCount()).isEqualTo(2);

        JsonNode summary = objectMapper.readTree(upload.getValidationSummary());
        assertThat(summary.get("total_rows").asInt()).isEqualTo(3);
        assertThat(summary.get("valid_rows").asInt()).isEqualTo(3);
        assertThat(summary.get("invalid_rows").asInt()).isZero();
        assertThat(summary.get("unique_dins").asInt()).isEqualTo(2);
        verify(dinEnrichmentService).enrichSync(List.of("00012345", "00099999"));
        verify(realtimeClient).broadcastUploadComplete(eq(locationId), eq(uploadId), eq(CsvUploadStatus.SUCCESS), eq(upload.getValidationSummary()));
    }

    @Test
    void validShortNumericDinsAreStoredAndEnrichedCanonically() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        CsvUpload upload = upload(uploadId, locationId);
        CsvUploadRepository uploadRepository = mock(CsvUploadRepository.class);
        DispensingRecordImportRepository importRepository = mock(DispensingRecordImportRepository.class);
        SupabaseRealtimeClient realtimeClient = mock(SupabaseRealtimeClient.class);
DinEnrichmentService dinEnrichmentService = mock(DinEnrichmentService.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        CsvProcessingService service = new CsvProcessingService(
                uploadRepository,
                objectMapper,
                importRepository,
                realtimeClient,
                dinEnrichmentService,
                dinNormalizer,
                drugRepository
        );

        service.process(uploadId, locationId, """
                dispensed_date,din,quantity_dispensed,quantity_on_hand,cost_per_unit
                2026-04-20,123,10,5,0.50
                2026-04-20,00099999,20,15,1.00
                """.getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyDispensingRecord>> records = ArgumentCaptor.forClass((Class<List<DailyDispensingRecord>>) (Class<?>) List.class);
        verify(importRepository).upsertAll(records.capture());

        assertThat(records.getValue()).containsExactly(
                new DailyDispensingRecord(locationId, "00012345", LocalDate.parse("2026-04-19"), 3, 20, null)
        );
        verify(dinEnrichmentService).enrichSync(List.of("00012345"));
    }

    @Test
    void nonPreferredDateFormatsAreAcceptedWithWarningsButAmbiguousDatesFail() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        CsvUpload upload = upload(uploadId, locationId);
        CsvUploadRepository uploadRepository = mock(CsvUploadRepository.class);
        DispensingRecordImportRepository importRepository = mock(DispensingRecordImportRepository.class);
        SupabaseRealtimeClient realtimeClient = mock(SupabaseRealtimeClient.class);
DinEnrichmentService dinEnrichmentService = mock(DinEnrichmentService.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        when(uploadRepository.findById(uploadId)).thenReturn(Optional.of(upload));

        CsvProcessingService service = new CsvProcessingService(
                uploadRepository,
                objectMapper,
                importRepository,
                realtimeClient,
                dinEnrichmentService,
                dinNormalizer,
                drugRepository
        );

        service.process(uploadId, locationId, """
                dispensed_date,din,quantity_dispensed,quantity_on_hand,cost_per_unit
                2026-04-20,00012345,3,20,0.50
                2026-04-19,00012345,3,20,0.50
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(upload.getStatus()).isEqualTo(CsvUploadStatus.ERROR);
        assertThat(upload.getErrorMessage()).contains("Dates in column 'dispensed_date' must be in YYYY-MM-DD format");

        JsonNode summary = objectMapper.readTree(upload.getValidationSummary());
        assertThat(summary.get("total_rows").asInt()).isEqualTo(2);
        assertThat(summary.get("valid_rows").asInt()).isEqualTo(1);
        assertThat(summary.get("invalid_rows").asInt()).isEqualTo(1);
        assertThat(summary.get("warnings").get(0).asText()).contains("non-standard date format");
        verify(importRepository, never()).upsertAll(anyList());
    }

    private CsvUpload upload(UUID id, UUID locationId) throws Exception {
        CsvUpload upload = ReflectionUtils.accessibleConstructor(CsvUpload.class).newInstance();
        ReflectionTestUtils.setField(upload, "id", id);
        upload.setLocationId(locationId);
        upload.setFilename("dispensing-history.csv");
        upload.setStatus(CsvUploadStatus.PENDING);
        upload.setUploadedAt(Instant.parse("2026-04-19T12:00:00Z"));
        return upload;
    }
}
