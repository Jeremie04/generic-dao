package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

/**
 * Cote "plusieurs" de la relation Auteur <-> Livre (voir Auteur.livres, @AField(mappedBy)).
 */
@AClass(tableName = "livre_collection_test")
public class Livre extends GenericDAO {

    @AField(isId = true)
    private int id;

    @AField
    private String titre;

    @AField
    private Auteur auteur;

    public Livre() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitre() {
        return titre;
    }

    public void setTitre(String titre) {
        this.titre = titre;
    }

    public Auteur getAuteur() {
        return auteur;
    }

    public void setAuteur(Auteur auteur) {
        this.auteur = auteur;
    }
}
