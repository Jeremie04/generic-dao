package Generic.dao.legacy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Array;

import Generic.annotation.*;
import Generic.connexion.Connexion;
import Generic.dao.GenericDAO;
import Generic.util.Pagination;

/**
 * Ancienne version de {@link GenericDAO}, conservée ici pour référence.
 * Non utilisée par le reste du module : ne pas étendre, préférer GenericDAO.
 */
public class GenericDAO2 {
    protected Method method;
    private int initialValue = 2066;
    private String tableName = "";
    private Integer debut = null;
    private Integer fin = null;
    private String ordre = "";
    // temporary ignored Field
    private List<String> ignoredFields = new ArrayList<>();
    // filtre
    private boolean filterStringstart = false; // filtrer les valeur des string du debut
    private boolean filterStringend = false; // filtrer les valeurs des string du fin
    private String recherche = null; // le nom à rechercher
    private String OtherConditions = "";
    // pagination
    private boolean paginable = false;
    private Pagination pagination;

    /* Small FONCTIONS */
    // Initialize types primitifs
    public void init() throws Exception {
        System.out.println("Initializing " + this.getClass());
        Class c = this.getClass();
        Field[] f = c.getDeclaredFields();
        for (int i = 0; i < f.length; i++) {
            if (f[i].getType().isPrimitive()) {
                method = this.getClass().getMethod("set" + f[i].getName().substring(0, 1).toUpperCase()
                        + f[i].getName().toString().substring(1), f[i].getType());
                if (f[i].getType().toString().contains("int")
                        || f[i].getType().toString().contains("float")
                        || f[i].getType().toString().contains("double")
                        || f[i].getType().toString().contains("long")) {
                    method.invoke(this, initialValue);
                    if (method.getName().equals("setId")) {
                        System.out.println("initialize id with value " + initialValue + " in object " + this);
                    }
                }
            }
        }
    }

    private String getTableName(Class<?> clazz) {
        AClass classAnnotation = clazz.getAnnotation(AClass.class);
        if (!this.getTableName().equals("")) {
            return this.getTableName();
        } else if (classAnnotation != null && !classAnnotation.tableName().isEmpty()) {
            return classAnnotation.tableName();
        } else {
            return clazz.getSimpleName();
        }
    }

    private String getFieldName(Field field) {
        AField fieldAnnotation = field.getAnnotation(AField.class);
        if (fieldAnnotation != null && !fieldAnnotation.column().isEmpty()) {
            return fieldAnnotation.column();
        } else {
            return field.getName();
        }
    }

    private String getFieldNameIfObject(Field field, String fieldName, Object objet) throws Exception {
        if (isObject(field)) {
            Object fieldObjet = field.get(objet);
            Field fieldprimaryKey = getPrimaryKey(fieldObjet.getClass());
            if (fieldprimaryKey == null) {
                throw new Exception("Primary key not found");
            }
            fieldName = fieldprimaryKey.getName() + "_" + fieldName;
            return fieldName;
        }
        throw new Exception(
                "On ne peut pas prendre le primary key du champ " + field.getName() + " n'est pas un objet ");
    }

    private <T> Iterator<T> addToIterator(Iterator<T> iterator, T[] array) throws Exception {
        List<T> fieldsChildList = Arrays.asList(array);
        List<T> combinedList = new ArrayList<>();
        while (iterator.hasNext()) {
            combinedList.add(iterator.next());
        }
        combinedList.addAll(fieldsChildList);
        return combinedList.iterator();
    }

    private boolean isObject(Class<?> type) {
        return !type.isPrimitive() && !type.getName().startsWith("java.");
        // return !(type.isPrimitive() || type == String.class || type == Boolean.class
        // || type == Integer.class ||
        // type == Long.class || type == Double.class || type == Float.class || type ==
        // Short.class ||
        // type == Byte.class || type == java.util.Date.class || type ==
        // java.sql.Date.class);
    }

    private boolean isObject(Field field) {
        Class<?> type = field.getType();
        return isObject(type);
    }

    private <T> T[] combineArray(Class<?> clazz, T[] array1, T[] array2) throws Exception {
        @SuppressWarnings("unchecked")
        T[] combination = (T[]) Array.newInstance(clazz, array1.length + array2.length);
        int id = 0;
        for (T item1 : array1) {
            combination[id] = item1;
            id++;
        }
        for (T item2 : array2) {
            combination[id] = item2;
            id++;
        }
        return combination;
    }

    private Field[] getFieldsNotIgnored(Class<?> clazz) throws Exception {
        // System.out.println("super Class :" + clazz.getSuperclass());
        Field[] fields = clazz.getDeclaredFields();
        if (clazz.getSuperclass() != GenericDAO.class && clazz.getSuperclass() != java.util.Date.class
                && clazz.getSuperclass() != java.sql.Timestamp.class && clazz.getSuperclass() != java.sql.Time.class) {
            Field[] superClassFields = getFieldsNotIgnored(clazz.getSuperclass());
            fields = combineArray(Field.class, fields, superClassFields);
        }
        return Arrays.stream(fields)
                .filter(field -> {
                    AField fieldAnnotation = field.getAnnotation(AField.class);
                    String fieldName = getFieldName(field);
                    String className = clazz.getSimpleName();
                    className = className.substring(0, 1).toLowerCase() + className.substring(1);
                    // System.out.println(fieldName + "_" + className);
                    return (fieldAnnotation == null || !fieldAnnotation.ignored()) &&
                            !ignoredFields.contains(fieldName + "_" + className);
                })
                .toArray(Field[]::new);
    }

    private Field[] getFieldsNotNull(Object object, Field[] fields) {
        List<Field> notNullFields = new ArrayList<>();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(object);
                if (field.get(object) != null && isFieldValueSet(value)) {
                    // System.out.println(field + " not null, value :" + field.get(object));
                    notNullFields.add(field);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return notNullFields.toArray(new Field[notNullFields.size()]);
    }

    private boolean isFieldValueSet(Object value) {
        if (value instanceof Integer) {
            return (Integer) value != initialValue && (Integer) value != 0;
        } else if (value instanceof Double) {
            return !((Double) value).equals((double) initialValue) && (double) value != 0;
        }
        return true;
    }

    private boolean isPrimaryKey(Field field) throws Exception {
        AField fieldAnnotation = field.getAnnotation(AField.class);
        return fieldAnnotation != null && fieldAnnotation.isId();
    }

    private Field getPrimaryKey(Class<?> clazz) throws Exception {
        Field[] fields = getFieldsNotIgnored(clazz);
        for (Field field : fields) {
            if (isPrimaryKey(field))
                return field;
            else
                System.out.println(field.getName() + " not primary key");
        }
        return null;
    }

    private PreparedStatement setValueToField(PreparedStatement stat, Object value, int index)
            throws Exception {
        if (value instanceof String && this.getRecherche() != null) {
            String val = "";
            if (isFilterStringend()) {
                val = val + "%";
            }
            val = val + value.toString();
            if (isFilterStringstart()) {
                val = val + "%";
            }
            stat.setString(index, val);
        } else if (value instanceof String) {
            // System.out.println("value set to stat " + value.toString());
            stat.setString(index, value.toString());
        } else if (value instanceof Date) {
            stat.setDate(index, (Date) value);
        } else {
            stat.setObject(index, value);
        }
        return stat;
    }

    private PreparedStatement prepareStatement(PreparedStatement stat, Field[] fields, Object obj) throws Exception {
        int index = 1;
        for (Field field : fields) {
            field.setAccessible(true);
            if (isObject(field)) { // si objet, représenter la valeur de son primary key
                Object fieldObjet = field.get(obj);
                Field fieldprimaryKey = getPrimaryKey(fieldObjet.getClass());
                fieldprimaryKey.setAccessible(true);
                Object fieldprimaryKeyValue = fieldprimaryKey.get(fieldObjet);
                stat = setValueToField(stat, fieldprimaryKeyValue, index);
                index++;
            } else {
                Object value = field.get(obj);
                stat = setValueToField(stat, value, index);
                index++;
            }
        }
        return stat;
    }

    private Object convertValue(Object value, Class<?> fieldType) throws Exception {
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

    /* SMALL FONCTION */
    public static void executeSql(Connection con, String sql, boolean commit, boolean isClose) throws Exception {
        boolean close = false;
        if (con == null) {
            con = Connexion.getConnection(false);
            close = true;
        }
        try (Statement stat = con.createStatement()) {
            System.out.println(sql);
            stat.executeUpdate(sql);
            if (commit) {
                con.commit();
                System.out.println("Commited");
            }
        } finally {
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
    }

    public static void executeSql(String sql, boolean commit, boolean isClose) throws Exception {
        executeSql(null, sql, commit, isClose);
    }
    /* CRUD FONCTIONS */

    /*
     * SAVE
     */

    private String prepareSaveSQL(Class<?> clazz, Field[] fields) throws Exception {
        String tableName = getTableName(clazz);
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        boolean hasAutoIncrementId = false;

        for (Field field : fields) {
            field.setAccessible(true);
            if (columns.length() > 0) {
                columns.append(", ");
                values.append(", ");
            }
            if (isObject(field)) {
                columns.append(getFieldNameIfObject(field, field.getName(), this));
            } else {
                columns.append(getFieldName(field));
            }
            values.append("?");
        }

        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
        System.out.println(sql);
        return sql;
    }

    public void save(Connection con, boolean isClose, boolean commit) throws Exception {
        PreparedStatement statement = null;
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        try {
            Class<?> clazz = this.getClass();
            // System.out.println("this class " + clazz);
            Field[] allfields = getFieldsNotIgnored(clazz);
            Field[] fields = getFieldsNotNull(this, allfields);
            String sql = prepareSaveSQL(clazz, fields);
            statement = con.prepareStatement(sql);
            statement = prepareStatement(statement, fields, this);
            statement.executeUpdate();
            if (commit) {
                con.commit();
                System.out.println("Commited");
            }
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
    }

    public <T> void save(Connection con, T[] objects, boolean isClose, boolean commit) throws Exception {
        PreparedStatement statement = null;
        boolean close = false;
        if (con == null) {
            Connexion co = new Connexion();
            con = co.getConnect(false);
            close = true;
        }
        try {
            Field[] allfields = getFieldsNotIgnored(objects[0].getClass());
            Field[] fields = getFieldsNotNull(objects[0], allfields);
            String sql = prepareSaveSQL(objects[0].getClass(), fields);
            for (T objet : objects) {
                statement = con.prepareStatement(sql);
                statement = prepareStatement(statement, fields, objet);
                statement.executeUpdate();
                statement.clearParameters();
            }
            if (commit) {
                con.commit();
                System.out.println("Commited");
            }
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            if (statement != null) {
                statement.close();
            }
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
    }

    /*
     * SELECT
     */
    private String prepareAcondition(Field field) throws Exception {
        String fieldName = getFieldName(field);
        String equal = " = ? ";
        // System.out.println(field.getDeclaringClass() + " and " + this.getClass());
        if (field.getDeclaringClass() != this.getClass()) {
            fieldName = fieldName + field.getDeclaringClass().getSimpleName();
            // System.out.println("field " + field.getName() + " set to " + fieldName);
        }
        if (isObject(field)) {
            fieldName = getFieldNameIfObject(field, fieldName, this);
        }
        if (doesFilterExist() && (field.getType().equals(String.class) || field.getType().equals(Character.class))) {
            equal = " LIKE ?";
        }
        return fieldName + equal;
    }

    private String prepareResearchCondition(Field[] fields) throws Exception {
        String condition = "";
        if (recherche != null) {
            String[] values = recherche.split(" ");
            int index2 = 0;
            for (Field field : fields) {
                if (field.getType() == String.class && !isPrimaryKey(field)) {
                    String fieldName = getFieldName(field);
                    if (field.getDeclaringClass() != this.getClass()) {
                        fieldName = getFieldName(field) + "_" + field.getDeclaringClass().getSimpleName();
                    }
                    for (String value : values) {
                        condition = condition + " " + fieldName + " ILIKE ";
                        condition = condition + "'";
                        if (isFilterStringend())
                            condition = condition + "%";
                        condition = condition + value;
                        if (isFilterStringstart())
                            condition = condition + "%";
                        condition = condition + "'";
                        condition = condition + " OR ";
                    }
                } else if (isObject(field)) {
                    condition = condition + prepareResearchCondition(field.getType().getDeclaredFields());
                }
            }
        }
        return condition;
    }

    private String getResearchCondition(Field[] fields) throws Exception {
        String researchCondition = prepareResearchCondition(fields);
        if (!researchCondition.equals("")) {
            return researchCondition.substring(0, researchCondition.length() - 4);
        }
        return researchCondition;
    }

    private String prepareSelectSQL(Connection con, String columns, String tableName, Field[] fields, Field[] allFields)
            throws Exception {
        String columnString = (columns == null) ? "*" : columns;
        String sql = "SELECT " + columnString + " FROM " + tableName;
        String researchCondition = getResearchCondition(allFields);
        if (!sql.contains(" where ")
                && (fields.length > 0 || (!researchCondition.equals("") && !researchCondition.contains(" where ")))
                && !getOtherConditions().contains(" where ")) {
            sql = sql + " where ";
        }
        sql = sql + " " + getOtherConditions()
                + ((!getOtherConditions().isEmpty() && fields.length > 0) ? " and " : "");
        int index = 0;
        for (Field field : fields) {
            if (index != 0) {
                sql = sql + " and ";
            }
            String condition = prepareAcondition(field);
            sql = sql + condition;
            index++;
        }
        sql = sql + researchCondition;
        prepagePaginationIfAllowed(con, this.getClass(), sql, fields);
        sql = sql + " " + this.getOrdre();
        if (this.getDebut() != null && this.getFin() != null) {
            Integer fin = this.getFin() - this.getDebut();
            sql = sql + " OFFSET " + this.getDebut() + " limit " + fin;
        }
        System.out.println(sql);
        return sql;
    }

    private Object getValueFromResultSet(ResultSet resultSet, String columnName, String motherClassName)
            throws Exception {
        int columnIdx = -1;
        if (motherClassName != null) {
            // System.out.println("try " + columnName + "_" + motherClassName);
            try {
                columnIdx = resultSet.findColumn(columnName + "_" + motherClassName);
            } catch (Exception ex) {
                System.out.println(columnName + "_" + motherClassName + " not found so go to next one");
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
    private <T> T setFieldValueFromResultSet(Field field, String sqlFieldName, ResultSet resultSet, T instance,
            boolean throwifFieldNotFound) throws Exception {
        field.setAccessible(true);
        Object value = null;
        try {
            int columnIdx = resultSet.findColumn(sqlFieldName);
            if (columnIdx != -1) {
                value = resultSet.getObject(columnIdx);
            }
            if (isObject(field)) {
                String childFieldName = sqlFieldName.split("_", 2)[0];
                Object valueFromObjetField = field.get(instance); // getValeur du field qui pourrait déjà être instancié
                if (valueFromObjetField == null) {
                    valueFromObjetField = (T) field.getType().getDeclaredConstructor().newInstance();
                }
                Field childField = valueFromObjetField.getClass().getDeclaredField(childFieldName);
                childField.setAccessible(true);
                childField.set(valueFromObjetField, convertValue(value, childField.getType()));
                field.set(instance, valueFromObjetField);

            } else {
                field.set(instance, convertValue(value, field.getType()));
            }
        } catch (SQLException e) {
            if (throwifFieldNotFound) {
                throw new Exception("La colonne " + field.getName() + " n'existe pas dans la table " + tableName);
            }
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private <T> T setRowFromResultSet(Class<T> clazz, ResultSet resultSet, Iterator<Field> FieldIterator,
            String motherClassName) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();
        while (FieldIterator.hasNext()) {
            Field field = FieldIterator.next();
            field.setAccessible(true);
            if (isObject(field)) {
                Class<?> fieldClazz = field.getType();
                Field[] fieldsChild = getFieldsNotIgnored(fieldClazz);
                List<Field> fieldsList = new ArrayList<>(Arrays.asList(fieldsChild));
                Iterator<Field> FieldIteratorChild = fieldsList.iterator();
                // System.out.println("field " + field.getName() + " is an object");
                T instanceValue = (T) setRowFromResultSet(fieldClazz, resultSet, FieldIteratorChild,
                        fieldClazz.getSimpleName());
                // System.out.println("value :" + instanceValue + " set into " +
                // instance.getClass());
                field.set(instance, instanceValue);
            } else {
                String columnName = getFieldName(field);
                // System.out.println("columnName " + columnName);
                Object value = getValueFromResultSet(resultSet, columnName, motherClassName);
                // System.out.println("value :" + value + " set into " + instance.getClass());
                if (value == null) {
                    columnName = columnName + "_" + clazz.getSimpleName();
                    // System.out.println("null value précédement " + columnName);
                    value = getValueFromResultSet(resultSet, columnName, motherClassName);
                }
                if (value != null) {
                    // si le field appartient à la classe
                    // System.out.println(field.getDeclaringClass() + " and " +
                    // instance.getClass());
                    if (field.getDeclaringClass() == clazz) {
                        field.set(instance, convertValue(value, field.getType()));
                        FieldIterator.remove();
                    }
                }

            }
        }
        return instance;
    }

    private <T> T setRowFromResultSet(Class<T> clazz, ResultSet resultSet, Map<String, Field> columnFieldMap,
            Iterator<Field> fieldIterator,
            String motherClassName)
            throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();
        while (fieldIterator.hasNext()) {
            Field field = fieldIterator.next();
            field.setAccessible(true);
            String columnName = getFieldName(field);
            if (isObject(field)) {
                System.out.println("Field is an object: " + field.getName() + " (" + field.getType() + ")");
                Class<?> fieldClazz = field.getType();
                Field[] fieldsChild = getFieldsNotIgnored(fieldClazz);
                List<Field> fieldsList = new ArrayList<>(Arrays.asList(fieldsChild));
                Iterator<Field> FieldIteratorChild = fieldsList.iterator();
                // Map<String, Field> childColumnFieldMap = prepareColumnFieldMap(fieldClazz,
                // resultSet);
                Object childInstance = setRowFromResultSet(fieldClazz, resultSet, columnFieldMap,
                        FieldIteratorChild,
                        field.getDeclaringClass().getSimpleName());
                field.set(instance, childInstance);
            }
            if (motherClassName != this.getClass().getSimpleName()) {
                columnName = columnName + "_" + field.getDeclaringClass().getSimpleName();
                System.out.println("column name :" + columnName);
            }
            if (columnFieldMap.containsKey(columnName)) {
                System.out.println("Field name: " + columnName + " declaring class " + motherClassName);
                // Gérer les champs primitifs ou simples
                Object value = null;
                try {
                    value = resultSet.getObject(columnName);
                } catch (Exception e) {
                    try {
                        value = resultSet.getObject(columnName + "_" + clazz.getSimpleName());
                    } catch (Exception ex) {
                        System.out.println(
                                columnName + " or " + columnName + "_" + clazz.getSimpleName() + " not found ");
                    }
                }
                if (value != null) {
                    field.set(instance, convertValue(value, field.getType()));
                }
            }
        }
        return instance;
    }

    private Map<String, Field> prepareColumnFieldMap(Class<?> clazz, ResultSet resultSet) throws SQLException {
        Map<String, Field> columnFieldMap = new HashMap<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnName(i);
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                if (getFieldName(field).equalsIgnoreCase(columnName)
                        || getFieldName(field).equalsIgnoreCase(columnName + "_" + clazz.getSimpleName())) {
                    if (field.getDeclaringClass() != this.getClass()) {
                        columnName = columnName + "_" + field.getDeclaringClass().getSimpleName();
                    }
                    if (isColumnInResultSet(resultSet, columnName)) {
                        columnFieldMap.put(columnName, field);
                        break;
                    }
                } else if (isObject(field)) {
                    // Inclure les champs des objets imbriqués
                    Class<?> fieldClazz = field.getType();
                    Map<String, Field> childColumnFieldMap = prepareColumnFieldMap(fieldClazz, resultSet);
                    for (Map.Entry<String, Field> entry : childColumnFieldMap.entrySet()) {
                        columnFieldMap.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return columnFieldMap;
    }

    private boolean isColumnInResultSet(ResultSet resultSet, String columnName) {
        try {
            resultSet.findColumn(columnName);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public int getResultSize(Connection co, Class<?> clazz, String sql, Field[] fields) throws Exception {
        if (co == null)
            throw new Exception("Connection ne doit pas être null");
        String tableName = getTableName(clazz);
        String request = "SELECT count(*) count FROM (" + sql + ") as sql";
        // System.out.println(request);
        PreparedStatement stat = co.prepareStatement(request);
        try {
            stat = prepareStatement(stat, fields, this);
            try (ResultSet res = stat.executeQuery()) {
                if (res.next()) {
                    return res.getInt("count");
                }
            }
        } finally {
            stat.close();
        }
        return 0;
    }

    public int getTableSize(Connection co, Class<?> clazz) throws Exception {
        if (co == null)
            throw new Exception("Connection ne doit pas être null");
        String tableName = getTableName(clazz);
        String sql = "SELECT count(*) count FROM " + tableName;
        System.out.println(sql);
        try (PreparedStatement stat = co.prepareStatement(sql)) {
            try (ResultSet res = stat.executeQuery()) {
                if (res.next()) {
                    return res.getInt("count");
                }
            }
        }
        return 0;
    }

    private void prepagePaginationIfAllowed(Connection co, Class<?> clazz, String sql, Field[] fields)
            throws Exception {
        if (isPaginable()) {
            int totalSize = getResultSize(co, clazz, sql, fields);
            System.out.println("Result size for sql is " + totalSize);
            this.getPagination().setTotalSize(totalSize);
            this.setLimit(this.getPagination().getStart(), this.getPagination().getEnd());
        }
    }

    public <T> T[] select(Connection con, boolean isClose, String sql) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        List<T> resultList = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Class<T> clazz = ((Class<T>) this.getClass());
        try {
            Field[] allfields = getFieldsNotIgnored(clazz);
            Field[] notNullFields = getFieldsNotNull(this, allfields);
            statement = con.prepareStatement(sql);
            statement = prepareStatement(statement, notNullFields, this);
            resultSet = statement.executeQuery();
            Map<String, Field> columnFieldMap = null;
            while (resultSet.next()) {
                if (columnFieldMap == null) {
                    columnFieldMap = prepareColumnFieldMap(clazz, resultSet);
                    System.out.println("HashMap : " + columnFieldMap);
                }
                List<Field> listField = new ArrayList<>(Arrays.asList(allfields));
                T instance = setRowFromResultSet(clazz, resultSet, columnFieldMap,
                        listField.iterator(), this.getClass().getSimpleName());
                resultList.add(instance);
            }
        } finally {
            if (resultSet != null)
                resultSet.close();
            if (statement != null)
                statement.close();
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }

        @SuppressWarnings("unchecked")
        T[] resultArray = (T[]) Array.newInstance(clazz, resultList.size());
        return resultList.toArray(resultArray);
    }

    public <T> T[] select(Connection con, boolean isClose) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<T> clazz = ((Class<T>) this.getClass());
            Field[] allfields = getFieldsNotIgnored(clazz);
            Field[] notNullFields = getFieldsNotNull(this, allfields);
            String tableName = getTableName(clazz);
            String sql = prepareSelectSQL(con, null, tableName, notNullFields, allfields);

            return select(con, isClose, sql);
        } finally {
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
    }

    public <T> T[] selection(Connection con, boolean isClose, String sql) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        @SuppressWarnings("unchecked")
        Class<T> clazz = ((Class<T>) this.getClass());
        List<T> resultList = new ArrayList<>();
        System.out.println(sql);
        try (Statement statement = con.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(sql)) {
                Field[] allfields = getFieldsNotIgnored(clazz);
                while (resultSet.next()) {
                    List<Field> fieldsList = new ArrayList<>(Arrays.asList(allfields));
                    Iterator<Field> FieldIterator = fieldsList.iterator();
                    T instance = setRowFromResultSet(clazz, resultSet, FieldIterator, null);
                    resultList.add(instance);
                }

            }
        } finally {
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
        @SuppressWarnings("unchecked")
        T[] resultArray = (T[]) Array.newInstance(clazz, resultList.size());
        return resultList.toArray(resultArray);
    }

    private String toString(String[] attributs) {
        StringBuilder columnCombinaison = new StringBuilder();
        for (int i = 0; i < attributs.length; i++) {
            if (i != 0) {
                columnCombinaison.append(",");
            }
            columnCombinaison.append(attributs[i]);
        }
        return columnCombinaison.toString();
    }

    @SuppressWarnings("unchecked")
    public <T> T[] select(Connection con, String[] attributs, boolean isClose) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        List<T> resultList = new ArrayList<>();
        Class<T> clazz = ((Class<T>) this.getClass());
        try {
            Field[] allfields = getFieldsNotIgnored(clazz);
            Field[] notNullFields = getFieldsNotNull(this, allfields);
            String tableName = getTableName(clazz);
            String sql = prepareSelectSQL(con, toString(attributs), tableName, notNullFields, allfields);
            statement = con.prepareStatement(sql);
            statement = prepareStatement(statement, notNullFields, this);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (String attribut : attributs) {
                    Field field = null;
                    try {
                        field = clazz.getDeclaredField(attribut);
                        instance = setFieldValueFromResultSet(field, attribut, resultSet, instance, true);
                    } catch (Exception e) {
                        if (attribut.contains("_")) { // columnName_tableName ex: id_status, nom_status
                            String[] attributsSplited = attribut.split("_", 2);
                            Field objectField = null;
                            if (attributsSplited[1].equalsIgnoreCase(clazz.getSimpleName())) {
                                objectField = clazz.getDeclaredField(attributsSplited[0]); // si attribut de la classe
                                instance = setFieldValueFromResultSet(objectField, attribut, resultSet, instance,
                                        true);
                            } else {
                                objectField = clazz.getDeclaredField(attributsSplited[1]); // si attribut de l'alltribut
                                instance = setFieldValueFromResultSet(objectField, attribut, resultSet, instance,
                                        true);
                            }
                        } else {
                            throw e;
                        }
                    }
                }
                resultList.add(instance);
            }
        } finally {
            if (resultSet != null)
                resultSet.close();
            if (statement != null)
                statement.close();
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
        T[] resultArray = (T[]) Array.newInstance(clazz, resultList.size());
        return resultList.toArray(resultArray);
    }

    /*
     * FIND BY ID
     */

    public <T> T findById(Connection con, Object id, boolean isClose) throws Exception {
        @SuppressWarnings("unchecked")
        // T instance = (T) this.getClass().getDeclaredConstructor().newInstance();
        Field primaryKey = getPrimaryKey(this.getClass());
        if (primaryKey == null)
            throw new Exception("Primary Key is undefined");
        primaryKey.setAccessible(true);
        primaryKey.set(this, convertValue(id, primaryKey.getType()));
        T[] row = select(con, isClose);
        if (row.length == 0)
            throw new Exception("Primary Key: " + id + " not found for table " + this.getClass().getName());
        return row[0];
    }

    /*
     * UPDATE
     */

    private String prepareUpdateSQL(Class<?> clazz, Field[] fields, Field primaryKey) throws Exception {
        String tableName = getTableName(clazz);
        String sql = "UPDATE " + tableName + " SET ";
        for (int i = 0; i < fields.length; i++) {
            if (!isPrimaryKey(fields[i])) {
                String fieldName = getFieldName(fields[i]);
                if (isObject(fields[i])) {
                    fieldName = getFieldNameIfObject(fields[i], fieldName, this);
                }
                sql = sql + fieldName + " =  ?";
                if (i < fields.length - 1) {
                    sql = sql + ", ";
                }
            }
        }
        sql = sql + " WHERE " + getFieldName(primaryKey) + " = ?";

        System.out.println(sql);
        return sql;
    }

    private PreparedStatement prepareUpdateStatement(PreparedStatement stat, Field[] fields, Object idValue,
            Field primaryKey)
            throws Exception {
        int index = 1;
        for (Field field : fields) {
            field.setAccessible(true);
            if (!isPrimaryKey(field)) {
                if (isObject(field)) {
                    Object fieldObjet = field.get(this);
                    Field fieldprimaryKey = getPrimaryKey(fieldObjet.getClass());
                    fieldprimaryKey.setAccessible(true);
                    // System.out.println(fieldprimaryKey.getName() + " object : " + fieldObjet);

                    Object fieldprimaryKeyValue = fieldprimaryKey.get(fieldObjet);
                    stat = setValueToField(stat, fieldprimaryKeyValue, index);
                } else {
                    Object value = field.get(this);
                    stat = setValueToField(stat, value, index);
                }
                index++;
            }
        }
        setValueToField(stat, idValue, index);
        return stat;
    }

    public void update(Connection con, Object idValue, boolean isClose, boolean commit) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        try {
            Class<?> clazz = ((Class<?>) this.getClass());
            Field primaryKey = getPrimaryKey(clazz);
            if (primaryKey == null)
                throw new Exception("Primary Key is undefined");
            Field[] allfields = getFieldsNotIgnored(clazz);
            Field[] notNullFields = getFieldsNotNull(this, allfields);
            String sql = prepareUpdateSQL(clazz, notNullFields, primaryKey);
            try (PreparedStatement statement = con.prepareStatement(sql)) {
                PreparedStatement stat = prepareUpdateStatement(statement, notNullFields, idValue, primaryKey);
                stat.executeUpdate();
            }

            if (commit) {
                con.commit();
                System.out.println("Commited");
            }
        } finally {
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
    }

    /*
     * DELETE
     */

    private String prepareDeleteSQL(Class<?> clazz, Field primaryKey) {
        String tableName = getTableName(clazz);
        String sql = "DELETE FROM " + tableName + " WHERE " + getFieldName(primaryKey) + " = ?";
        System.out.println(sql);
        return sql;
    }

    public void delete(Connection con, Object idValue, boolean isClose, boolean commit) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        try {
            Class<?> clazz = ((Class<?>) this.getClass());
            Field primaryKey = getPrimaryKey(clazz);
            if (primaryKey == null)
                throw new Exception("Primary Key is undefined");
            primaryKey.setAccessible(true);
            primaryKey.set(this, convertValue(idValue, primaryKey.getType()));
            String sql = prepareDeleteSQL(clazz, primaryKey);
            Field[] fields = { primaryKey };
            try (PreparedStatement statement = con.prepareStatement(sql)) {
                PreparedStatement stat = prepareStatement(statement, fields, this);
                stat.executeUpdate();
            }
            if (commit) {
                con.commit();
                System.out.println("Commited");
            }
        } finally {
            if (close || (isClose && con != null)) {
                con.close();
                System.out.println("connection closed");
            }
        }
    }

    /*
     * Connection
     */

    public Connection getConnection() throws Exception {
        Connexion c = new Connexion();
        return c.getConnect();
    }

    /*
     * >>Getter Setter
     */

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    private Integer getDebut() {
        return debut;
    }

    private void setDebut(Integer debut) throws Exception {
        if (debut == null || debut < 0)
            throw new Exception("valeur debut limite invalide");
        this.debut = debut;
    }

    private Integer getFin() {
        return fin;
    }

    private void setFin(Integer fin) throws Exception {
        if (fin == null || fin < 0)
            throw new Exception("valeur fin limite invalide");
        this.fin = fin;
    }

    public void setLimit(Integer debut, Integer fin) throws Exception {
        if (debut > fin)
            throw new Exception("Debut " + debut + " plus grand que Fin " + fin);
        this.setDebut(debut);
        this.setFin(fin);
    }

    private String getOrdre() {
        return ordre;
    }

    public void setOrdre(String column, String ordre) throws Exception {
        if (!ordre.equalsIgnoreCase("ASC") && !ordre.equalsIgnoreCase("DESC"))
            throw new Exception("Ordre invalid vous avez ecrit " + ordre + " au lieu de ASC ou DESC");
        this.ordre = "ORDER BY " + column + " " + ordre;
    }

    private boolean isFilterStringstart() {
        return filterStringstart;
    }

    private void setFilterStringstart(boolean filterStringstart) {
        this.filterStringstart = filterStringstart;
    }

    private boolean isFilterStringend() {
        return filterStringend;
    }

    private boolean doesFilterExist() {
        return isFilterStringend() || isFilterStringstart();
    }

    private void setFilterStringend(boolean filterStringend) {
        this.filterStringend = filterStringend;
    }

    public void setFilter(boolean start, boolean end) throws Exception {
        setFilterStringstart(start);
        setFilterStringend(end);
    }

    public String getOtherConditions() {
        return OtherConditions;
    }

    public void setOtherConditions(String otherConditions) {
        OtherConditions = otherConditions;
    }

    private boolean isPaginable() {
        return paginable;
    }

    public void setPaginable(boolean paginable) {
        this.paginable = paginable;
    }

    public Pagination getPagination() {
        return pagination;
    }

    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    public String getRecherche() {
        return recherche;
    }

    public void setRecherche(String recherche) {
        this.recherche = recherche;
    }

    public void setIgnoredFields(String... fields) {
        for (String field : fields) {
            ignoredFields.add(field);
        }
    }
}
