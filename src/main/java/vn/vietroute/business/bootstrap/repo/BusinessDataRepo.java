package vn.vietroute.business.bootstrap.repo;

import java.util.Map;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.carrier.entity.Carrier;

/** SQL native và ánh xạ của BusinessDataRepo; transaction ghi thuộc Service. */
public interface BusinessDataRepo extends Repository<Carrier, Long> {
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
        value = """
        SELECT CAST(PG_ADVISORY_XACT_LOCK(781205611) AS TEXT) AS auth_lock, CAST(PG_ADVISORY_XACT_LOCK(781205690) AS
            TEXT) AS seed_lock
        """,
        nativeQuery = true
    )
    Map<String, Object> lockSeed();

    @Query(
        value = """
        SELECT (
            SELECT COUNT(*)
            FROM {h-schema}carriers
        ) + (
            SELECT COUNT(*)
            FROM JSONB_TO_RECORDSET(CAST(:carriers AS JSONB)) x(code TEXT, name TEXT)
            WHERE NOT EXISTS (
                SELECT 1
                FROM {h-schema}carriers c
                WHERE c.code = x.code
        )
        ) AS scope_count, EXISTS (
            SELECT 1
            FROM JSONB_TO_RECORDSET(CAST(:carriers AS JSONB)) x(code TEXT, name TEXT)
            JOIN {h-schema}carriers c USING(code)
            WHERE c.name <> x.name
        ) AS collision
        """,
        nativeQuery = true
    )
    Map<String, Object> validateFoundation(String carriers);
}
