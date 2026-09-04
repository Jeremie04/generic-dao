package test;

import java.util.List;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

/**
 * Cote "un" d'une relation "un-a-plusieurs" : un auteur a plusieurs livres. La cle etrangere
 * est portee par Livre (champ "auteur"), d'ou @AField(mappedBy = "auteur") sur "livres" ici.
 */
@AClass(tableName = "auteur_collection_test")
public class Auteur extends GenericDAO {

    @AField(isId = true)
    private int id;

    @AField
    private String nom;

    @AField(mappedBy = "auteur")
    private List<Livre> livres;

    public Auteur() {
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

    public List<Livre> getLivres() {
        return livres;
    }

    public void setLivres(List<Livre> livres) {
        this.livres = livres;
    }
}
