package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

@AClass(tableName = "categorie_test")
public class Categorie extends GenericDAO {

    @AField(isId = true, column = "id")
    private int id;

    @AField(column = "nom")
    private String nom;

    public Categorie() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    @Override
    public String toString() {
        return "Categorie{id=" + id + ", nom='" + nom + "'}";
    }
}
