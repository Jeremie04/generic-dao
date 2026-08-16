package test;

import java.sql.Connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Verifie par assertions le scenario heritage de TestAvance.java : Employe herite ses
 * champs (dont la cle primaire) de Personne.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericDAOInheritanceTest {

    private static final String TABLE = "employe_junit_test";
    private static Connection con;
    private static Object idEmploye;

    @BeforeAll
    static void setUp() throws Exception {
        con = Connexion.getConnection(false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE + " (id SERIAL PRIMARY KEY, nom VARCHAR(100), "
                        + "email VARCHAR(100), poste VARCHAR(100), salaire NUMERIC)",
                true, false);
    }

    @AfterAll
    static void tearDown() throws Exception {
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE, true, false);
        con.close();
    }

    private static Employe employe() {
        Employe e = new Employe();
        e.setTableName(TABLE);
        return e;
    }

    private static Employe trouverParId(Object id) throws Exception {
        Employe e = employe().findById(con, id, false);
        e.setTableName(TABLE);
        return e;
    }

    @Test
    @Order(1)
    void saveEcritLesChampsHeritesEtPropres() throws Exception {
        Employe e = employe();
        e.setNom("Rakoto");
        e.setEmail("rakoto@example.com");
        e.setPoste("Developpeur");
        e.setSalaire(1500000);
        idEmploye = e.save(con, false, true);
        assertNotNull(idEmploye);
    }

    @Test
    @Order(2)
    void findByIdRempliLaClePrimaireEtLesChampsHerites() throws Exception {
        // Regression : la cle primaire (declaree dans Personne) et nom/email restaient
        // toujours null avant la correction de ResultSetMapper.setRowFromResultSet.
        Employe trouve = trouverParId(idEmploye);
        assertEquals("Rakoto", trouve.getNom());
        assertEquals("rakoto@example.com", trouve.getEmail());
        assertEquals("Developpeur", trouve.getPoste());
    }

    @Test
    @Order(3)
    void updateFonctionneAvecUneClePrimaireHeritee() throws Exception {
        // Regression : prepareAcondition suffixait "id" en "idPersonne" pour un champ herite,
        // ce qui cassait la clause WHERE generee par findById()/select().
        Employe trouve = trouverParId(idEmploye);
        trouve.setSalaire(1600000);
        trouve.update(con, idEmploye, false, true);

        assertEquals(1600000.0, trouverParId(idEmploye).getSalaire(), 0.01);
    }

    @Test
    @Order(4)
    void deleteSupprimeLaLigne() throws Exception {
        employe().delete(con, idEmploye, false, true);
        assertEquals(0, employe().getTableSize(con, Employe.class));
    }
}
