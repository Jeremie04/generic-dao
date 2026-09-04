package test;

import java.sql.Connection;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Regression pour setFetchRelations sur un champ collection (List<Item>, relation
 * "un-a-plusieurs") : Auteur.livres, mappedBy = "auteur" (voir Auteur.java / Livre.java).
 * Verifie le chargement batch (pas de JOIN, pas de doublon de parent) et le cas sans enfant.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GenericDAOCollectionRelationTest {

    private static final String TABLE_AUTEUR = "auteur_collection_test";
    private static final String TABLE_LIVRE = "livre_collection_test";
    private static Connection con;
    private static int idAuteurAvecLivres;
    private static int idAuteurSansLivre;

    @BeforeAll
    static void setUp() throws Exception {
        con = Connexion.getConnection(false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_AUTEUR + " (id SERIAL PRIMARY KEY, nom VARCHAR(100))",
                true, false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_LIVRE + " (id SERIAL PRIMARY KEY, titre VARCHAR(100), "
                        + "id_auteur INTEGER REFERENCES " + TABLE_AUTEUR + "(id))",
                true, false);

        Auteur auteurAvecLivres = new Auteur();
        auteurAvecLivres.setNom("Victor Hugo");
        idAuteurAvecLivres = ((Number) auteurAvecLivres.save(con, false, true)).intValue();

        Auteur auteurSansLivre = new Auteur();
        auteurSansLivre.setNom("Sans Livre");
        idAuteurSansLivre = ((Number) auteurSansLivre.save(con, false, true)).intValue();

        Auteur refVersAuteur = new Auteur();
        refVersAuteur.setId(idAuteurAvecLivres);

        Livre livre1 = new Livre();
        livre1.setTitre("Les Miserables");
        livre1.setAuteur(refVersAuteur);
        livre1.save(con, false, true);

        Livre livre2 = new Livre();
        livre2.setTitre("Notre-Dame de Paris");
        livre2.setAuteur(refVersAuteur);
        livre2.save(con, false, true);
    }

    @AfterAll
    static void tearDown() throws Exception {
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_LIVRE, true, false);
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_AUTEUR, true, false);
        con.close();
    }

    @Test
    @Order(1)
    void setFetchRelationsChargeLaListeDesEnfantsSansDupliquerLeParent() throws Exception {
        Auteur filtre = new Auteur();
        filtre.setId(idAuteurAvecLivres);
        filtre.setFetchRelations("livres");
        Auteur[] resultats = filtre.select(con, false);

        assertEquals(1, resultats.length, "un seul parent, pas de doublon du a un JOIN cote enfant");
        List<Livre> livres = resultats[0].getLivres();
        assertEquals(2, livres.size());
        assertTrue(livres.stream().anyMatch(l -> "Les Miserables".equals(l.getTitre())));
        assertTrue(livres.stream().anyMatch(l -> "Notre-Dame de Paris".equals(l.getTitre())));
    }

    @Test
    @Order(2)
    void setFetchRelationsDonneUneListeVideSiAucunEnfant() throws Exception {
        Auteur filtre = new Auteur();
        filtre.setId(idAuteurSansLivre);
        filtre.setFetchRelations("livres");
        Auteur[] resultats = filtre.select(con, false);

        assertEquals(1, resultats.length);
        assertEquals(0, resultats[0].getLivres().size());
    }
}
