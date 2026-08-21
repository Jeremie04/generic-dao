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
 * Regressions pour setFetchRelations sur trois cas limites, avec une entite auto-referente
 * (Noeud.parent est lui-meme un Noeud) :
 * 1) relation auto-referente -> JOIN sur la meme table sans alias, rejete par Postgres ;
 * 2) colonne "nom" presente des deux cotes -> "WHERE nom = ?" ambigu sans qualification ;
 * 3) champ-relation renomme via @AField(column = "parent_ref") -> l'alias de lecture doit
 *    suivre ce renommage, pas rester base sur le nom de champ Java "parent".
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericDAORelationEdgeCasesTest {

    private static final String TABLE = "noeud_edge_test";
    private static Connection con;
    private static Object idRacine;
    private static Object idEnfant;

    @BeforeAll
    static void setUp() throws Exception {
        con = Connexion.getConnection(false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE + " (id SERIAL PRIMARY KEY, nom VARCHAR(100), "
                        + "id_parent_ref INTEGER REFERENCES " + TABLE + "(id))",
                true, false);

        Noeud racine = new Noeud();
        racine.setNom("Racine");
        idRacine = racine.save(con, false, true);

        Noeud refVersRacine = new Noeud();
        refVersRacine.setId(((Number) idRacine).intValue());
        Noeud enfant = new Noeud();
        enfant.setNom("Enfant");
        enfant.setParent(refVersRacine);
        idEnfant = enfant.save(con, false, true);
    }

    @AfterAll
    static void tearDown() throws Exception {
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE, true, false);
        con.close();
    }

    @Test
    @Order(1)
    void joinAutoReferentFonctionneSansConflitDeTable() throws Exception {
        // Regression : sans alias de table, "JOIN noeud_edge_test ON ..." (meme table des deux
        // cotes) etait rejete par Postgres ("table name specified more than once").
        // Racine n'a pas de parent : un JOIN (INNER) l'exclut du resultat, seul Enfant reste.
        Noeud filtre = new Noeud();
        filtre.setFetchRelations("parent");
        Noeud[] resultats = filtre.select(con, false);
        assertEquals(1, resultats.length);
        assertEquals("Enfant", resultats[0].getNom());
    }

    @Test
    @Order(2)
    void colonneAmbigueEstQualifieeParLaTableEtAliasSuitLeRenommage() throws Exception {
        // Regression : sans qualification par table, "WHERE nom = ?" devenait ambigu des que
        // le JOIN automatique exposait aussi une colonne "nom" sur la table liee (ici la meme
        // table, puisque la relation est auto-referente).
        Noeud filtre = new Noeud();
        filtre.setNom("Enfant");
        filtre.setFetchRelations("parent");
        Noeud[] resultats = filtre.select(con, false);
        assertEquals(1, resultats.length);
        assertNotNull(resultats[0].getParent());
        assertEquals("Racine", resultats[0].getParent().getNom(),
                "l'alias de lecture doit suivre le renommage @AField(column = \"parent_ref\"), "
                        + "pas rester base sur le nom de champ Java \"parent\"");
    }
}
