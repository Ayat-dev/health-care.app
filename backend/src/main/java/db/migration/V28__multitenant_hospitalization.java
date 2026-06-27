package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * V28 — Multi-tenant (P4.2) : isolation de l'hospitalisation. Ajoute {@code clinic_id}
 * ({@code @TenantId}) sur {@code rooms} + {@code hospitalizations}, et remplace l'UNIQUE
 * GLOBAL d'origine sur {@code rooms.room_number} (posé inline en V10) par un UNIQUE
 * COMPOSITE {@code (clinic_id, room_number)} → deux cliniques peuvent réutiliser « 101 ».
 * <p>
 * Migration <b>Java</b> (et non SQL) car le DROP d'une contrainte inline <i>non nommée</i>
 * n'est pas portable en SQL statique : H2 et PostgreSQL l'auto-nomment différemment. On
 * retrouve son nom via {@code INFORMATION_SCHEMA} (portable) puis on la supprime par son nom.
 * Le reste suit le schéma « 3 temps » des migrations SQL sœurs (V25–V27).
 */
public class V28__multitenant_hospitalization extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection(); // géré par Flyway — ne pas fermer / committer
        try (Statement st = conn.createStatement()) {

            // 1. clinic_id sur rooms + hospitalizations (3 temps, robuste même avec données existantes).
            for (String table : new String[]{"rooms", "hospitalizations"}) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN clinic_id BIGINT");
                st.execute("UPDATE " + table + " SET clinic_id = "
                        + "(SELECT id FROM clinics WHERE code = 'CENTRALE') WHERE clinic_id IS NULL");
                st.execute("ALTER TABLE " + table + " ALTER COLUMN clinic_id SET NOT NULL");
                st.execute("ALTER TABLE " + table + " ADD CONSTRAINT fk_" + table + "_clinic "
                        + "FOREIGN KEY (clinic_id) REFERENCES clinics (id)");
                st.execute("CREATE INDEX idx_" + table + "_clinic ON " + table + " (clinic_id)");
            }

            // 2. Supprime l'UNIQUE global sur rooms(room_number) — nom retrouvé par introspection.
            String uniqueName = findSingleColumnUnique(conn, "ROOMS", "ROOM_NUMBER");
            if (uniqueName == null) {
                throw new IllegalStateException(
                        "UNIQUE attendu sur rooms(room_number) introuvable — migration V28 interrompue.");
            }
            st.execute("ALTER TABLE rooms DROP CONSTRAINT " + uniqueName);

            // 3. UNIQUE composite (clinic_id, room_number) — numéros réutilisables par clinique.
            st.execute("ALTER TABLE rooms ADD CONSTRAINT uq_rooms_clinic_number "
                    + "UNIQUE (clinic_id, room_number)");
        }
    }

    /**
     * Nom de la contrainte UNIQUE mono-colonne sur {@code table(column)}, via {@code INFORMATION_SCHEMA}
     * (portable H2 + PostgreSQL). Identifiants comparés en MAJUSCULES (H2 stocke en MAJ, PG en min).
     */
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
