package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

/**
 * Entite auto-referente (son propre type comme relation), utilisee uniquement pour tester
 * les cas limites de setFetchRelations : colonne "nom" presente des deux cotes du JOIN, et
 * champ-relation renomme via @AField (column = "parent_ref" au lieu du nom Java "parent").
 */
@AClass(tableName = "noeud_edge_test")
public class Noeud extends GenericDAO {

    @AField(isId = true, column = "id")
    private int id;

    @AField(column = "nom")
    private String nom;

    @AField(column = "parent_ref")
    private Noeud parent;

    public Noeud() {
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

    public Noeud getParent() {
        return parent;
    }

    public void setParent(Noeud parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return "Noeud{id=" + id + ", nom='" + nom + "', parent="
                + (parent != null ? parent.getId() : null) + "}";
    }
}
