package Generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import Generic.annotation.AClass;
import Generic.annotation.AField;

/**
 * Reflection pure : resolution table/colonne/cle primaire, decouverte des champs
 * a considerer et conversion de valeurs JDBC vers les types Java. Les methodes qui
 * dependent de l'etat de requete d'une entite (tableName override, ignoredFields,
 * initialValue) recoivent cette entite explicitement via le parametre "self".
 */
final class FieldReflection {

    private FieldReflection() {
    }

    static boolean isObject(Class<?> type) {
        return !(type.isPrimitive() || type.equals(String.class) ||
                type.equals(Boolean.class) || type.equals(Character.class) ||
                Number.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)
                || Timestamp.class.isAssignableFrom(type));
    }

    static boolean isObject(Field field) {
        return isObject(field.getType());
    }

    static boolean isPrimaryKey(Field field) {
        AField fieldAnnotation = field.getAnnotation(AField.class);
        return fieldAnnotation != null && fieldAnnotation.isId();
    }

    static String getFieldName(Field field) {
        AField fieldAnnotation = field.getAnnotation(AField.class);
        if (fieldAnnotation != null && !fieldAnnotation.column().isEmpty()) {
            return fieldAnnotation.column();
        } else {
            return field.getName();
        }
    }

    static String getTableName(GenericDAO self, Class<?> clazz) {
        if (!self.getTableName().equals("")) {
            return self.getTableName();
        }
        return getSimpleTableName(clazz);
    }

    // Resolution du nom de table a partir de la seule Class, sans consulter le tableName
    // override d'une entite "self" : utilise pour resoudre le nom de table d'un objet lie
    // (relation), qui ne doit jamais heriter du setTableName() de l'entite qui le contient.
    static String getSimpleTableName(Class<?> clazz) {
        AClass classAnnotation = clazz.getAnnotation(AClass.class);
        if (classAnnotation != null && !classAnnotation.tableName().isEmpty()) {
            return classAnnotation.tableName();
        }
        return clazz.getSimpleName();
    }

    static String getFieldNameIfObject(GenericDAO self, Field field, String fieldName, Object objet) throws Exception {
        if (isObject(field)) {
            Object fieldObjet = field.get(objet);
            Field fieldPrimaryKey = getPrimaryKey(self, fieldObjet.getClass());
            if (fieldPrimaryKey == null) {
                throw new Exception("Primary key not found");
            }
            return fieldPrimaryKey.getName() + "_" + fieldName;
        }
        throw new Exception(
                "On ne peut pas prendre le primary key du champ " + field.getName() + " n'est pas un objet ");
    }

    // La partie couteuse (parcours de la hierarchie de classes + reflexion + lecture des
    // annotations @AField) ne depend que de la Class, jamais de l'instance : on la met en
    // cache pour ne la refaire qu'une fois par classe d'entite, pas a chaque appel de
    // save/select/update/delete. Seul le filtrage par setIgnoredFields(...), propre a
    // chaque instance, reste recalcule a chaque appel (peu couteux : simple contains()).
    private static final Map<Class<?>, Field[]> FIELDS_NON_STATIQUES_NON_IGNOREES_CACHE = new ConcurrentHashMap<>();

    private static Field[] decouvrirChampsDeLaClasse(Class<?> clazz) {
        List<Field> fields = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
        Class<?> superClass = clazz.getSuperclass();

        while (superClass != null && superClass != GenericDAO.class &&
                superClass != java.util.Date.class && superClass != java.sql.Timestamp.class &&
                superClass != java.sql.Time.class) {
            fields.addAll(Arrays.asList(superClass.getDeclaredFields()));
            superClass = superClass.getSuperclass();
        }

        return fields.stream()
                .filter(field -> {
                    // Un champ static (constante, compteur partage, ...) n'est pas une colonne :
                    // sans ce filtre, "public static final int NB = 3;" serait insere comme si
                    // c'etait un attribut de chaque ligne.
                    if (Modifier.isStatic(field.getModifiers())) {
                        return false;
                    }
                    AField fieldAnnotation = field.getAnnotation(AField.class);
                    return fieldAnnotation == null || !fieldAnnotation.ignored();
                })
                .toArray(Field[]::new);
    }

    static Field[] getFieldsNotIgnored(GenericDAO self, Class<?> clazz) {
        Field[] champsDeLaClasse = FIELDS_NON_STATIQUES_NON_IGNOREES_CACHE
                .computeIfAbsent(clazz, FieldReflection::decouvrirChampsDeLaClasse);

        if (self.getIgnoredFields().isEmpty()) {
            return champsDeLaClasse.clone(); // copie defensive : ne jamais exposer le tableau partage du cache
        }

        String classNameMinuscule = clazz.getSimpleName();
        classNameMinuscule = Character.toLowerCase(classNameMinuscule.charAt(0)) + classNameMinuscule.substring(1);

        List<Field> resultat = new ArrayList<>(champsDeLaClasse.length);
        for (Field field : champsDeLaClasse) {
            if (!self.getIgnoredFields().contains(getFieldName(field) + "_" + classNameMinuscule)) {
                resultat.add(field);
            }
        }
        return resultat.toArray(new Field[0]);
    }

    static Field[] getFieldsNotNull(GenericDAO self, Object object, Field[] fields) {
        List<Field> notNullFields = new ArrayList<>();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(object);
                if (value != null && isFieldValueSet(self, value)) {
                    notNullFields.add(field);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return notNullFields.toArray(new Field[notNullFields.size()]);
    }

    static boolean isFieldValueSet(GenericDAO self, Object value) {
        if (value instanceof Integer) {
            return (Integer) value != self.getInitialValue() && (Integer) value != 0;
        } else if (value instanceof Double) {
            return !((Double) value).equals((double) self.getInitialValue()) && (double) value != 0;
        } else if (value instanceof String) {
            return !value.toString().isEmpty();
        } else if (value instanceof Boolean) {
            // Meme convention que pour les nombres : la valeur par defaut (false) est traitee
            // comme "non renseignee", sinon un simple "new Entite()" pour un select-all
            // filtrerait involontairement sur ce champ (WHERE monBooleen = false).
            return (Boolean) value;
        }
        return true;
    }

    static Field getPrimaryKey(GenericDAO self, Class<?> clazz) throws Exception {
        Field[] fields = getFieldsNotIgnored(self, clazz);
        for (Field field : fields) {
            if (isPrimaryKey(field)) {
                self.setPrimaryKey(field);
                return field;
            }
        }
        return null;
    }

    static Object convertValue(Object value, Class<?> fieldType) throws Exception {
        if (value == null) {
            return null;
        }

        if (fieldType == String.class) {
            return value.toString();
        } else if (fieldType == int.class || fieldType == Integer.class) {
            return Integer.parseInt(value.toString());
        } else if (fieldType == double.class || fieldType == Double.class) {
            return Double.parseDouble(value.toString());
        } else if (fieldType == float.class || fieldType == Float.class) {
            return Float.parseFloat(value.toString());
        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        } else if (fieldType == Date.class) {
            if (value instanceof Date) {
                return (Date) value;
            } else if (value instanceof Timestamp) {
                return new Date(((Timestamp) value).getTime());
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                return dateFormat.parse(value.toString());
            }
        } else {
            return value;
        }
    }
}
