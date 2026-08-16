package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

/**
 * Exemple de relation "a un" : un Materiel a une Categorie.
 * Le champ objet "categorie" est mappe sur la colonne "id_categorie"
 * (cle primaire de Categorie prefixee par le nom du champ) — voir
 * GenericDAO.getFieldNameIfObject.
 */
@AClass(tableName = "materiel_test")
public class Materiel extends GenericDAO {

    @AField(isId = true, column = "id")
    private int id;

    @AField(column = "designation")
    private String designation;

    @AField(column = "categorie")
    private Categorie categorie;

    public Materiel() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Categorie getCategorie() {
        return categorie;
    }

    public void setCategorie(Categorie categorie) {
        this.categorie = categorie;
    }

    @Override
    public String toString() {
        return "Materiel{id=" + id + ", designation='" + designation + "', categorie=" + categorie + "}";
    }
}
