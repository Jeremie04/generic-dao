package test;

import java.sql.Connection;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Verifie par assertions deux comportements de reflexion decouverts en testant
 * PerfEntityLarge (TestPerformanceAvance.java) : les champs static et le traitement des
 * booleens a leur valeur par defaut. Chaque test repart d'une table vide (@BeforeEach).
 */
public class GenericDAOFieldHandlingTest {

    private static final String TABLE = "generic_dao_perf_large_test";
    private static Connection con;

    @BeforeAll
    static void setUp() throws Exception {
        con = Connexion.getConnection(false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE + " (" + PerfEntityLarge.colonnesSQL() + ")",
                true, false);
    }

    @BeforeEach
    void nettoyer() throws Exception {
        GenericDAO.executeSql(con, "TRUNCATE TABLE " + TABLE, true, false);
    }

    @AfterAll
    static void tearDown() throws Exception {
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE, true, false);
        con.close();
    }

    @Test
    void champStatiqueIgnorePendantLeSave() throws Exception {
        // Regression : NB_ATTRIBUTS (public static final) etait autrefois insere comme si
        // c'etait un attribut de chaque ligne, produisant une colonne SQL inexistante.
        PerfEntityLarge e = new PerfEntityLarge();
        e.setChamp01("valeur");
        Object id = e.save(con, false, true);
        assertNotNull(id);
    }

    @Test
    void selectAllNeFiltrePasSurUnBooleenAFaux() throws Exception {
        // Regression : un champ boolean a false (valeur par defaut Java) etait traite comme
        // "renseigne", ce qui filtrait involontairement un select() cense tout renvoyer
        // (WHERE actif = false genere sans le demander).
        PerfEntityLarge actif = new PerfEntityLarge();
        actif.setChamp01("actif");
        actif.setActif(true);
        actif.save(con, false, true);

        PerfEntityLarge inactif = new PerfEntityLarge();
        inactif.setChamp01("inactif");
        inactif.setActif(false);
        inactif.save(con, false, true);

        PerfEntityLarge[] tous = new PerfEntityLarge().select(con, false);
        assertEquals(2, tous.length,
                "select() sans filtre doit renvoyer toutes les lignes, actif=true et actif=false confondus");
    }
}
