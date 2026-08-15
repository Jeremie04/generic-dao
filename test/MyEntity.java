package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

@AClass(tableName = "generic_dao_test")
public class MyEntity extends GenericDAO {

    @AField(isId = true, column = "id")
    private int id;

    @AField(column = "nom")
    private String nom;

    @AField(column = "description")
    private String description;

    public MyEntity() {
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "MyEntity{id=" + id + ", nom='" + nom + "', description='" + description + "'}";
    }
}
