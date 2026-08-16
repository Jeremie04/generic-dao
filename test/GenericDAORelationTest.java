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
import static org.junit.jupiter.api.Assertions.assertNull;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Verifie par assertions le scenario objet imbrique (has-a) de TestAvance.java :
 * Materiel possede une Categorie via une colonne "id_categorie".
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericDAORelationTest {

    private static final String TABLE_CATEGORIE = "categorie_junit_test";
    private static final String TABLE_MATERIEL = "materiel_junit_test";
    private static Connection con;
    private static int idCategorie;
    private static Object idMateriel;

    @BeforeAll
    static void setUp() throws Exception {
        con = Connexion.getConnection(false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_CATEGORIE + " (id SERIAL PRIMARY KEY, nom VARCHAR(100))",
                true, false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_MATERIEL + " (id SERIAL PRIMARY KEY, "
                        + "designation VARCHAR(100), id_categorie INTEGER REFERENCES " + TABLE_CATEGORIE + "(id))",
                true, false);

        Categorie c = new Categorie();
        c.setTableName(TABLE_CATEGORIE);
        c.setNom("Informatique");
        idCategorie = ((Number) c.save(con, false, true)).intValue();
    }

    @AfterAll
    static void tearDown() throws Exception {
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_MATERIEL, true, false);
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_CATEGORIE, true, false);
        con.close();
    }

    @Test
    @Order(1)
    void saveEcritLaClePrimaireDeLObjetLie() throws Exception {
        Categorie ref = new Categorie();
        ref.setId(idCategorie); // seule la cle primaire est necessaire pour ecrire id_categorie

        Materiel m = new Materiel();
        m.setTableName(TABLE_MATERIEL);
        m.setDesignation("Ordinateur portable");
        m.setCategorie(ref);
        idMateriel = m.save(con, false, true);
        assertNotNull(idMateriel);
    }

    @Test
    @Order(2)
    void selectSansJointureNeRemplitQueLaCleDeLObjetLie() throws Exception {
        Materiel filtre = new Materiel();
        filtre.setTableName(TABLE_MATERIEL);
        Materiel[] resultats = filtre.select(con, false);
        assertEquals(1, resultats.length);
        assertEquals(idCategorie, resultats[0].getCategorie().getId(),
                "sans JOIN, la colonne id_categorie deja presente doit remplir categorie.id");
        assertNull(resultats[0].getCategorie().getNom(),
                "sans JOIN, les autres attributs de l'objet lie doivent rester null");
    }

    @Test
    @Order(3)
    void selectAvecJointureChargeLObjetLieEnEntier() throws Exception {
        String sql = "SELECT " + TABLE_MATERIEL + ".*, " + TABLE_CATEGORIE + ".nom AS nom_categorie "
                + "FROM " + TABLE_MATERIEL + " JOIN " + TABLE_CATEGORIE
                + " ON " + TABLE_MATERIEL + ".id_categorie = " + TABLE_CATEGORIE + ".id";
        Materiel[] resultats = new Materiel().select(con, false, sql);
        assertEquals(1, resultats.length);
        assertEquals("Informatique", resultats[0].getCategorie().getNom(),
                "avec JOIN, le nom de l'objet lie doit etre rempli via l'alias nom_categorie");
    }
}
