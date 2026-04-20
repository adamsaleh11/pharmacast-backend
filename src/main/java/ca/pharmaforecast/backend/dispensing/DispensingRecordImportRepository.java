package ca.pharmaforecast.backend.dispensing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;

@Repository
public class DispensingRecordImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public DispensingRecordImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertAll(List<DailyDispensingRecord> records) {
        if (records.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO dispensing_records (
                    location_id,
                    din,
                    dispensed_date,
                    quantity_dispensed,
                    quantity_on_hand,
                    cost_per_unit
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (location_id, din, dispensed_date)
                DO UPDATE SET
                    quantity_dispensed = EXCLUDED.quantity_dispensed,
                    quantity_on_hand = EXCLUDED.quantity_on_hand,
                    cost_per_unit = EXCLUDED.cost_per_unit,
                    updated_at = now()
                """,
                records,
                100,
                (statement, record) -> {
                    statement.setObject(1, record.locationId());
                    statement.setString(2, record.din());
                    statement.setDate(3, Date.valueOf(record.dispensedDate()));
                    statement.setInt(4, record.quantityDispensed());
                    statement.setInt(5, record.quantityOnHand());
                    statement.setBigDecimal(6, record.costPerUnit());
                }
        );
    }
}
