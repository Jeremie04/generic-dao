package Generic.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface AField {
    boolean isId() default false;

    boolean ignored() default false;

    boolean notIncremented() default false;

    String column() default ""; // Column's Name

    String sequence() default ""; // Sequence's Name

    String sequenceBefore() default ""; // Sequence's before
}