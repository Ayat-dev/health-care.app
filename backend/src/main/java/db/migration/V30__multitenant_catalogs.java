package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * V30 — Multi-tenant (P4.2) : catalogues par clinique. Pose {@code clinic_id} ({@code @TenantId})
 * sur departments / act_catalog / lab_test_catalog / insurance_providers / radiology_exam_catalog,
 * et remplace l'{@code UNIQUE} GLOBAL d'origine sur leur colonne {@code code} (inline, auto-nommé)
 * par un {@code UNIQUE} COMPOSITE {@code (clinic_id, code)} → chaque clinique réutilise les mêmes codes.
 * <p>
 * {@code icd10_catalog} reste PARTAGÉ (standard international, choix produit). Migration <b>Java</b>
 * pour droper les UNIQUE non nommés portablement (cf. V28) — généralisée sur les 5 tables.
 */
public class V30__multitenant_catalogs extends BaseJavaMigration {

    /** {table, colonne unique → composite (clinic_id, colonne)}. */
    private static final String[][] CATALOGS = {
            {"departments",            "code"},
            {"act_catalog",            "code"},
            {"lab_test_catalog",       "code"},
            {"insurance_providers",    "code"},
            {"radiology_exam_catalog", "code"},
    };

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection(); // géré par Flyway — ne pas fermer / committer
        try (Statement st = conn.createStatement()) {
            for (String[] cat : CATALOGS) {
                String table = cat[0];
                String col = cat[1];

                // 1. clinic_id (3 temps, rétro-remplissage CENTRALE — robuste même avec données existantes).
                st.execute("ALTER TABLE " + table + " ADD COLUMN clinic_id BIGINT");
                st.execute("UPDATE " + table + " SET clinic_id = "
                        + "(SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL");
                st.execute("ALTER TABLE " + table + " ALTER COLUMN clinic_id SET NOT NULL");
                st.execute("ALTER TABLE " + table + " ADD CONSTRAINT fk_" + table + "_clinic "
                        + "FOREIGN KEY (clinic_id) REFERENCES clinics (id)");
                st.execute("CREATE INDEX idx_" + table + "_clinic ON " + table + " (clinic_id)");

                // 2. Supprime l'UNIQUE global sur <col> — nom retrouvé par introspection (portable H2/PG).
                String uniqueName = findSingleColumnUnique(conn, table.toUpperCase(), col.toUpperCase());
                if (uniqueName != null) {
                    st.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + uniqueName);
                }

                // 3. UNIQUE composite (clinic_id, <col>) — codes réutilisables par clinique.
                st.execute("ALTER TABLE " + table + " ADD CONSTRAINT uq_" + table + "_clinic_" + col
                        + " UNIQUE (clinic_id, " + col + ")");
            }
        }
    }

    /** Nom de la contrainte UNIQUE mono-colonne sur {@code table(column)}, via INFORMATION_SCHEMA (H2 + PG). */
    private String findSingleColumnUnique(Connection conn, String table, String column) throws Exception {
        String sql =
                "SELECT tc.constraint_name "
              + "FROM information_schema.table_constraints tc "
              + "JOIN information_schema.key_column_usage kcu "
              + "  ON tc.constraint_name = kcu.constraint_name "
              + " AND tc.table_schema   = kcu.table_schema "
              + " AND tc.table_name     = kcu.table_name "
              + "WHERE tc.constraint_type = 'UNIQUE' "
              + "  AND UPPER(tc.table_name)  = ? "
              + "  AND UPPER(kcu.column_name) = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
