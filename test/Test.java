package test;

import java.sql.Connection;

import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;
import Generic.util.Pagination;

public class Test {

    public static void main(String[] args) throws Exception {
        Connection con = Connexion.getConnection(false);

        try {
            System.out.println("=== Creation de la table de test ===");
            GenericDAO.executeSql(con,
                    "CREATE TABLE IF NOT EXISTS generic_dao_test (" +
                            "id SERIAL PRIMARY KEY, nom VARCHAR(100), description VARCHAR(255))",
                    true, false);

            System.out.println("\n=== TEST save() (une entite) ===");
            MyEntity e1 = new MyEntity();
            e1.setNom("Premier");
            e1.setDescription("Ligne inseree par le test");
            Object id1 = e1.save(con, false, true);
            System.out.println("Nouvel id genere : " + id1);

            System.out.println("\n=== TEST save() (tableau, insertion en lot) ===");
            MyEntity e2 = new MyEntity();
            e2.setNom("Deuxieme");
            e2.setDescription("Insere en lot");
            MyEntity e3 = new MyEntity();
            e3.setNom("Troisieme");
            e3.setDescription("Insere en lot aussi");
            new MyEntity().save(con, new MyEntity[] { e2, e3 }, false, true);

            System.out.println("\n=== TEST select() (tous) ===");
            MyEntity[] all = new MyEntity().select(con, false);
            for (MyEntity e : all) {
                System.out.println(e);
            }

            System.out.println("\n=== TEST selectOne() ===");
            MyEntity one = new MyEntity().selectOne(con, false);
            System.out.println("Premier resultat : " + one);

            System.out.println("\n=== TEST findById() ===");
            MyEntity found = new MyEntity().findById(con, id1, false);
            System.out.println("Trouve par id " + id1 + " : " + found);

            System.out.println("\n=== TEST update() ===");
            found.setDescription("Description modifiee");
            found.update(con, id1, false, true);
            MyEntity updated = new MyEntity().findById(con, id1, false);
            System.out.println("Apres update : " + updated);

            System.out.println("\n=== TEST recherche texte (setRecherche) ===");
            MyEntity search = new MyEntity();
            search.setRecherche("Deuxieme");
            MyEntity[] searchResults = search.select(con, false);
            for (MyEntity e : searchResults) {
                System.out.println("Resultat recherche : " + e);
            }

            System.out.println("\n=== TEST pagination ===");
            MyEntity pager = new MyEntity();
            pager.setPaginable(true);
            pager.setPagination(Pagination.fromPageNumber(1, 2));
            MyEntity[] page = pager.select(con, false);
            System.out.println("Taille page : " + page.length
                    + " / total en base : " + pager.getPagination().getTotalSize());

            System.out.println("\n=== TEST getTableSize() ===");
            int tableSize = new MyEntity().getTableSize(con, MyEntity.class);
            System.out.println("Nombre de lignes dans la table : " + tableSize);

            System.out.println("\n=== TEST delete() ===");
            new MyEntity().delete(con, id1, false, true);
            MyEntity[] remaining = new MyEntity().select(con, false);
            System.out.println("Restant apres delete : " + remaining.length);

            System.out.println("\n=== Nettoyage (suppression de la table de test) ===");
            GenericDAO.executeSql(con, "DROP TABLE IF EXISTS generic_dao_test", true, false);

            System.out.println("\nTous les tests se sont executes sans exception.");
        } finally {
            con.close(); // rend la connexion au pool
            Connexion.shutdownPool(); // arret propre : ce programme est le seul a utiliser le pool dans ce process
        }
    }
}
