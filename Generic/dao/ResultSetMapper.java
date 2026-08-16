package Generic.dao;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Reconstruction d'objets Java a partir d'un ResultSet : mapping colonne -> champ,
 * y compris les objets imbriques (colonnes prefixees "<colonne>_<nom du champ objet>").
 */
final class ResultSetMapper {

    private ResultSetMapper() {
    }

    static Object getValueFromResultSet(ResultSet resultSet, String columnName, String motherFieldName)
            throws Exception {
        int columnIdx = -1;
        if (motherFieldName != null) {
            System.out.println("try " + columnName + "_" + motherFieldName);
            try {
                columnIdx = resultSet.findColumn(columnName + "_" + motherFieldName);
            } catch (Exception ex) {
                System.out.println(columnName + "_" + motherFieldName + " not found so go to next one");
            }
        } else {
            try {
                columnIdx = resultSet.findColumn(columnName);
            } catch (Exception e) {
                System.out.println(columnName + " not found so go to next one");
            }
        }
        if (columnIdx != -1) {
            return resultSet.getObject(columnIdx);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T setRowFromResultSet(GenericDAO self, Class<T> clazz, ResultSet resultSet,
            Iterator<Field> fieldIterator, String motherFieldName) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();

        while (fieldIterator.hasNext()) {
            Field field = fieldIterator.next();
            if (!self.getFieldToSet().isEmpty()) {
                String fieldName = (motherFieldName != null) ? motherFieldName : clazz.getSimpleName();
                if (!self.getFieldToSet().contains(fieldName.toLowerCase() + "." + FieldReflection.getFieldName(field))) {
                    continue;
                }
            }
            field.setAccessible(true);

            if (FieldReflection.isObject(field)) {
                Class<?> fieldClazz = field.getType();
                Field[] fieldsChild = FieldReflection.getFieldsNotIgnored(self, fieldClazz);
                List<Field> fieldsList = new ArrayList<>(List.of(fieldsChild));
                Iterator<Field> fieldIteratorChild = fieldsList.iterator();

                T instanceValue = (T) setRowFromResultSet(self, fieldClazz, resultSet, fieldIteratorChild,
                        GenericDAO.toSnakeCase(field.getName()));
                field.set(instance, instanceValue);
            } else {
                String columnName = FieldReflection.getFieldName(field);
                Object value = getValueFromResultSet(resultSet, columnName, motherFieldName);

                // Un champ herite d'une classe parente (Employe extends Personne, par ex.)
                // appartient bien a cette ligne : ne pas exiger declaringClass == clazz, sinon
                // les champs herites (dont potentiellement la cle primaire) restent toujours null.
                if (value != null) {
                    field.set(instance, FieldReflection.convertValue(value, field.getType()));
                    fieldIterator.remove();
                }
            }
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    static <T> T setFieldValueFromResultSet(GenericDAO self, Field field, String sqlFieldName, ResultSet resultSet,
            T instance, boolean throwIfFieldNotFound) throws Exception {
        field.setAccessible(true);
        Object value = null;
        try {
            int columnIdx = resultSet.findColumn(sqlFieldName);
            if (columnIdx != -1) {
                value = resultSet.getObject(columnIdx);
            }
            if (FieldReflection.isObject(field)) {
                String childFieldName = sqlFieldName.split("_", 2)[0]; // partie avant le premier "_"
                System.out.println("child field name " + childFieldName);
                Object valueFromObjetField = field.get(instance); // valeur deja instanciee, le cas echeant
                if (valueFromObjetField == null) {
                    valueFromObjetField = (T) field.getType().getDeclaredConstructor().newInstance();
                }
                Field childField = valueFromObjetField.getClass().getDeclaredField(childFieldName);
                String nomAttribut = GenericDAO.toCamelCase(sqlFieldName.split("_", 2)[1]); // nom d'attribut suppose
                System.out.println(
                        "nom attribut (sql après camelcase) :" + nomAttribut + " nom du field " + field.getName());
                if (nomAttribut.equalsIgnoreCase(field.getName())) {
                    childField.setAccessible(true);
                    childField.set(valueFromObjetField, FieldReflection.convertValue(value, childField.getType()));
                    field.set(instance, valueFromObjetField);
                }

            } else {
                field.set(instance, FieldReflection.convertValue(value, field.getType()));
            }
        } catch (SQLException e) {
            if (throwIfFieldNotFound) {
                throw new Exception(
                        "La colonne " + field.getName() + " n'existe pas dans la table " + self.getTableName());
            }
        }
        return instance;
    }
}
