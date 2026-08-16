package Generic.dao;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reconstruction d'objets Java a partir d'un ResultSet : mapping colonne -> champ,
 * y compris les objets imbriques (colonnes prefixees "<colonne>_<nom du champ objet>").
 */
final class ResultSetMapper {

    private static final Logger LOG = Logger.getLogger(ResultSetMapper.class.getName());

    private ResultSetMapper() {
    }

    /**
     * Precalcule une seule fois par requete (pas par ligne ni par champ) la correspondance
     * nom de colonne en minuscules -> index. Sert a savoir si une colonne existe sans
     * s'appuyer sur l'exception que leve ResultSet.findColumn() quand elle est absente :
     * construire une exception a un cout reel en Java, et setRowFromResultSet() tente
     * plusieurs noms de colonnes candidats pour chaque champ de chaque ligne.
     */
    static Map<String, Integer> buildColumnIndex(ResultSet resultSet) throws SQLException {
        ResultSetMetaData meta = resultSet.getMetaData();
        Map<String, Integer> index = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            // putIfAbsent : en cas de colonnes en double (JOIN sans alias), on garde le
            // premier index, comme le ferait ResultSet.findColumn().
            index.putIfAbsent(meta.getColumnLabel(i).toLowerCase(), i);
        }
        return index;
    }

    static Object getValueFromResultSet(ResultSet resultSet, Map<String, Integer> columnIndex, String columnName,
            String motherFieldName) throws Exception {
        Integer columnIdx;
        if (motherFieldName != null) {
            columnIdx = columnIndex.get((columnName + "_" + motherFieldName).toLowerCase());
        } else {
            columnIdx = columnIndex.get(columnName.toLowerCase());
        }
        if (columnIdx != null) {
            return resultSet.getObject(columnIdx);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static <T> T setRowFromResultSet(GenericDAO self, Class<T> clazz, ResultSet resultSet,
            Map<String, Integer> columnIndex, Iterator<Field> fieldIterator, String motherFieldName)
            throws Exception {
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

                T instanceValue = (T) setRowFromResultSet(self, fieldClazz, resultSet, columnIndex,
                        fieldIteratorChild, GenericDAO.toSnakeCase(field.getName()));
                field.set(instance, instanceValue);
            } else {
                String columnName = FieldReflection.getFieldName(field);
                Object value = getValueFromResultSet(resultSet, columnIndex, columnName, motherFieldName);

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
            Map<String, Integer> columnIndex, T instance, boolean throwIfFieldNotFound) throws Exception {
        field.setAccessible(true);
        Object value = null;
        try {
            Integer columnIdx = columnIndex.get(sqlFieldName.toLowerCase());
            if (columnIdx != null) {
                value = resultSet.getObject(columnIdx);
            }
            if (FieldReflection.isObject(field)) {
                String childFieldName = sqlFieldName.split("_", 2)[0]; // partie avant le premier "_"
                LOG.finest(() -> "child field name " + childFieldName);
                Object valueFromObjetField = field.get(instance); // valeur deja instanciee, le cas echeant
                if (valueFromObjetField == null) {
                    valueFromObjetField = (T) field.getType().getDeclaredConstructor().newInstance();
                }
                Field childField = valueFromObjetField.getClass().getDeclaredField(childFieldName);
                String nomAttribut = GenericDAO.toCamelCase(sqlFieldName.split("_", 2)[1]); // nom d'attribut suppose
                LOG.finest(() -> "nom attribut (sql après camelcase) :" + nomAttribut + " nom du field "
                        + field.getName());
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
