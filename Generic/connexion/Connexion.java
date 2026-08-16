package Generic.connexion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Logger;

import Generic.exceptions.DatabaseException;

public class Connexion {

    private static final Logger LOG = Logger.getLogger(Connexion.class.getName());

    public static String DATABASENAME = "postgres";
    public static String HOST = "localhost";
    public static String DATABASE = "newtestdao";
    public static String USERNAME = "postgres";
    public static String PASSWORD = "mdpprom15";
    public static int PORT = 5432;

    public Connection getConnect() throws Exception {
        Connection con = null;
        try {
            if (DATABASENAME.equals("postgres")) {
                Class.forName("org.postgresql.Driver");
                con = DriverManager.getConnection("jdbc:postgresql://" + HOST + ":" + PORT + "/" + DATABASE, USERNAME,
                        PASSWORD);
                LOG.fine("[ RC Framework : Postgres Connected ]");
                con.setAutoCommit(false);
            } else if (DATABASENAME.equals("oracle")) {

                Class.forName("oracle.jdbc.driver.OracleDriver");
                con = DriverManager.getConnection("jdbc:oracle:thin:@" + HOST + ":" + PORT + ":orcl", USERNAME,
                        PASSWORD);
                LOG.fine("[ RC Framework : Oracle Connected ]");
                con.setAutoCommit(false);
            }
            return con;

        } catch (Exception e) {
            throw new DatabaseException("Une erreur est survenue durant la connexion à la database :" + e.getMessage());
        }
    }

    public Connection getConnect(boolean setAutoCommit) throws Exception {
        Connection con = getConnect();
        con.setAutoCommit(setAutoCommit);
        return con;
    }

    public static Connection getConnection(boolean setAutoCommit) throws Exception {
        Connexion co = new Connexion();
        Connection con = co.getConnect();
        con.setAutoCommit(setAutoCommit);
        return con;
    }
}
