package Generic.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A poser sur une classe qui etend {@code GenericDAO} pour preciser le nom de la table SQL
 * associee. Optionnelle : sans elle (ou avec {@code tableName} laisse vide), le nom de la
 * classe Java est utilise tel quel comme nom de table.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AClass {
    /** Nom de la table SQL. Vide par defaut (le nom de la classe est alors utilise). */
    String tableName() default "";
}
