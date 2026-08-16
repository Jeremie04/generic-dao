package test;

import Generic.annotation.AField;
import Generic.dao.GenericDAO;

/**
 * Classe de base destinee a etre etendue : ses champs (dont la cle primaire)
 * sont fusionnes avec ceux des sous-classes par GenericDAO.getFieldsNotIgnored,
 * qui remonte la hierarchie jusqu'a GenericDAO. Toutes les sous-classes
 * partagent donc la meme table (heritage a table unique).
 */
public abstract class Personne extends GenericDAO {

    @AField(isId = true, column = "id")
    protected int id;

    @AField(column = "nom")
    protected String nom;

    @AField(column = "email")
    protected String email;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
