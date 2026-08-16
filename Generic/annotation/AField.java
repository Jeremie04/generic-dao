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

    // Declares mais non lus par GenericDAO : reserves pour une future gestion des sequences
    // SQL, sans effet actuellement.
    String sequence() default ""; // Sequence's Name

    String sequenceBefore() default ""; // Sequence's before
}