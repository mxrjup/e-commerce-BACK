package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.Statement;

/** Keep the shared V1/V2 history intact; only PostgreSQL supports trigram indexes. */
public class V3__faceted_search extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("CREATE INDEX idx_specification_filter ON produit_specification (cle, valeur, produit_id)");
            if ("PostgreSQL".equals(context.getConnection().getMetaData().getDatabaseProductName())) {
                statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
                statement.execute("CREATE INDEX idx_produit_title_trgm ON produit USING gin (lower(titre) gin_trgm_ops)"
                        + " WHERE statut = 'PUBLIE'");
            }
        }
    }
}
