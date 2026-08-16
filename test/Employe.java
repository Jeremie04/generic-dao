package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;

@AClass(tableName = "employe_test")
public class Employe extends Personne {

    @AField(column = "poste")
    private String poste;

    @AField(column = "salaire")
    private double salaire;

    public Employe() {
    }

    public String getPoste() {
        return poste;
    }

    public void setPoste(String poste) {
        this.poste = poste;
    }

    public double getSalaire() {
        return salaire;
    }

    public void setSalaire(double salaire) {
        this.salaire = salaire;
    }

    @Override
    public String toString() {
        return "Employe{id=" + getId() + ", nom='" + getNom() + "', email='" + getEmail()
                + "', poste='" + poste + "', salaire=" + salaire + "}";
    }
}
