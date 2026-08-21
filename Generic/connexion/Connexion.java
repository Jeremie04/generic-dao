package Generic.connexion;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import Generic.exceptions.DatabaseException;

/**
 * Point d'acces aux connexions JDBC, adosse a un pool HikariCP partage par toute la JVM et
 * cree une seule fois (a la premiere connexion demandee) plutot qu'une connexion physique
 * neuve a chaque appel. Voir la section "Pool de connexions" du README pour l'utilisation
 * et le detail des reglages.
 */
public class Connexion {

    private static final Logger LOG = Logger.getLogger(Connexion.class.getName());

    public static String DATABASENAME = "postgres";
    public static String HOST = "localhost";
    public static String DATABASE = "newtestdao";
    public static String USERNAME = "postgres";
    public static String PASSWORD = "mdpprom15";
    public static int PORT = 5432;

    // Reglages du pool : lus une seule fois, a la creation du pool (premiere connexion
    // demandee, quel que soit l'endroit d'ou elle vient). Les modifier apres coup n'a plus
    // d'effet sur un pool deja demarre.
    public static int MAXIMUM_POOL_SIZE = 10;
    public static long CONNECTION_TIMEOUT_MS = 30_000;

    /**
     * Initialization-on-demand holder : le pool n'est construit qu'au tout premier appel a
     * getConnect(), de facon thread-safe sans aucune synchronisation au runtime (garantie du
     * chargement de classe de la JVM) — le lazy-singleton le moins couteux disponible en Java.
     */
    private static final class PoolHolder {
        private static final HikariDataSource DATA_SOURCE = createDataSource();

        private static HikariDataSource createDataSource() {
            HikariConfig config = new HikariConfig();
            config.setPoolName("GenericDAO-Pool");
            // Taille fixe : HikariCP recommande officiellement de ne pas regler minimumIdle
            // differemment de maximumPoolSize, pour eviter le cout de creation/destruction de
            // connexions en reaction a la charge plutot que de garder un pool stable et pret.
            config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
            config.setMinimumIdle(MAXIMUM_POOL_SIZE);
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            // Remplace les appels repetes a con.setAutoCommit(false) : chaque connexion rendue
            // par le pool a deja ce reglage (Hikari le reapplique automatiquement aux connexions
            // rendues au pool avant de les re-preter, meme si un appelant l'a change entre-temps).
            config.setAutoCommit(false);

            try {
                if (DATABASENAME.equals("oracle")) {
                    Class.forName("oracle.jdbc.driver.OracleDriver");
                    config.setJdbcUrl("jdbc:oracle:thin:@" + HOST + ":" + PORT + ":orcl");
                } else {
                    Class.forName("org.postgresql.Driver");
                    config.setJdbcUrl("jdbc:postgresql://" + HOST + ":" + PORT + "/" + DATABASE);
                    // Reglages officiellement recommandes par HikariCP pour PostgreSQL : active
                    // le cache de requetes preparees cote pilote/serveur. Beneficie directement
                    // au motif d'usage de GenericDAO, qui repete constamment les memes formes de
                    // SQL (save/select/update/delete) avec des parametres differents.
                    config.addDataSourceProperty("cachePrepStmts", "true");
                    config.addDataSourceProperty("prepStmtCacheSize", "250");
                    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    config.addDataSourceProperty("useServerPrepStmts", "true");
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Driver JDBC introuvable : " + e.getMessage(), e);
            }

            config.setUsername(USERNAME);
            config.setPassword(PASSWORD);
            return new HikariDataSource(config);
        }
    }

    /**
     * Emprunte une connexion au pool partage (autoCommit desactive par defaut). Le pool est
     * cree au tout premier appel a partir des champs DATABASENAME/HOST/... courants ; les
     * modifier apres coup n'a plus d'effet (voir {@link #shutdownPool()} pour en repartir).
     */
    public Connection getConnect() throws Exception {
        try {
            Connection con = PoolHolder.DATA_SOURCE.getConnection();
            LOG.fine(() -> "[ RC Framework : " + DATABASENAME + " connexion empruntee au pool ]");
            return con;
        } catch (SQLException e) {
            throw new DatabaseException(
                    "Une erreur est survenue durant l'emprunt d'une connexion au pool : " + e.getMessage(), e);
        }
    }

    public Connection getConnect(boolean setAutoCommit) throws Exception {
        Connection con = getConnect();
        con.setAutoCommit(setAutoCommit);
        return con;
    }

    public static Connection getConnection(boolean setAutoCommit) throws Exception {
        return new Connexion().getConnect(setAutoCommit);
    }

    /**
     * Ferme le pool et toutes ses connexions physiques. A appeler explicitement a l'arret
     * propre d'une application longue duree (serveur, ...). Inutile pour un programme one-shot
     * comme les classes test.* de ce depot, qui peuvent laisser le pool mourir avec le process
     * JVM — mais elles l'appellent quand meme par propreté (voir test/Test.java).
     */
    public static void shutdownPool() {
        if (!PoolHolder.DATA_SOURCE.isClosed()) {
            PoolHolder.DATA_SOURCE.close();
        }
    }
}
