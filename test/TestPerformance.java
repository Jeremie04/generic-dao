package test;

import java.sql.Connection;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;
import Generic.util.Pagination;

/**
 * Mesure le temps d'execution des operations CRUD de GenericDAO sur un volume de donnees
 * significatif (NB_LIGNES), pour reperer d'eventuels goulots d'etranglement — notamment
 * save() unitaire (un commit par ligne) vs en lot (un seul commit), et select() sur une
 * table de plusieurs milliers de lignes.
 */
public class TestPerformance {

    // A augmenter pour un test de charge plus realiste (10 000, 50 000, ...).
    private static final int NB_LIGNES = 2000;
    private static final String TABLE = "generic_dao_perf_test";

    public static void main(String[] args) throws Exception {
        Connection con = Connexion.getConnection(false);

        try {
            System.out.println("=== Creation de la table de test (" + NB_LIGNES + " lignes) ===");
            GenericDAO.executeSql(con,
                    "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                            "id SERIAL PRIMARY KEY, nom VARCHAR(100), description VARCHAR(255))",
                    true, false);

            mesurerSaveUnitaire(con);
            mesurerSaveEnLot(con);
            mesurerSelectTout(con);
            mesurerSelectPagine(con);
            mesurerFindById(con);

            System.out.println("\n=== Nettoyage ===");
            GenericDAO.executeSql(con, "DROP TABLE IF EXISTS " + TABLE, true, false);

            System.out.println("\nMesures terminees.");
        } finally {
            con.close(); // rend la connexion au pool
            Connexion.shutdownPool(); // arret propre : ce programme est le seul a utiliser le pool dans ce process
        }
    }

    private static MyEntity entite(String prefixe, int i) {
        MyEntity e = new MyEntity();
        e.setTableName(TABLE);
        e.setNom(prefixe + i);
        e.setDescription("Ligne de test numero " + i);
        return e;
    }

    private static void mesurerSaveUnitaire(Connection con) throws Exception {
        System.out.println("\n=== save() unitaire x " + NB_LIGNES + " (un commit par ligne) ===");
        long debut = System.nanoTime();
        for (int i = 0; i < NB_LIGNES; i++) {
            entite("unitaire_", i).save(con, false, true);
        }
        afficherDuree(debut, NB_LIGNES);
    }

    private static void mesurerSaveEnLot(Connection con) throws Exception {
        System.out.println("\n=== save() en lot x " + NB_LIGNES + " (un seul commit) ===");
        MyEntity[] lot = new MyEntity[NB_LIGNES];
        for (int i = 0; i < NB_LIGNES; i++) {
            lot[i] = entite("lot_", i);
        }
        // save(Connection, T[], ...) est une methode d'instance : le nom de table vient de
        // l'objet sur lequel on appelle save(...), pas des objets du tableau. Il faut donc
        // configurer explicitement cette instance-la, pas seulement celles du lot.
        MyEntity dispatcher = new MyEntity();
        dispatcher.setTableName(TABLE);
        long debut = System.nanoTime();
        dispatcher.save(con, lot, false, true);
        afficherDuree(debut, NB_LIGNES);
    }

    private static void mesurerSelectTout(Connection con) throws Exception {
        System.out.println("\n=== select() sur la table complete ===");
        MyEntity filtre = new MyEntity();
        filtre.setTableName(TABLE);
        long debut = System.nanoTime();
        MyEntity[] resultats = filtre.select(con, false);
        afficherDuree(debut, resultats.length);
    }

    private static void mesurerSelectPagine(Connection con) throws Exception {
        System.out.println("\n=== select() paginable (50 lignes/page) ===");
        MyEntity filtre = new MyEntity();
        filtre.setTableName(TABLE);
        filtre.setPaginable(true);
        filtre.setPagination(Pagination.fromPageNumber(1, 50));
        long debut = System.nanoTime();
        MyEntity[] resultats = filtre.select(con, false);
        afficherDuree(debut, resultats.length);
        System.out.println("total en base (compte par la pagination) : " + filtre.getPagination().getTotalSize());
    }

    private static void mesurerFindById(Connection con) throws Exception {
        System.out.println("\n=== findById() (une seule ligne) ===");
        MyEntity modele = new MyEntity();
        modele.setTableName(TABLE);
        long debut = System.nanoTime();
        modele.findById(con, 1, false);
        afficherDuree(debut, 1);
    }

    private static void afficherDuree(long debutNano, int nbLignes) {
        long dureeMs = (System.nanoTime() - debutNano) / 1_000_000;
        String moyenne = nbLignes > 0 ? String.format(" (%.3f ms/ligne)", dureeMs / (double) nbLignes) : "";
        System.out.println(nbLignes + " ligne(s) en " + dureeMs + " ms" + moyenne);
    }
}
