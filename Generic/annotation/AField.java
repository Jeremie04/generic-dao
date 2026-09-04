package Generic.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A poser sur chaque champ d'une classe qui etend {@code GenericDAO} pour preciser son mapping
 * SQL. Une entite doit avoir exactement un champ avec {@code isId = true} : c'est la cle
 * primaire utilisee par {@code save}/{@code findById}/{@code update}/{@code delete}.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AField {
    /** Marque ce champ comme la cle primaire de l'entite (un seul champ par entite). */
    boolean isId() default false;

    /** Exclut ce champ de toutes les operations CRUD (save/select/update/delete). */
    boolean ignored() default false;

    // Declare mais non lu par GenericDAO : reserve pour une future gestion des cles non
    // auto-incrementees, sans effet actuellement.
    boolean notIncremented() default false;

    /** Nom de la colonne SQL. Vide par defaut (le nom du champ Java est alors utilise). */
    String column() default ""; // Column's Name

    /**
     * A poser sur un champ collection ({@code List<Item>} ou {@code Item[]}) representant une
     * relation "un-a-plusieurs" chargeable via {@code setFetchRelations(...)}. Nomme le champ
     * de la classe {@code Item} qui reference cette entite en retour (la cle etrangere est
     * portee par la table liee, jamais par celle de ce champ collection). Obligatoire pour tout
     * champ collection utilise avec {@code setFetchRelations}, sinon une exception est levee a
     * l'execution : impossible de deviner ce champ par reflexion seule (ambiguite en cas de
     * plusieurs champs du meme type, ou de relation auto-referente).
     */
    String mappedBy() default "";

    // Declares mais non lus par GenericDAO : reserves pour une future gestion des sequences
    // SQL, sans effet actuellement.
    String sequence() default ""; // Sequence's Name

    String sequenceBefore() default ""; // Sequence's before
}