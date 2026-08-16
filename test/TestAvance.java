package test;

import java.sql.Connection;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;

/**
 * Teste deux cas plus avances que Test.java :
 * 1) une entite qui contient un objet (relation "a un")
 * 2) une entite qui herite ses champs (et sa cle primaire) d'une classe parente
 */
public class TestAvance {

    public static void main(String[] args) throws Exception {
        Connection con = Connexion.getConnection(false);

        try {
            testObjetImbrique(con);
            testHeritage(con);
            System.out.println("\nTous les tests avances se sont executes sans exception.");
        } finally {
            con.close();
        }
    }

    private static void testObjetImbrique(Connection con) throws Exception {
        System.out.println("========================================");
        System.out.println("=== SCENARIO 1 : objet imbrique (has-a) ===");
        System.out.println("========================================");

        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS categorie_test (" +
                        "id SERIAL PRIMARY KEY, nom VARCHAR(100))",
                true, false);
        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS materiel_test (" +
                        "id SERIAL PRIMARY KEY, designation VARCHAR(100), " +
                        "id_categorie INTEGER REFERENCES categorie_test(id))",
                true, false);

        System.out.println("\n--- save() de la Categorie ---");
        Categorie informatique = new Categorie();
        informatique.setNom("Informatique");
        Object categorieId = informatique.save(con, false, true);
        System.out.println("Categorie creee, id = " + categorieId);

        System.out.println("\n--- save() du Materiel avec une reference a la Categorie ---");
        Categorie refCategorie = new Categorie();
        refCategorie.setId(((Number) categorieId).intValue());
        // seule la cle primaire est necessaire ici : elle sert a remplir la colonne id_categorie

        Materiel pc = new Materiel();
        pc.setDesignation("Ordinateur portable");
        pc.setCategorie(refCategorie);
        Object materielId = pc.save(con, false, true);
        System.out.println("Materiel cree, id = " + materielId);

        System.out.println("\n--- select() simple (sans jointure) ---");
        Materiel[] simples = new Materiel().select(con, false);
        for (Materiel m : simples) {
            System.out.println(m);
        }
        System.out.println("Remarque : categorie.id est rempli (colonne id_categorie deja presente "
                + "dans materiel_test), mais categorie.nom reste null : un select() genere "
                + "automatiquement ne fait pas de JOIN.");

        System.out.println("\n--- select() avec une requete SQL personnalisee (JOIN) ---");
        String sqlJoin = "SELECT materiel_test.*, categorie_test.nom AS nom_categorie " +
                "FROM materiel_test JOIN categorie_test ON materiel_test.id_categorie = categorie_test.id";
        Materiel[] complets = new Materiel().select(con, false, sqlJoin);
        for (Materiel m : complets) {
            System.out.println(m);
        }

        System.out.println("\n--- Nettoyage ---");
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS materiel_test", true, false);
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS categorie_test", true, false);
    }

    private static void testHeritage(Connection con) throws Exception {
        System.out.println("\n========================================");
        System.out.println("=== SCENARIO 2 : heritage (extends) ===");
        System.out.println("========================================");

        GenericDAO.executeSql(con,
                "CREATE TABLE IF NOT EXISTS employe_test (" +
                        "id SERIAL PRIMARY KEY, nom VARCHAR(100), email VARCHAR(100), " +
                        "poste VARCHAR(100), salaire NUMERIC)",
                true, false);

        System.out.println("\n--- save() (champs herites de Personne + champs propres a Employe) ---");
        Employe emp = new Employe();
        emp.setNom("Rakoto");
        emp.setEmail("rakoto@example.com");
        emp.setPoste("Developpeur");
        emp.setSalaire(1500000);
        Object empId = emp.save(con, false, true);
        System.out.println("Employe cree, id = " + empId);

        System.out.println("\n--- findById() : la cle primaire est declaree dans Personne ---");
        Employe found = new Employe().findById(con, empId, false);
        System.out.println("Trouve : " + found);

        System.out.println("\n--- update() ---");
        found.setSalaire(1600000);
        found.update(con, empId, false, true);
        Employe updated = new Employe().findById(con, empId, false);
        System.out.println("Apres update : " + updated);

        System.out.println("\n--- select() (tous) ---");
        Employe[] tous = new Employe().select(con, false);
        for (Employe e : tous) {
            System.out.println(e);
        }

        System.out.println("\n--- delete() ---");
        new Employe().delete(con, empId, false, true);

        System.out.println("\n--- Nettoyage ---");
        GenericDAO.executeSql(con, "DROP TABLE IF EXISTS employe_test", true, false);
    }
}
