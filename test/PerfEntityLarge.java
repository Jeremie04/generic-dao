package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

/**
 * Entite avec beaucoup d'attributs (contrairement a MyEntity qui n'en a que 2), utilisee
 * uniquement pour mesurer l'impact du nombre de champs sur le cout de la reflexion dans
 * GenericDAO — voir TestPerformanceAvance.
 */
@AClass(tableName = "generic_dao_perf_large_test")
public class PerfEntityLarge extends GenericDAO {

    /** Nombre d'attributs (hors id), pour affichage dans les mesures. */
    public static final int NB_ATTRIBUTS = 17;

    @AField(isId = true, column = "id")
    private int id;

    @AField(column = "champ01")
    private String champ01;
    @AField(column = "champ02")
    private String champ02;
    @AField(column = "champ03")
    private String champ03;
    @AField(column = "champ04")
    private String champ04;
    @AField(column = "champ05")
    private String champ05;
    @AField(column = "champ06")
    private String champ06;
    @AField(column = "champ07")
    private String champ07;
    @AField(column = "champ08")
    private String champ08;
    @AField(column = "champ09")
    private String champ09;
    @AField(column = "champ10")
    private String champ10;
    @AField(column = "champ11")
    private String champ11;
    @AField(column = "champ12")
    private String champ12;

    @AField(column = "entier1")
    private int entier1;
    @AField(column = "entier2")
    private int entier2;
    @AField(column = "reel1")
    private double reel1;
    @AField(column = "reel2")
    private double reel2;
    @AField(column = "actif")
    private boolean actif;

    public PerfEntityLarge() {
    }

    /** Colonnes SQL correspondant a tous les attributs, pour construire le CREATE TABLE du test. */
    public static String colonnesSQL() {
        StringBuilder sql = new StringBuilder("id SERIAL PRIMARY KEY");
        for (int i = 1; i <= 12; i++) {
            sql.append(", champ").append(String.format("%02d", i)).append(" VARCHAR(100)");
        }
        sql.append(", entier1 INTEGER, entier2 INTEGER, reel1 DOUBLE PRECISION, reel2 DOUBLE PRECISION, actif BOOLEAN");
        return sql.toString();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getChamp01() {
        return champ01;
    }

    public void setChamp01(String champ01) {
        this.champ01 = champ01;
    }

    public String getChamp02() {
        return champ02;
    }

    public void setChamp02(String champ02) {
        this.champ02 = champ02;
    }

    public String getChamp03() {
        return champ03;
    }

    public void setChamp03(String champ03) {
        this.champ03 = champ03;
    }

    public String getChamp04() {
        return champ04;
    }

    public void setChamp04(String champ04) {
        this.champ04 = champ04;
    }

    public String getChamp05() {
        return champ05;
    }

    public void setChamp05(String champ05) {
        this.champ05 = champ05;
    }

    public String getChamp06() {
        return champ06;
    }

    public void setChamp06(String champ06) {
        this.champ06 = champ06;
    }

    public String getChamp07() {
        return champ07;
    }

    public void setChamp07(String champ07) {
        this.champ07 = champ07;
    }

    public String getChamp08() {
        return champ08;
    }

    public void setChamp08(String champ08) {
        this.champ08 = champ08;
    }

    public String getChamp09() {
        return champ09;
    }

    public void setChamp09(String champ09) {
        this.champ09 = champ09;
    }

    public String getChamp10() {
        return champ10;
    }

    public void setChamp10(String champ10) {
        this.champ10 = champ10;
    }

    public String getChamp11() {
        return champ11;
    }

    public void setChamp11(String champ11) {
        this.champ11 = champ11;
    }

    public String getChamp12() {
        return champ12;
    }

    public void setChamp12(String champ12) {
        this.champ12 = champ12;
    }

    public int getEntier1() {
        return entier1;
    }

    public void setEntier1(int entier1) {
        this.entier1 = entier1;
    }

    public int getEntier2() {
        return entier2;
    }

    public void setEntier2(int entier2) {
        this.entier2 = entier2;
    }

    public double getReel1() {
        return reel1;
    }

    public void setReel1(double reel1) {
        this.reel1 = reel1;
    }

    public double getReel2() {
        return reel2;
    }

    public void setReel2(double reel2) {
        this.reel2 = reel2;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }

    @Override
    public String toString() {
        return "PerfEntityLarge{id=" + id + ", champ01='" + champ01 + "', entier1=" + entier1
                + ", reel1=" + reel1 + ", actif=" + actif + ", ...}";
    }
}
