package test;

import java.sql.Connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Regression pour deux points independants :
 * 1) setRecherche(...) liait autrefois ses valeurs par concatenation dans le SQL
 *    ({@code ILIKE '...'}) : injection SQL possible, et une simple apostrophe dans le terme
 *    cherche cassait deja la requete sans intention malveillante. Verifie que les valeurs sont
 *    desormais liees via {@code ?}.
 * 2) {@code update(Connection, T[], ...)} et {@code delete(Connection, T[], ...)} : mise a jour
 *    et suppression en lot, chaque element identifie par sa propre cle primaire.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericDAOBatchAndSearchTest {

    private static final String TABLE = "generic_dao_batch_search_test";
    private static Connection con;

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

    @Test
    @Order(1)
    void rechercheAvecApostropheNeCassePasEtTrouveLaBonneLigne() throws Exception {
        MyEntity e = entite();
        e.setNom("O'Brien");
        e.save(con, false, true);

        MyEntity recherche = entite();
        recherche.setRecherche("O'Brien");
        MyEntity[] resultats = recherche.select(con, false);

        assertEquals(1, resultats.length,
                "une apostrophe dans le terme cherche ne doit plus casser la requete (regression injection SQL)");
        assertEquals("O'Brien", resultats[0].getNom());
    }

    @Test
    @Order(2)
    void rechercheAvecPayloadDInjectionNeRamenePasToutesLesLignes() throws Exception {
        // Avec l'ancienne concatenation, "x' OR '1'='1" aurait ferme le litteral SQL et transforme
        // la condition en tautologie (toutes les lignes remontent). Valeur liee via "?" : traitee
        // comme un simple texte a chercher, ne doit correspondre a rien ici.
        MyEntity recherche = entite();
        recherche.setRecherche("x' OR '1'='1");
        MyEntity[] resultats = recherche.select(con, false);

        assertEquals(0, resultats.length);
    }

    @Test
    @Order(3)
    void updateEnLotModifieChaqueLigneAvecSaPropreClePrimaire() throws Exception {
        MyEntity a = entite();
        a.setNom("Un");
        Object idA = a.save(con, false, true);
        MyEntity b = entite();
        b.setNom("Deux");
        Object idB = b.save(con, false, true);

        MyEntity aAModifier = entite();
        aAModifier.setId(((Number) idA).intValue());
        aAModifier.setDescription("Modifie A");
        MyEntity bAModifier = entite();
        bAModifier.setId(((Number) idB).intValue());
        bAModifier.setDescription("Modifie B");

        entite().update(con, new MyEntity[] { aAModifier, bAModifier }, false, true);

        MyEntity retrouveA = entite().findById(con, idA, false);
        MyEntity retrouveB = entite().findById(con, idB, false);
        assertEquals("Modifie A", retrouveA.getDescription());
        assertEquals("Modifie B", retrouveB.getDescription());
    }

    @Test
    @Order(4)
    void deleteEnLotSupprimeChaqueLigneAvecSaPropreClePrimaire() throws Exception {
        int tailleAvant = entite().getTableSize(con, MyEntity.class);

        MyEntity c = entite();
        c.setNom("Trois");
        Object idC = c.save(con, false, true);
        MyEntity d = entite();
        d.setNom("Quatre");
        Object idD = d.save(con, false, true);
        assertEquals(tailleAvant + 2, entite().getTableSize(con, MyEntity.class));

        MyEntity aSupprimerC = entite();
        aSupprimerC.setId(((Number) idC).intValue());
        MyEntity aSupprimerD = entite();
        aSupprimerD.setId(((Number) idD).intValue());
        entite().delete(con, new MyEntity[] { aSupprimerC, aSupprimerD }, false, true);

        assertEquals(tailleAvant, entite().getTableSize(con, MyEntity.class));
    }
}
