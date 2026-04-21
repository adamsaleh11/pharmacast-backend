package ca.pharmaforecast.backend.upload;

import ca.pharmaforecast.backend.dispensing.DailyDispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordImportRepository;
import ca.pharmaforecast.backend.drug.DinEnrichmentService;
import ca.pharmaforecast.backend.drug.DinNormalizer;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.drug.InvalidDinException;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.realtime.SupabaseRealtimeClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CsvProcessingService implements CsvProcessingJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvProcessingService.class);
    private static final String PROCESSING_FAILURE_MESSAGE = "CSV processing failed. Please try uploading again.";

    private static final List<String> MANDATORY_COLUMNS = List.of(
            "dispensed_date",
            "din",
            "quantity_dispensed"
    );
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH_DAY_YEAR = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final CsvUploadRepository csvUploadRepository;
    private final ObjectMapper objectMapper;
    private final DispensingRecordImportRepository dispensingRecordImportRepository;
    private final SupabaseRealtimeClient supabaseRealtimeClient;
    private final DinEnrichmentService dinEnrichmentService;
    private final DinNormalizer dinNormalizer;
    private final DrugRepository drugRepository;
    private final LocationRepository locationRepository;
    private final UploadBacktestPort uploadBacktestPort;

    @Autowired
    public CsvProcessingService(
            CsvUploadRepository csvUploadRepository,
            ObjectMapper objectMapper,
            DispensingRecordImportRepository dispensingRecordImportRepository,
            SupabaseRealtimeClient supabaseRealtimeClient,
            DinEnrichmentService dinEnrichmentService,
            DinNormalizer dinNormalizer,
            DrugRepository drugRepository,
            LocationRepository locationRepository,
            UploadBacktestPort uploadBacktestPort
    ) {
        this.csvUploadRepository = csvUploadRepository;
        this.objectMapper = objectMapper;
        this.dispensingRecordImportRepository = dispensingRecordImportRepository;
        this.supabaseRealtimeClient = supabaseRealtimeClient;
        this.dinEnrichmentService = dinEnrichmentService;
        this.dinNormalizer = dinNormalizer;
        this.drugRepository = drugRepository;
        this.locationRepository = locationRepository;
        this.uploadBacktestPort = uploadBacktestPort;
    }

    public CsvProcessingService(
            CsvUploadRepository csvUploadRepository,
            ObjectMapper objectMapper,
            DispensingRecordImportRepository dispensingRecordImportRepository,
            SupabaseRealtimeClient supabaseRealtimeClient,
            DinEnrichmentService dinEnrichmentService,
            DinNormalizer dinNormalizer,
            DrugRepository drugRepository
    ) {
        this(
                csvUploadRepository,
                objectMapper,
                dispensingRecordImportRepository,
                supabaseRealtimeClient,
                dinEnrichmentService,
                dinNormalizer,
                drugRepository,
                null,
                request -> null
        );
    }

    @Transactional
    @Async("csvProcessingExecutor")
    @Override
    public void process(UUID uploadId, UUID locationId, byte[] csvBytes) {
        CsvUpload upload = csvUploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found"));
        upload.setStatus(CsvUploadStatus.PROCESSING);

        ValidationResult result = null;
        try {
            result = validate(csvBytes);
            if (!result.errors().isEmpty()) {
                upload.setStatus(CsvUploadStatus.ERROR);
                upload.setErrorMessage(toJson(result.errors()));
                upload.setValidationSummary(toJson(result.summary()));
                csvUploadRepository.save(upload);
                supabaseRealtimeClient.broadcastUploadComplete(locationId, uploadId, upload.getStatus(), upload.getValidationSummary());
                return;
            }

            dinEnrichmentService.enrichSync(result.validRows().stream()
                    .map(ValidCsvRow::din)
                    .distinct()
                    .toList());

            ensureDinsExistInDb(result.validRows().stream()
                    .map(ValidCsvRow::din)
                    .distinct()
                    .toList());

            dispensingRecordImportRepository.upsertAll(aggregate(locationId, result.validRows()));
            Map<String, Object> backtestSummary = runUploadBacktest(uploadId, locationId, result.validRows());
            if (backtestSummary != null) {
                result.summary().put("backtest", backtestSummary);
            }
            upload.setStatus(CsvUploadStatus.SUCCESS);
            upload.setValidationSummary(toJson(result.summary()));
            upload.setRowCount((Integer) result.summary().get("total_rows"));
            upload.setDrugCount((Integer) result.summary().get("unique_dins"));
            applyBacktestSummary(upload, backtestSummary);
            csvUploadRepository.save(upload);
            supabaseRealtimeClient.broadcastUploadComplete(locationId, uploadId, upload.getStatus(), upload.getValidationSummary());
        } catch (Exception ex) {
            LOGGER.warn("csv upload processing failed uploadId={} locationId={} error={}", uploadId, locationId, ex.toString());
            upload.setStatus(CsvUploadStatus.ERROR);
            upload.setErrorMessage(PROCESSING_FAILURE_MESSAGE);
            upload.setValidationSummary(toJson(buildProcessingFailureSummary(result)));
            csvUploadRepository.save(upload);
            supabaseRealtimeClient.broadcastUploadComplete(locationId, uploadId, upload.getStatus(), upload.getValidationSummary());
        }
    }

    private ValidationResult validate(byte[] csvBytes) {
        try (
                Reader reader = new InputStreamReader(new ByteArrayInputStream(csvBytes), StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)
        ) {
            Map<String, String> headers = normalizeHeaders(parser.getHeaderMap());
            List<CSVRecord> records = parser.getRecords();
            List<ValidationError> errors = new ArrayList<>();

            for (String column : MANDATORY_COLUMNS) {
                if (!headers.containsKey(column)) {
                    errors.add(new ValidationError(
                            0,
                            column,
                            "",
                            "Column '%s' is missing — please re-export from Kroll using the Dispensing History report".formatted(column)
                    ));
                }
            }

            Set<Integer> invalidRowNumbers = new HashSet<>();
            Set<String> uniqueDins = new HashSet<>();
            List<ValidCsvRow> validCsvRows = new ArrayList<>();
            int nonPreferredDateRows = 0;
            LocalDate dateRangeStart = null;
            LocalDate dateRangeEnd = null;

            if (errors.isEmpty()) {
                for (CSVRecord record : records) {
                    int rowNumber = Math.toIntExact(record.getRecordNumber() + 1);
                    List<ValidationError> rowErrors = validateRecord(record, headers, rowNumber);
                    if (!rowErrors.isEmpty()) {
                        errors.addAll(rowErrors);
                        invalidRowNumbers.add(rowNumber);
                        continue;
                    }

                    String din = dinNormalizer.normalize(value(record, headers, "din"));
                    DateParseResult dateResult = parseDate(value(record, headers, "dispensed_date"));
                    LocalDate dispensedDate = dateResult.date();
                    int quantityDispensed = parseInteger(value(record, headers, "quantity_dispensed"));
                    Integer quantityOnHand = headers.containsKey("quantity_on_hand")
                            ? parseInteger(value(record, headers, "quantity_on_hand"))
                            : null;
                    BigDecimal costPerUnit = parseOptionalCost(record, headers);
                    if (dateResult.nonPreferredFormat()) {
                        nonPreferredDateRows++;
                    }
                    uniqueDins.add(din);
                    validCsvRows.add(new ValidCsvRow(
                            din,
                            dispensedDate,
                            quantityDispensed,
                            quantityOnHand,
                            costPerUnit
                    ));
                    if (dateRangeStart == null || dispensedDate.isBefore(dateRangeStart)) {
                        dateRangeStart = dispensedDate;
                    }
                    if (dateRangeEnd == null || dispensedDate.isAfter(dateRangeEnd)) {
                        dateRangeEnd = dispensedDate;
                    }
                }
            }

            int totalRows = records.size();
            int invalidRows = missingMandatoryColumns(errors) ? totalRows : invalidRowNumbers.size();
            int validRows = totalRows - invalidRows;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total_rows", totalRows);
            summary.put("valid_rows", validRows);
            summary.put("invalid_rows", invalidRows);
            summary.put("unique_dins", uniqueDins.size());
            summary.put("date_range_start", dateRangeStart == null ? null : dateRangeStart.toString());
            summary.put("date_range_end", dateRangeEnd == null ? null : dateRangeEnd.toString());
            List<String> warnings = new ArrayList<>();
            if (nonPreferredDateRows > 0) {
                warnings.add("%d rows use a non-standard date format. Future exports should use YYYY-MM-DD.".formatted(nonPreferredDateRows));
            }
            summary.put("warnings", warnings);

            if (!errors.isEmpty()) {
                validCsvRows.clear();
            }
            return new ValidationResult(errors, summary, validCsvRows);
        } catch (IOException ex) {
            List<ValidationError> errors = List.of(new ValidationError(
                    0,
                    "file",
                    "",
                    "CSV file could not be read. Please re-export from Kroll and try again."
            ));
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total_rows", 0);
            summary.put("valid_rows", 0);
            summary.put("invalid_rows", 0);
            summary.put("unique_dins", 0);
            summary.put("date_range_start", null);
            summary.put("date_range_end", null);
            summary.put("warnings", List.of());
            return new ValidationResult(errors, summary, List.of());
        }
    }

    private List<DailyDispensingRecord> aggregate(UUID locationId, List<ValidCsvRow> rows) {
        Map<AggregateKey, AggregateRow> aggregates = new LinkedHashMap<>();
        for (ValidCsvRow row : rows) {
            AggregateKey key = new AggregateKey(row.din(), row.dispensedDate());
            aggregates.compute(key, (ignored, existing) -> {
                if (existing == null) {
                    return new AggregateRow(
                            row.quantityDispensed(),
                            row.quantityOnHand(),
                            row.costPerUnit()
                    );
                }
                Integer quantityOnHand = existing.quantityOnHand() == null
                        ? row.quantityOnHand()
                        : row.quantityOnHand() == null ? existing.quantityOnHand() : Math.min(existing.quantityOnHand(), row.quantityOnHand());
                return new AggregateRow(
                        existing.quantityDispensed() + row.quantityDispensed(),
                        quantityOnHand,
                        mergeCost(existing.costPerUnit(), row.costPerUnit())
                );
            });
        }

        return aggregates.entrySet().stream()
                .map(entry -> new DailyDispensingRecord(
                        locationId,
                        entry.getKey().din(),
                        entry.getKey().dispensedDate(),
                        entry.getValue().quantityDispensed(),
                        entry.getValue().quantityOnHand(),
                        entry.getValue().costPerUnit()
                ))
                .toList();
    }

    private Map<String, Object> runUploadBacktest(UUID uploadId, UUID locationId, List<ValidCsvRow> rows) {
        if (locationRepository == null) {
            return null;
        }
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location not found"));
        BacktestUploadRequest request = BacktestUploadRequest.xgboostResidualV1(
                location.getOrganizationId(),
                locationId,
                uploadId,
                rows.stream()
                        .map(row -> new BacktestDemandRow(
                                row.dispensedDate().toString(),
                                row.din(),
                                row.quantityDispensed(),
                                row.costPerUnit()
                        ))
                        .toList()
        );
        return uploadBacktestPort.runUploadBacktest(request);
    }

    private void applyBacktestSummary(CsvUpload upload, Map<String, Object> backtestSummary) {
        if (upload == null || backtestSummary == null) {
            return;
        }
        Object modelVersion = backtestSummary.get("model_version");
        if (modelVersion != null) {
            upload.setBacktestModelVersion(modelVersion.toString());
        }
        Object modelPathCounts = backtestSummary.get("model_path_counts");
        if (modelPathCounts != null) {
            upload.setBacktestModelPathCounts(objectMapper.convertValue(
                    modelPathCounts,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {
                    }
            ));
        }
    }

    private List<ValidationError> validateRecord(CSVRecord record, Map<String, String> headers, int rowNumber) {
        List<ValidationError> errors = new ArrayList<>();

        String dateValue = value(record, headers, "dispensed_date");
        if (!parseDate(dateValue).valid()) {
            errors.add(new ValidationError(
                    rowNumber,
                    "dispensed_date",
                    safeValue(dateValue),
                    "Dates in column 'dispensed_date' must be in YYYY-MM-DD format — your file appears to use a different format. Adjust your Kroll export settings."
            ));
        }

        String dinValue = value(record, headers, "din");
        try {
            dinNormalizer.normalize(dinValue);
        } catch (InvalidDinException ex) {
            errors.add(new ValidationError(
                    rowNumber,
                    "din",
                    safeValue(dinValue),
                    "Row %d: DIN '%s' is invalid — DINs must be 1 to 8 numeric digits".formatted(rowNumber, dinValue)
            ));
        }

        String quantityDispensed = value(record, headers, "quantity_dispensed");
        Integer parsedQuantityDispensed = parseInteger(quantityDispensed);
        if (parsedQuantityDispensed == null) {
            errors.add(new ValidationError(
                    rowNumber,
                    "quantity_dispensed",
                    safeValue(quantityDispensed),
                    "Row %d: quantity_dispensed must be a whole number".formatted(rowNumber)
            ));
        } else if (parsedQuantityDispensed < 0) {
            errors.add(new ValidationError(
                    rowNumber,
                    "quantity_dispensed",
                    safeValue(quantityDispensed),
                    "Row %d: quantity_dispensed cannot be negative".formatted(rowNumber)
            ));
        }

        // quantity_on_hand is now optional historical data and ignored by validation.

        if (headers.containsKey("cost_per_unit")) {
            String costPerUnit = value(record, headers, "cost_per_unit");
            if (!costPerUnit.isBlank()) {
                BigDecimal cost = parseDecimal(costPerUnit);
                if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new ValidationError(
                            rowNumber,
                            "cost_per_unit",
                            safeValue(costPerUnit),
                            "Row %d: cost_per_unit must be a positive decimal value".formatted(rowNumber)
                    ));
                }
            }
        }

        return errors;
    }

    private boolean missingMandatoryColumns(List<ValidationError> errors) {
        return errors.stream().anyMatch(error -> MANDATORY_COLUMNS.contains(error.field()) && error.row() == 0);
    }

    private String value(CSVRecord record, Map<String, String> headers, String normalizedHeader) {
        return record.get(headers.get(normalizedHeader)).trim();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private DateParseResult parseDate(String value) {
        LocalDate isoDate = parseDate(value, ISO_DATE);
        if (isoDate != null) {
            return new DateParseResult(true, isoDate, false);
        }

        if (!value.contains("/")) {
            return DateParseResult.invalid();
        }

        LocalDate dayMonthDate = parseDate(value, DAY_MONTH_YEAR);
        LocalDate monthDayDate = parseDate(value, MONTH_DAY_YEAR);
        if (dayMonthDate != null && monthDayDate != null && !dayMonthDate.equals(monthDayDate)) {
            return DateParseResult.invalid();
        }
        if (dayMonthDate != null) {
            return new DateParseResult(true, dayMonthDate, true);
        }
        if (monthDayDate != null) {
            return new DateParseResult(true, monthDayDate, true);
        }
        return DateParseResult.invalid();
    }

    private LocalDate parseDate(String value, DateTimeFormatter formatter) {
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseOptionalCost(CSVRecord record, Map<String, String> headers) {
        if (!headers.containsKey("cost_per_unit")) {
            return null;
        }
        String costPerUnit = value(record, headers, "cost_per_unit");
        if (costPerUnit.isBlank()) {
            return null;
        }
        return parseDecimal(costPerUnit);
    }

    private BigDecimal mergeCost(BigDecimal existing, BigDecimal incoming) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        return existing;
    }

    private Map<String, String> normalizeHeaders(Map<String, Integer> headerMap) {
        Map<String, String> normalized = new HashMap<>();
        for (String header : headerMap.keySet()) {
            normalized.put(header.toLowerCase(Locale.ROOT), header);
        }
        return normalized;
    }

    private Map<String, Object> buildProcessingFailureSummary(ValidationResult result) {
        Map<String, Object> summary = result == null ? new LinkedHashMap<>() : new LinkedHashMap<>(result.summary());
        if (summary.isEmpty()) {
            summary.put("total_rows", 0);
            summary.put("valid_rows", 0);
            summary.put("invalid_rows", 0);
            summary.put("unique_dins", 0);
            summary.put("date_range_start", null);
            summary.put("date_range_end", null);
            summary.put("warnings", List.of());
        }
        summary.put("processing_error", "upload_processing_failed");
        return summary;
    }

    private void ensureDinsExistInDb(List<String> dins) {
        List<String> existingDins = drugRepository.findAll().stream()
                .map(Drug::getDin)
                .toList();
        List<String> missingDins = dins.stream()
                .filter(din -> !existingDins.contains(din))
                .toList();
        for (String din : missingDins) {
            Drug drug = new Drug();
            drug.setDin(din);
            drug.setName("Unknown Drug");
            drug.setStrength("Unknown");
            drug.setForm("Unknown");
            drug.setTherapeuticClass("Unknown");
            drug.setManufacturer("Unknown");
            drug.setLastRefreshedAt(java.time.Instant.now());
            drugRepository.save(drug);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize CSV validation details", ex);
        }
    }

    public record ValidationError(int row, String field, String value, String message) {
    }

    private record ValidationResult(
            List<ValidationError> errors,
            Map<String, Object> summary,
            List<ValidCsvRow> validRows
    ) {
    }

    private record ValidCsvRow(
            String din,
            LocalDate dispensedDate,
            int quantityDispensed,
            Integer quantityOnHand,
            BigDecimal costPerUnit
    ) {
    }

    private record AggregateKey(String din, LocalDate dispensedDate) {
    }

    private record AggregateRow(int quantityDispensed, Integer quantityOnHand, BigDecimal costPerUnit) {
    }

    private record DateParseResult(boolean valid, LocalDate date, boolean nonPreferredFormat) {
        static DateParseResult invalid() {
            return new DateParseResult(false, null, false);
        }
    }
}
