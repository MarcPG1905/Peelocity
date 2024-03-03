package com.marcpg.peelocity.storage;

import com.alessiodp.libby.Library;
import com.alessiodp.libby.VelocityLibraryManager;
import com.marcpg.data.database.sql.AutoCatchingSQLConnection;
import com.marcpg.data.database.sql.SQLConnection;
import com.marcpg.peelocity.Peelocity;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

@SuppressWarnings("unchecked")
public class DatabaseStorage<T> extends Storage<T> {
    // START - Configuration-Based Values
    public static SQLConnection.DatabaseType TYPE;
    public static String ADDRESS;
    public static int PORT;
    public static String NAME;
    public static String USERNAME;
    public static String PASSWORD;

    // END - Configuration-Based Values

    private final AutoCatchingSQLConnection<T> connection;

    public DatabaseStorage(String name, String primaryKeyName) throws SQLException, ClassNotFoundException {
        super(name, primaryKeyName);
        this.connection = new AutoCatchingSQLConnection<>(TYPE, ADDRESS, PORT, NAME, USERNAME, PASSWORD, name, primaryKeyName, e -> Peelocity.LOG.error("Couldn't interact with the " + name + " database: " + e.getMessage()));
        this.createTable(switch (name) {
            case "friendships" -> "uuid UUID PRIMARY KEY, player1 UUID NOT NULL, player2 UUID NOT NULL";
            case "bans" -> "player UUID PRIMARY KEY, permanent BOOLEAN NOT NULL, expires BIGINT NOT NULL, duration BIGINT NOT NULL, reason TEXT NOT NULL";
            case "mutes" -> "player UUID PRIMARY KEY, expires BIGINT NOT NULL, duration BIGINT NOT NULL, reason TEXT NOT NULL";
            case "whitelist" -> "username VARCHAR(20) PRIMARY KEY";
            default -> throw new IllegalStateException("Unexpected table name: " + name);
        });
    }

    private void createTable(String values) throws SQLException {
        String query = (TYPE == SQLConnection.DatabaseType.MS_SQL_SERVER ? "IF OBJECT_ID(N'" + this.name + "', N'U') IS NULL CREATE TABLE " : "CREATE TABLE IF NOT EXISTS ") + this.name + "(" + values + ");";
        this.connection.connection().prepareStatement(query).executeUpdate();
    }

    @Override
    public boolean contains(T key) {
        return this.connection.contains(key);
    }

    @Override
    public void add(@NotNull Map<String, Object> entries) {
        this.connection.add(entries);
    }

    @Override
    public void remove(T key) {
        this.connection.remove(key);
    }

    @Override
    public Map<String, Object> get(T key) {
        return this.connection.getRowMap(key);
    }

    public Collection<Map<String, Object>> get(String predicate, Object... replacements) {
        return this.connection.getRowMapsMatching(predicate, replacements);
    }

    @Override
    public Collection<Map<String, Object>> getAll() {
        return null;
    }

    public static void loadDependency() {
        VelocityLibraryManager<Peelocity> libraryManager = new VelocityLibraryManager<>(Peelocity.INSTANCE, Peelocity.LOG, Peelocity.DATA_DIR, Peelocity.SERVER.getPluginManager());
        libraryManager.addSonatype();

        String[] info = switch (TYPE) {
            case MYSQL -> new String[]{ "com{}mysql", "mysql-connector-j", "8.3.0" };
            case MARIADB -> new String[]{ "org{}mariadb{}jdbc", "mariadb-java-client", "3.3.2" };
            case MS_SQL_SERVER -> new String[]{ "com{}microsoft{}sqlserver", "mssql-jdbc", "12.6.0.jre11" };
            case ORACLE -> new String[]{ "com{}oracle{}database{}jdbc", "ojdbc10", "19.22.0.0" };
            default -> new String[]{ "org{}postgresql", "postgresql", "42.7.1" };
        };
        libraryManager.loadLibrary(Library.builder().groupId(info[0]).artifactId(info[1]).version(info[2]).build());
    }
}
