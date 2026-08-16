package test;

import java.sql.Connection;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Complete TestPerformance.java (entite simple a 2 attributs) en mesurant les memes
 * operations sur :
 * 1) une entite avec beaucoup d'attributs et aucune relation (PerfEntityLarge) ;
 * 2) une entite avec un objet imbrique, comme dans TestAvance (Materiel -> Categorie).
 * Objectif : verifier si/combien le nombre d'attributs et les relations alourdissent le
 * cout de la reflexion, par rapport au cout fixe (commit/latence reseau) deja mesure sur
 * l'entite simple.
 */
public class TestPerformanceAvance {

    private static final int NB_LIGNES = 2000;
    private static final int NB_CATEGORIES = 20;

    private static final String TABLE_LARGE = "generic_dao_perf_large_test";
    private static final String TABLE_CATEGORIE = "categorie_perf_test";
    private static final String TABLE_MATERIEL = "materiel_perf_test";

    public static void main(String[] args) throws Exception {
        Connection con = Connexion.getConnection(false);

        try {
            mesurerEntiteRiche(con);
            mesurerEntiteAvecRelation(con);
            System.out.println("\nMesures terminees.");
        } finally {
            con.close();
        }
    }

    // === 1) Entite avec beaucoup d'attributs, sans relation ===

    private static void mesurerEntiteRiche(Connection con) throws Exception {
        System.out.println("\n#########################################################");
        System.out.println("# ENTITE RICHE : " + PerfEntityLarge.NB_ATTRIBUTS + " attributs, sans relation");
        System.out.println("#########################################################");

        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_LARGE + " (" + PerfEntityLarge.colonnesSQL() + ")",
                true, false);

        System.out.println("\n=== save() unitaire x " + NB_LIGNES + " ===");
        long debut = System.nanoTime();
        for (int i = 0; i < NB_LIGNES; i++) {
            creerEntiteRiche(i).save(con, false, true);
        }
        afficherDuree(debut, NB_LIGNES);

        System.out.println("\n=== save() en lot x " + NB_LIGNES + " ===");
        PerfEntityLarge[] lot = new PerfEntityLarge[NB_LIGNES];
        for (int i = 0; i < NB_LIGNES; i++) {
            lot[i] = creerEntiteRiche(NB_LIGNES + i);
        }
        PerfEntityLarge dispatcher = new PerfEntityLarge();
        debut = System.nanoTime();
        dispatcher.save(con, lot, false, true);
        afficherDuree(debut, NB_LIGNES);

        System.out.println("\n=== select() sur la table complete ===");
        debut = System.nanoTime();
        PerfEntityLarge[] tous = new PerfEntityLarge().select(con, false);
        afficherDuree(debut, tous.length);

        System.out.println("\n=== Nettoyage ===");
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_LARGE, true, false);
    }

    private static PerfEntityLarge creerEntiteRiche(int i) {
        PerfEntityLarge e = new PerfEntityLarge();
        e.setChamp01("valeur01_" + i);
        e.setChamp02("valeur02_" + i);
        e.setChamp03("valeur03_" + i);
        e.setChamp04("valeur04_" + i);
        e.setChamp05("valeur05_" + i);
        e.setChamp06("valeur06_" + i);
        e.setChamp07("valeur07_" + i);
        e.setChamp08("valeur08_" + i);
        e.setChamp09("valeur09_" + i);
        e.setChamp10("valeur10_" + i);
        e.setChamp11("valeur11_" + i);
        e.setChamp12("valeur12_" + i);
        e.setEntier1(i);
        e.setEntier2(i * 2);
        e.setReel1(i * 1.5);
        e.setReel2(i * 2.5);
        e.setActif(i % 2 == 0);
        return e;
    }

    // === 2) Entite avec une relation (objet imbrique), comme Materiel -> Categorie ===

    private static void mesurerEntiteAvecRelation(Connection con) throws Exception {
        System.out.println("\n#########################################################");
        System.out.println("# ENTITE AVEC RELATION : Materiel -> Categorie (comme TestAvance)");
        System.out.println("#########################################################");

        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_CATEGORIE + " (id SERIAL PRIMARY KEY, nom VARCHAR(100))",
                true, false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS " + TABLE_MATERIEL + " (id SERIAL PRIMARY KEY, "
                        + "designation VARCHAR(100), id_categorie INTEGER REFERENCES " + TABLE_CATEGORIE + "(id))",
                true, false);

        System.out.println("\n--- creation de " + NB_CATEGORIES + " categories ---");
        int[] idsCategories = new int[NB_CATEGORIES];
        for (int i = 0; i < NB_CATEGORIES; i++) {
            Categorie c = new Categorie();
            c.setTableName(TABLE_CATEGORIE);
            c.setNom("Categorie " + i);
            idsCategories[i] = ((Number) c.save(con, false, true)).intValue();
        }

        System.out.println("\n=== save() unitaire x " + NB_LIGNES + " (avec relation vers Categorie) ===");
        long debut = System.nanoTime();
        for (int i = 0; i < NB_LIGNES; i++) {
            Categorie ref = new Categorie();
            ref.setId(idsCategories[i % NB_CATEGORIES]);

            Materiel m = new Materiel();
            m.setTableName(TABLE_MATERIEL);
            m.setDesignation("Materiel " + i);
            m.setCategorie(ref);
            m.save(con, false, true);
        }
        afficherDuree(debut, NB_LIGNES);

        System.out.println("\n=== select() sans jointure (categorie.nom reste null) ===");
        Materiel filtreSansJointure = new Materiel();
        filtreSansJointure.setTableName(TABLE_MATERIEL);
        debut = System.nanoTime();
        Materiel[] sansJointure = filtreSansJointure.select(con, false);
        afficherDuree(debut, sansJointure.length);

        System.out.println("\n=== select() avec jointure (categorie chargee en entier) ===");
        String sqlJoin = "SELECT " + TABLE_MATERIEL + ".*, " + TABLE_CATEGORIE + ".nom AS nom_categorie "
                + "FROM " + TABLE_MATERIEL + " JOIN " + TABLE_CATEGORIE
                + " ON " + TABLE_MATERIEL + ".id_categorie = " + TABLE_CATEGORIE + ".id";
        debut = System.nanoTime();
        Materiel[] avecJointure = new Materiel().select(con, false, sqlJoin);
        afficherDuree(debut, avecJointure.length);

        System.out.println("\n=== Nettoyage ===");
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_MATERIEL, true, false);
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE_CATEGORIE, true, false);
    }

    private static void afficherDuree(long debutNano, int nbLignes) {
        long dureeMs = (System.nanoTime() - debutNano) / 1_000_000;
        String moyenne = nbLignes > 0 ? String.format(" (%.3f ms/ligne)", dureeMs / (double) nbLignes) : "";
        System.out.println(nbLignes + " ligne(s) en " + dureeMs + " ms" + moyenne);
    }
}
