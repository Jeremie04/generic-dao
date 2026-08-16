package Generic.dao;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import Generic.connexion.Connexion;
import Generic.exceptions.NotFoundException;
import Generic.util.Pagination;
import Generic.util.ParserAttributs;

/**
 * Classe a etendre pour obtenir le CRUD generique (save/select/update/delete/findById)
 * par reflexion, plus la recherche texte et la pagination. Porte l'etat de la requete en
 * cours (recherche, filtres, tri, pagination, ...) et orchestre les classes internes du
 * package : FieldReflection (metadonnees), SqlBuilder (construction SQL), StatementBinder
 * (liaison des parametres) et ResultSetMapper (reconstruction d'objets).
 */
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

    public Object save(Connection con, boolean isClose, boolean commit) throws Exception {
        boolean closeConnection = false;
        if (con == null) {
            con = getConnection();
            closeConnection = true;
        }

        try {
            Class<?> clazz = this.getClass();
            Field[] allFields = FieldReflection.getFieldsNotIgnored(this, clazz);
            Field[] fields = FieldReflection.getFieldsNotNull(this, this, allFields);
            String sql = SqlBuilder.prepareSaveSQL(this, clazz, fields);
            Object idValue = null;
            try (PreparedStatement statement = con.prepareStatement(sql)) {
                StatementBinder.prepareStatement(this, statement, fields, this);
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
            Field[] allFields = FieldReflection.getFieldsNotIgnored(this, objects[0].getClass());
            Field[] fields = FieldReflection.getFieldsNotNull(this, objects[0], allFields);
            String sql = SqlBuilder.prepareSaveSQL(this, objects[0].getClass(), fields);

            try (PreparedStatement statement = con.prepareStatement(sql)) {
                for (T object : objects) {
                    StatementBinder.prepareStatement(this, statement, fields, object);
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

    public String prepareResearchCondition(Field[] fields) throws Exception {
        return SqlBuilder.prepareResearchCondition(this, fields);
    }

    public String prepareResearchConditionFromFieldNames(List<String> fields) {
        return SqlBuilder.prepareResearchConditionFromFieldNames(this, fields);
    }

    public String prepareSelectSQL(Connection con, String columns, String tableName, Field[] fields,
            Field[] allFields) throws Exception {
        return SqlBuilder.prepareSelectSQL(this, con, columns, tableName, fields, allFields);
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
            Field[] allfields = FieldReflection.getFieldsNotIgnored(this, clazz);
            Field[] notNullFields = FieldReflection.getFieldsNotNull(this, this, allfields);
            System.out.println(sql);
            statement = con.prepareStatement(sql);
            statement = StatementBinder.prepareStatement(this, statement, notNullFields, this);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                List<Field> fieldsList = new ArrayList<>(Arrays.asList(allfields));
                Iterator<Field> FieldIterator = fieldsList.iterator();
                T instance = ResultSetMapper.setRowFromResultSet(this, clazz, resultSet, FieldIterator, null);
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
            Field[] allfields = FieldReflection.getFieldsNotIgnored(this, clazz);
            Field[] notNullFields = FieldReflection.getFieldsNotNull(this, this, allfields);
            String tableName = FieldReflection.getTableName(this, clazz);
            String sql = SqlBuilder.prepareSelectSQL(this, con, null, tableName, notNullFields, allfields);

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
                Field[] allfields = FieldReflection.getFieldsNotIgnored(this, clazz);
                while (resultSet.next()) {
                    List<Field> fieldsList = new ArrayList<>(Arrays.asList(allfields));
                    Iterator<Field> FieldIterator = fieldsList.iterator();
                    T instance = ResultSetMapper.setRowFromResultSet(this, clazz, resultSet, FieldIterator, null);
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
            Field[] allfields = FieldReflection.getFieldsNotIgnored(this, clazz);
            Field[] notNullFields = FieldReflection.getFieldsNotNull(this, this, allfields);
            String tableName = FieldReflection.getTableName(this, clazz);
            String sql = SqlBuilder.prepareSelectSQL(this, con, toString(attributs), tableName, notNullFields,
                    allfields);
            statement = con.prepareStatement(sql);
            statement = StatementBinder.prepareStatement(this, statement, notNullFields, this);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (String attribut : attributs) {
                    Field field = null;
                    try {
                        field = clazz.getDeclaredField(attribut);
                        instance = ResultSetMapper.setFieldValueFromResultSet(this, field, attribut, resultSet,
                                instance, true);
                    } catch (Exception e) {
                        if (attribut.contains("_")) { // columnName_tableName ex: id_status, nom_status
                            String[] attributsSplited = attribut.split("_", 2);
                            Field objectField = null;
                            if (attributsSplited[1].equalsIgnoreCase(clazz.getSimpleName())) {
                                objectField = clazz.getDeclaredField(attributsSplited[0]); // si attribut de la classe
                                instance = ResultSetMapper.setFieldValueFromResultSet(this, objectField, attribut,
                                        resultSet, instance, true);
                            } else {
                                objectField = clazz.getDeclaredField(attributsSplited[1]); // si attribut de l'alltribut
                                instance = ResultSetMapper.setFieldValueFromResultSet(this, objectField, attribut,
                                        resultSet, instance, true);
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
        Field primaryKey = FieldReflection.getPrimaryKey(this, this.getClass());
        if (primaryKey == null)
            throw new Exception("Primary Key is undefined");
        primaryKey.setAccessible(true);
        primaryKey.set(this, FieldReflection.convertValue(id, primaryKey.getType()));
        T[] row = select(con, isClose);
        if (row.length == 0)
            throw new NotFoundException(
                    "Primary Key: " + id + " not found for table " + this.getClass().getSimpleName());
        return row[0];
    }

    public <T> T findById(Connection con, boolean isClose) throws Exception {
        Field primaryKey = FieldReflection.getPrimaryKey(this, this.getClass());
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

    public void update(Connection con, Object idValue, boolean isClose, boolean commit) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        try {
            Class<?> clazz = ((Class<?>) this.getClass());
            Field primaryKey = FieldReflection.getPrimaryKey(this, clazz);
            if (primaryKey == null)
                throw new Exception("Primary Key is undefined");
            Field[] allfields = FieldReflection.getFieldsNotIgnored(this, clazz);
            Field[] notNullFields = FieldReflection.getFieldsNotNull(this, this, allfields);
            String sql = SqlBuilder.prepareUpdateSQL(this, clazz, notNullFields, primaryKey);
            try (PreparedStatement statement = con.prepareStatement(sql)) {
                PreparedStatement stat = StatementBinder.prepareUpdateStatement(this, statement, notNullFields,
                        idValue, primaryKey);
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

    public void delete(Connection con, Object idValue, boolean isClose, boolean commit) throws Exception {
        boolean close = false;
        if (con == null) {
            con = getConnection();
            close = true;
        }
        try {
            Class<?> clazz = ((Class<?>) this.getClass());
            Field primaryKey = FieldReflection.getPrimaryKey(this, clazz);
            if (primaryKey == null)
                throw new Exception("Primary Key is undefined");
            primaryKey.setAccessible(true);
            primaryKey.set(this, FieldReflection.convertValue(idValue, primaryKey.getType()));
            String sql = SqlBuilder.prepareDeleteSQL(this, clazz, primaryKey);
            Field[] fields = { primaryKey };
            try (PreparedStatement statement = con.prepareStatement(sql)) {
                PreparedStatement stat = StatementBinder.prepareStatement(this, statement, fields, this);
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
     * Requetes utilitaires (taille, pagination)
     */

    public int getResultSize(Connection co, Class<?> clazz, String sql, Field[] fields) throws Exception {
        if (co == null)
            throw new Exception("Connection ne doit pas être null");
        String request = "SELECT count(*) count FROM (" + sql + ") as sql";
        System.out.println(request);
        PreparedStatement stat = co.prepareStatement(request);
        try {
            stat = StatementBinder.prepareStatement(this, stat, fields, this);
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
        String tableName = FieldReflection.getTableName(this, clazz);
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

    void prepagePaginationIfAllowed(Connection co, Class<?> clazz, String sql, Field[] fields) throws Exception {
        if (isPaginable()) {
            int totalSize = getResultSize(co, clazz, sql, fields);
            System.out.println("Result size for sql is " + totalSize + " start : " + this.getPagination().getStart()
                    + " end :" + this.getPagination().getEnd());
            this.getPagination().setTotalSize(totalSize);
            this.setLimit(this.getPagination().getBeginIndex(), this.getPagination().getEndIndex());
        }
    }

    /*
     * >>Getter Setter
     */

    String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    Integer getDebut() {
        return debut;
    }

    private void setDebut(Integer debut) throws Exception {
        if (debut != null && debut < 0)
            throw new Exception("valeur debut limite invalide");
        this.debut = debut;
    }

    Integer getFin() {
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

    String getOrdre() {
        return ordre;
    }

    public void setOrdre(String column, String ordre) throws Exception {
        if (!ordre.equalsIgnoreCase("ASC") && !ordre.equalsIgnoreCase("DESC"))
            throw new Exception("Ordre invalid vous avez ecrit " + ordre + " au lieu de ASC ou DESC");
        this.ordre = "ORDER BY " + column + " " + ordre;
    }

    boolean isFilterStringstart() {
        return filterStringstart;
    }

    private void setFilterStringstart(boolean filterStringstart) {
        this.filterStringstart = filterStringstart;
    }

    boolean isFilterStringend() {
        return filterStringend;
    }

    boolean doesFilterExist() {
        return isFilterStringend() || isFilterStringstart();
    }

    private void setFilterStringend(boolean filterStringend) {
        this.filterStringend = filterStringend;
    }

    public void setFilter(boolean start, boolean end) throws Exception {
        setFilterStringstart(start);
        setFilterStringend(end);
    }

    String getOtherConditions() {
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

    String getRecherche() {
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

    List<String> getIgnoredFields() {
        return this.ignoredFields;
    }

    int getInitialValue() {
        return this.initialValue;
    }

    List<String> getFieldToResearch() {
        return this.fieldsToResearch;
    }

    List<String> getFieldToSet() {
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
