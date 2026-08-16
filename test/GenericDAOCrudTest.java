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
import Generic.util.Pagination;

/**
 * Verifie par assertions ce que Test.java se contentait d'afficher pour relecture manuelle.
 * Les methodes sont ordonnees (@Order) car le CRUD est intrinsequement sequentiel (il faut
 * l'id genere par save() pour tester findById(), etc.).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericDAOCrudTest {

    private static final String TABLE = "generic_dao_junit_test";
    private static Connection con;
    private static Object idPremier;

    @BeforeAll
    static void setUp() throws Exception {
        con = Connexion.getConnection(false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE
                        + " (id SERIAL PRIMARY KEY, nom VARCHAR(100), description VARCHAR(255))",
                true, false);
    }

    @AfterAll
    static void tearDown() throws Exception {
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE, true, false);
        con.close();
    }

    private static MyEntity entite() {
        MyEntity e = new MyEntity();
        e.setTableName(TABLE);
        return e;
    }

    // Le resultat de findById()/select() est une instance FRAICHE qui n'herite pas du
    // setTableName() de l'entite appelante : il faut le reappliquer avant tout save/update/
    // delete sur l'objet trouve (piege deja rencontre avec save() en lot).
    private static MyEntity trouverParId(Object id) throws Exception {
        MyEntity e = entite().findById(con, id, false);
        e.setTableName(TABLE);
        return e;
    }

    @Test
    @Order(1)
    void saveGenereUneClePrimaire() throws Exception {
        MyEntity e = entite();
        e.setNom("Premier");
        e.setDescription("Une ligne");
        idPremier = e.save(con, false, true);
        assertNotNull(idPremier, "save() doit renvoyer la cle primaire generee");
    }

    @Test
    @Order(2)
    void saveEnLotInsereLesDeuxLignes() throws Exception {
        MyEntity e2 = entite();
        e2.setNom("Deuxieme");
        MyEntity e3 = entite();
        e3.setNom("Troisieme");
        // Regression : save(Connection, T[], ...) executait autrefois executeUpdate() sur
        // un SQL se terminant par RETURNING, ce que Postgres refuse (PSQLException).
        entite().save(con, new MyEntity[] { e2, e3 }, false, true);

        assertEquals(3, entite().getTableSize(con, MyEntity.class));
    }

    @Test
    @Order(3)
    void findByIdRetrouveLaBonneLigne() throws Exception {
        MyEntity trouve = trouverParId(idPremier);
        assertEquals("Premier", trouve.getNom());
    }

    @Test
    @Order(4)
    void updateModifieLaLigne() throws Exception {
        MyEntity trouve = trouverParId(idPremier);
        trouve.setDescription("Modifiee");
        trouve.update(con, idPremier, false, true);

        assertEquals("Modifiee", trouverParId(idPremier).getDescription());
    }

    @Test
    @Order(5)
    void rechercheNeTrouveQueLaLigneCorrespondante() throws Exception {
        MyEntity recherche = entite();
        recherche.setRecherche("Deuxieme");
        MyEntity[] resultats = recherche.select(con, false);
        assertEquals(1, resultats.length);
        assertEquals("Deuxieme", resultats[0].getNom());
    }

    @Test
    @Order(6)
    void selectOneNePolluePasLesAppelsSuivants() throws Exception {
        // Regression : selectOne() faisait autrefois setDebut(null)/setFin(null), rejete par
        // les validations de ces setters, empechant tout select() suivant sur l'instance.
        assertNotNull(entite().selectOne(con, false));
        assertEquals(3, entite().select(con, false).length,
                "un select() classique apres selectOne() doit revoir toutes les lignes");
    }

    @Test
    @Order(7)
    void paginationPremierePageCommenceBienAuDebut() throws Exception {
        // Regression : Pagination.fromPageNumber(1, taille) renvoyait autrefois la 2e page.
        MyEntity page = entite();
        page.setPaginable(true);
        page.setPagination(Pagination.fromPageNumber(1, 2));
        MyEntity[] resultats = page.select(con, false);
        assertEquals(2, resultats.length);
        assertEquals(3, page.getPagination().getTotalSize());
    }

    @Test
    @Order(8)
    void deleteSupprimeLaLigne() throws Exception {
        entite().delete(con, idPremier, false, true);
        assertEquals(2, entite().getTableSize(con, MyEntity.class));
    }
}
