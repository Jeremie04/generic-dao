package Generic.dao;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.lang.reflect.Array;

import Generic.annotation.*;
import Generic.connexion.Connexion;
import Generic.exceptions.NotFoundException;
import Generic.util.Pagination;
import Generic.util.ParserAttributs;

public class GenericDAO {
    protected Method method;
    private int initialValue = 2066;
    private String tableName = "";
    private Integer debut = null;
    private Integer fin = null;
    private String ordre = "";
    // temporary ignored Field
    private List<String> ignoredFields = new ArrayList<>();
    // only fields to consider
    private List<String> fieldsToSet = new ArrayList<>();
    // primary key
    private Field primaryKey = null;
    // filtre
    private boolean filterStringstart = false; // filtrer les valeur des string du debut
    private boolean filterStringend = false; // filtrer les valeurs des string du fin
    private String recherche = null; // le nom à rechercher
    private List<String> fieldsToResearch = new ArrayList<>(); // noms comme dans la base
    private String otherConditions = "";
    // pagination
    private boolean paginable = false;
    private Pagination pagination = null;

    public GenericDAO() {
        // Constructeur par défaut
    }

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

    private boolean isObject(Class<?> type) {
        return !(type.isPrimitive() || type.equals(String.class) ||
                type.equals(Boolean.class) || type.equals(Character.class) ||
                Number.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)
                || Timestamp.class.isAssignableFrom(type));
    }

    private boolean isObject(Field field) {
        return isObject(field.getType());
    }

    private Field[] getFieldsNotIgnored(Class<?> clazz) throws Exception {
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
                    AField fieldAnnotation = field.getAnnotation(AField.class);
                    String fieldName = getFieldName(field);
                    String className = clazz.getSimpleName();
                    className = Character.toLowerCase(className.charAt(0)) + className.substring(1);
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
        } else if (value instanceof String) {
            return !value.toString().isEmpty();
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
            if (isPrimaryKey(field)) {
                this.setPrimaryKey(field);
                return field;
            }
        }
        return null;
    }

    private PreparedStatement setValueToField(PreparedStatement stat, Object value, int index) throws Exception {
        if (value instanceof String) {
            String val = (String) value;
            if (this.getRecherche() != null) {
                if (isFilterStringend()) {
                    val = "%" + val;
                }
                if (isFilterStringstart()) {
                    val = val + "%";
                }
            }
            stat.setString(index, val);
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

    public static String toCamelCase(String input) {
        String[] words = input.split("_");
        StringBuilder result = new StringBuilder(words[0]); // Garde le premier mot tel quel

        for (int i = 1; i < words.length; i++) {
            result.append(Character.toUpperCase(words[i].charAt(0)))
                    .append(words[i].substring(1));
        }

        return result.toString();
    }

    public static String toSnakeCase(String input) {
        StringBuilder result = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append("_").append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
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
        } catch (SQLException e) {
            if (commit) {
                try {
                    con.rollback();
                    System.out.println("Rollback executed");
                } catch (SQLException rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            throw new Exception("SQL execution failed: " + e.getMessage(), e);
        } finally {
            if (close || isClose) {
                try {
                    con.close();
                    System.out.println("Connection closed");
                } catch (SQLException e) {
                    System.err.println("Failed to close connection: " + e.getMessage());
                }
            }
        }
    }

    /* CRUD FONCTIONS */

    /*
     * SAVE
     */

    private String prepareSaveSQL(Class<?> clazz, Field[] fields) throws Exception {
        String tableName = getTableName(clazz);
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner values = new StringJoiner(", ");

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = isObject(field) ? getFieldNameIfObject(field, getFieldName(field), this)
                    : getFieldName(field);
            columns.add(fieldName);
            values.add("?");
        }

        String sql = "INSERT INTO " + tableName + " (" + columns.toString() + ") VALUES (" + values.toString() + ")";
        Field primaryKey = getPrimaryKey(clazz);
        if (primaryKey == null)
            throw new Exception("Aucune clé primaire trouvé");
        sql = sql + " RETURNING " + getFieldName(primaryKey);
        System.out.println(sql);
        return sql;
    }

    public Object save(Connection con, boolean isClose, boolean commit) throws Exception {
        boolean closeConnection = false;
        if (con == null) {
            con = getConnection();
            closeConnection = true;
        }

        try {
            Class<?> clazz = this.getClass();
            Field[] allFields = getFieldsNotIgnored(clazz);
            Field[] fields = getFieldsNotNull(this, allFields);
            String sql = prepareSaveSQL(clazz, fields);
            Object idValue = null;
            try (PreparedStatement statement = con.prepareStatement(sql)) {
                prepareStatement(statement, fields, this);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        idValue = rs.getObject(1);
                    }
                }
                if (commit) {
                    con.commit();
                    System.out.println("Committed");
                }
                return idValue;
            } catch (SQLException e) {
                throw new Exception("SQL execution failed: " + e.getMessage(), e);
            }
        } finally {
            if (closeConnection || isClose) {
                try {
                    con.close();
                    System.out.println("Connection closed");
                } catch (SQLException e) {
                    System.err.println("Failed to close connection: " + e.getMessage());
                }
            }
        }
    }

    public <T> void save(Connection con, T[] objects, boolean isClose, boolean commit) throws Exception {
        boolean closeConnection = false;
        if (con == null) {
            con = getConnection();
            closeConnection = true;
        }

        try {
            Field[] allFields = getFieldsNotIgnored(objects[0].getClass());
            Field[] fields = getFieldsNotNull(objects[0], allFields);
            String sql = prepareSaveSQL(objects[0].getClass(), fields);

            try (PreparedStatement statement = con.prepareStatement(sql)) {
                for (T object : objects) {
                    prepareStatement(statement, fields, object);
                    // sql se termine par RETURNING <primaryKey> (voir prepareSaveSQL) : le driver
                    // Postgres exige executeQuery() ici, executeUpdate() lève
                    // "A result was returned when none was expected".
                    try (ResultSet rs = statement.executeQuery()) {
                        // rien à lire : l'API de save() en lot ne renvoie pas les ids générés.
                    }
                    statement.clearParameters();
                }

                if (commit) {
                    con.commit();
                    System.out.println("Committed");
                }
            } catch (SQLException e) {
                if (commit) {
                    try {
                        con.rollback();
                        System.out.println("Rollback executed");
                    } catch (SQLException rollbackEx) {
                        System.err.println("Rollback failed: " + rollbackEx.getMessage());
                    }
                }
                throw new Exception("SQL execution failed: " + e.getMessage(), e);
            }
        } finally {
            if (closeConnection || isClose) {
                try {
                    con.close();
                    System.out.println("Connection closed");
                } catch (SQLException e) {
                    System.err.println("Failed to close connection: " + e.getMessage());
                }
            }
        }
    }

    /*
     * SELECT
     */
    private String prepareAcondition(Field field) throws Exception {
        String fieldName = getFieldName(field);
        String equal = " = ? ";
        if (field.getDeclaringClass() != this.getClass()) {
            fieldName = fieldName + field.getDeclaringClass().getSimpleName();
        }
        if (isObject(field)) {
            fieldName = getFieldNameIfObject(field, fieldName, this);
        }
        if (doesFilterExist() && (field.getType().equals(String.class) || field.getType().equals(Character.class))) {
            equal = " LIKE ?";
        }
        return fieldName + equal;
    }

    public String prepareResearchCondition(Field[] fields) throws Exception {
        StringBuilder conditionBuilder = new StringBuilder(" OR ");

        if (getRecherche() != null) {
            String[] values = getRecherche().split(" ");
            for (Field field : fields) {
                if (field.getType() == String.class && !isPrimaryKey(field)) {
                    String fieldName = getFieldName(field);
                    if (field.getDeclaringClass() != this.getClass()
                            && !fieldName.toLowerCase()
                                    .endsWith("_" + field.getDeclaringClass().getSimpleName().toLowerCase())) {
                        fieldName = fieldName + "_" + field.getDeclaringClass().getSimpleName();
                    }

                    StringJoiner valueJoiner = new StringJoiner(" OR ");
                    for (String value : values) {
                        StringBuilder valueBuilder = new StringBuilder();
                        valueBuilder.append(fieldName).append(" ILIKE '");
                        if (isFilterStringend()) {
                            valueBuilder.append("%");
                        }
                        valueBuilder.append(value);
                        if (isFilterStringstart()) {
                            valueBuilder.append("%");
                        }
                        valueBuilder.append("'");
                        valueJoiner.add(valueBuilder.toString());
                    }

                    conditionBuilder.append(valueJoiner.toString()).append(" OR ");
                } else if (isObject(field)) {
                    conditionBuilder.append(prepareResearchCondition(field.getType().getDeclaredFields()));
                }
            }
        }

        String condition = conditionBuilder.toString();
        if (condition.endsWith(" OR ")) {
            condition = condition.substring(0, condition.length() - 4); // Remove the last " OR "
        }

        return condition;
    }

    public String prepareResearchConditionFromFieldNames(List<String> fields) {
        StringBuilder conditionBuilder = new StringBuilder(" OR ");

        if (getRecherche() != null) {
            String[] values = getRecherche().split(" ");

            for (String fieldName : fields) {
                StringJoiner valueJoiner = new StringJoiner(" OR ");
                for (String value : values) {
                    StringBuilder valueBuilder = new StringBuilder();
                    valueBuilder.append(fieldName).append(" ILIKE '");
                    if (isFilterStringend()) {
                        valueBuilder.append("%");
                    }
                    valueBuilder.append(value);
                    if (isFilterStringstart()) {
                        valueBuilder.append("%");
                    }
                    valueBuilder.append("'");
                    valueJoiner.add(valueBuilder.toString());
                }
                conditionBuilder.append(valueJoiner.toString()).append(" OR ");
            }
        }

        String condition = conditionBuilder.toString();
        if (condition.endsWith(" OR ")) {
            condition = condition.substring(0, condition.length() - 4); // Remove the last " OR "
        }

        return condition;
    }

    private String getResearchCondition(Field[] fields) throws Exception {
        if (this.getFieldToResearch().isEmpty()) {
            String researchCondition = prepareResearchCondition(fields);
            return researchCondition.replace("  ", " ").replace("OR OR", "OR");
        } else {
            return prepareResearchConditionFromFieldNames(this.getFieldToResearch());
        }
    }

    public String prepareSelectSQL(Connection con, String columns, String tableName, Field[] fields, Field[] allFields)
            throws Exception {
        StringBuilder sqlBuilder = new StringBuilder();
        String columnString = (columns == null) ? "*" : columns;
        sqlBuilder.append("SELECT ").append(columnString).append(" FROM ").append(tableName);

        String researchCondition = getResearchCondition(allFields);
        // researchCondition commence par " OR " pour s'enchainer apres les conditions exactes
        // (fields) ou apres otherConditions. Sans rien avant elle, ce " OR " en tete produirait
        // un "WHERE OR ..." invalide : on le retire dans ce cas precis.
        if (fields.length == 0 && getOtherConditions().isEmpty()) {
            researchCondition = researchCondition.replaceFirst("^\\s*OR\\s+", " ");
        }
        if (fields.length > 0 || (!researchCondition.isEmpty()
                && !researchCondition.equals(" ")
                && !researchCondition.toLowerCase().contains("WHERE"))) {
            sqlBuilder.append(" WHERE ");
        }

        if (sqlBuilder.indexOf("WHERE") == -1 && !getOtherConditions().isEmpty()
                && !getOtherConditions().toLowerCase().contains("where")) {
            sqlBuilder.append(" WHERE ");
        }
        sqlBuilder.append(getOtherConditions());

        if (!getOtherConditions().isEmpty() && fields.length > 0) {
            sqlBuilder.append(" AND ");
        }

        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sqlBuilder.append(" AND ");
            }
            String condition = prepareAcondition(fields[i]);
            sqlBuilder.append(condition);
        }

        sqlBuilder.append(researchCondition);

        prepagePaginationIfAllowed(con, this.getClass(), sqlBuilder.toString(), fields);

        sqlBuilder.append(" ").append(getOrdre());

        if (getDebut() != null && getFin() != null) {
            int fin = getFin() - getDebut();
            sqlBuilder.append(" OFFSET ").append(getDebut()).append(" LIMIT ").append(fin);
        }

        String sql = sqlBuilder.toString().replace("  ", " ").replace(" WHERE WHERE ", " WHERE ");
        System.out.println(sql);
        return sql;
    }

    private Object getValueFromResultSet(ResultSet resultSet, String columnName, String motherFieldName)
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
    private <T> T setRowFromResultSet(Class<T> clazz, ResultSet resultSet, Iterator<Field> fieldIterator,
            String motherFieldName) throws Exception {
        T instance = clazz.getDeclaredConstructor().newInstance();

        while (fieldIterator.hasNext()) {
            Field field = fieldIterator.next();
            if (!this.getFieldToSet().isEmpty()) {
                String fieldName = (motherFieldName != null) ? motherFieldName : clazz.getSimpleName();
                if (!this.getFieldToSet().contains(fieldName.toLowerCase() + "." + getFieldName(field))) {
                    continue;
                }
            }
            field.setAccessible(true);

            if (isObject(field)) {
                Class<?> fieldClazz = field.getType();
                Field[] fieldsChild = getFieldsNotIgnored(fieldClazz);
                List<Field> fieldsList = new ArrayList<>(List.of(fieldsChild));
                Iterator<Field> fieldIteratorChild = fieldsList.iterator();

                T instanceValue = (T) setRowFromResultSet(fieldClazz, resultSet, fieldIteratorChild,
                        toSnakeCase(field.getName()));
                field.set(instance, instanceValue);
            } else {
                String columnName = getFieldName(field);
                Object value = getValueFromResultSet(resultSet, columnName, motherFieldName);

                if (value != null && field.getDeclaringClass() == clazz) {
                    field.set(instance, convertValue(value, field.getType()));
                    fieldIterator.remove();
                }
            }
        }
        return instance;
    }

    public int getResultSize(Connection co, Class<?> clazz, String sql, Field[] fields) throws Exception {
        if (co == null)
            throw new Exception("Connection ne doit pas être null");
        String tableName = getTableName(clazz);
        String request = "SELECT count(*) count FROM (" + sql + ") as sql";
        System.out.println(request);
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
            System.out.println("Result size for sql is " + totalSize + " start : " + this.getPagination().getStart()
                    + " end :" + this.getPagination().getEnd());
            this.getPagination().setTotalSize(totalSize);
            this.setLimit(this.getPagination().getBeginIndex(), this.getPagination().getEndIndex());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T[] select(Connection con, boolean isClose, String sql) throws Exception {
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
            System.out.println(sql);
            statement = con.prepareStatement(sql);
            statement = prepareStatement(statement, notNullFields, this);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                List<Field> fieldsList = new ArrayList<>(Arrays.asList(allfields));
                Iterator<Field> FieldIterator = fieldsList.iterator();
                T instance = setRowFromResultSet(clazz, resultSet, FieldIterator, null);
                resultList.add(instance);
            }

            T[] resultArray = (T[]) Array.newInstance(clazz, resultList.size());
            return resultList.toArray(resultArray);
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

    // Select avec attribut

    @SuppressWarnings("unchecked")
    // pour une selection en precisant les attributs
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
                String childFieldName = sqlFieldName.split("_", 2)[0]; // Récupère la partie avant le premier "_" dans
                                                                       // sqlFieldName
                System.out.println("child field name " + childFieldName);
                Object valueFromObjetField = field.get(instance); // getValeur du field qui pourrait déjà être instancié
                if (valueFromObjetField == null) {
                    valueFromObjetField = (T) field.getType().getDeclaredConstructor().newInstance();
                }
                Field childField = valueFromObjetField.getClass().getDeclaredField(childFieldName);
                String nomAttribut = toCamelCase(sqlFieldName.split("_", 2)[1]); // le supposé nom de l'attribut dans la
                                                                                 // classe
                System.out.println(
                        "nom attribut (sql après camelcase) :" + nomAttribut + " nom du field " + field.getName());
                if (nomAttribut.equalsIgnoreCase(field.getName())) {
                    childField.setAccessible(true);
                    childField.set(valueFromObjetField, convertValue(value, childField.getType()));
                    field.set(instance, valueFromObjetField);
                }

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
     * SELECT ONE
     */
    public <T> T selectOne(Connection con, boolean isClose) throws Exception {
        this.setLimit(0, 1);
        T[] results = select(con, isClose);
        if (results.length == 0) {
            return null;
        }
        this.setDebut(null);
        this.setFin(null);
        return results[0];
    }

    /*
     * FIND BY ID
     */

    public <T> T findById(Connection con, Object id, boolean isClose) throws Exception {
        Field primaryKey = getPrimaryKey(this.getClass());
        if (primaryKey == null)
            throw new Exception("Primary Key is undefined");
        primaryKey.setAccessible(true);
        primaryKey.set(this, convertValue(id, primaryKey.getType()));
        T[] row = select(con, isClose);
        if (row.length == 0)
            throw new NotFoundException(
                    "Primary Key: " + id + " not found for table " + this.getClass().getSimpleName());
        return row[0];
    }

    public <T> T findById(Connection con, boolean isClose) throws Exception {
        Field primaryKey = getPrimaryKey(this.getClass());
        if (primaryKey == null)
            throw new Exception("Primary Key is undefined");
        primaryKey.setAccessible(true);
        Object id = primaryKey.get(this);
        T[] row = select(con, isClose);
        if (row.length == 0)
            throw new NotFoundException(
                    "Primary Key: " + id + " not found for table " + this.getClass().getSimpleName());
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

    private String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    private Integer getDebut() {
        return debut;
    }

    private void setDebut(Integer debut) throws Exception {
        if (debut != null && debut < 0)
            throw new Exception("valeur debut limite invalide");
        this.debut = debut;
    }

    private Integer getFin() {
        return fin;
    }

    private void setFin(Integer fin) throws Exception {
        if (fin != null && fin < 0)
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

    private String getOtherConditions() {
        return otherConditions;
    }

    public void setOtherConditions(String otherConditions) {
        this.otherConditions = otherConditions;
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

    private String getRecherche() {
        return recherche;
    }

    public void setRecherche(String recherche, String... colums) {
        this.recherche = recherche;
        for (String field : colums) {
            fieldsToResearch.add(field);
        }
    }

    public void setIgnoredFields(String... fields) {
        for (String field : fields) {
            ignoredFields.add(field);
        }
    }

    private List<String> getFieldToResearch() {
        return this.fieldsToResearch;
    }

    private List<String> getFieldToSet() {
        return this.fieldsToSet;
    }

    private Field getPrimaryKey() {
        return this.primaryKey;
    }

    public void setPrimaryKey(Field primaryKey) throws Exception {
        if (primaryKey == null)
            throw new Exception("Primary key Null");
        this.primaryKey = primaryKey;
    }

    // [materiel.categorie.etat.nom, materiel.categorie.etat.code]
    // => materiel.categorie, categorie.etat, etat.nom, etat.code
    public void setFieldsToSet(String... fields) {
        Set<String> resultat = new LinkedHashSet<>();
        for (String field : fields) {
            String[] parties = field.split("\\.");

            for (int i = 1; i < parties.length; i++) {
                String niveau = String.join(".", Arrays.copyOfRange(parties, i - 1, i + 1));
                resultat.add(niveau);
            }
        }
        this.fieldsToSet = new ArrayList<>(resultat);
    }

    // Dans le cas où les attributs sont nombreux
    // ex : materiel(id, categorie(id, etat(nom, code)), designation)
    public void setFieldsToSetSimplified(String inputs) {
        this.fieldsToSet = ParserAttributs.parser(inputs);
    }
}
