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
    /**
     * Initialise tous les champs primitifs numeriques (int/long/float/double) de l'entite
     * a la valeur sentinelle 2066, afin que {@code 0} reste distinguable d'un champ "non
     * renseigne" (voir {@code getFieldsNotNull} : un champ egal a cette sentinelle est traite
     * comme absent lors d'un save()/select()/update()). A appeler soi-meme juste apres la
     * construction de l'entite si vous devez pouvoir enregistrer explicitement la valeur 0.
     */
    public void init() throws Exception {
        System.out.println("Initializing " + this.getClass());
        Class c = this.getClass();
        Field[] f = c.getDeclaredFields();
        for (int i = 0; i < f.length; i++) {
            if (f[i].getType().isPrimitive() && !java.lang.reflect.Modifier.isStatic(f[i].getModifiers())) {
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

    /**
     * Convertit un identifiant snake_case (typiquement un nom de colonne SQL) en camelCase
     * (typiquement un nom d'attribut Java). Exemple : {@code "date_creation"} -> {@code "dateCreation"}.
     */
    public static String toCamelCase(String input) {
        String[] words = input.split("_");
        StringBuilder result = new StringBuilder(words[0]); // Garde le premier mot tel quel

        for (int i = 1; i < words.length; i++) {
            result.append(Character.toUpperCase(words[i].charAt(0)))
                    .append(words[i].substring(1));
        }

        return result.toString();
    }

    /**
     * Convertit un identifiant camelCase (typiquement un nom d'attribut Java) en snake_case
     * (typiquement un nom de colonne SQL). Exemple : {@code "dateCreation"} -> {@code "date_creation"}.
     */
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
    /**
     * Execute une instruction SQL arbitraire (DDL comme CREATE/DROP TABLE, ou DML comme
     * INSERT/UPDATE/DELETE) qui ne renvoie pas de resultat exploitable via une entite.
     * Utile par exemple pour preparer/nettoyer un schema dans des tests.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param sql     l'instruction SQL a executer telle quelle
     * @param commit  {@code true} pour valider la transaction apres execution (et faire un
     *                rollback automatique en cas d'erreur)
     * @param isClose {@code true} pour fermer la connexion apres l'appel (toujours fermee si
     *                {@code con} etait {@code null})
     */
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

    /**
     * Insere l'entite courante (CREATE). Seuls les champs non nuls / non a leur valeur par
     * defaut sont inclus dans l'INSERT (voir {@link #init()}) ; le champ marque
     * {@code @AField(isId = true)} est exclu et sa valeur generee par la base est renvoyee.
     * Un champ objet (relation "a un") est ecrit comme la cle primaire de l'objet lie — voir
     * la section "Relations" du README.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param isClose {@code true} pour fermer la connexion apres l'appel
     * @param commit  {@code true} pour valider la transaction (rollback automatique sinon en cas d'erreur)
     * @return la valeur de la cle primaire generee par la base (ex : le SERIAL/id)
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

    /**
     * Insere plusieurs entites du meme type en une seule requete preparee (insertion en lot).
     * Les colonnes inserees sont determinees a partir du premier element du tableau : tous
     * les objets doivent donc avoir les memes champs renseignes.
     * A la difference de {@link #save(Connection, boolean, boolean)}, ne renvoie pas les
     * cles primaires generees.
     * Attention : la table (et toute config {@code set...}, ex. {@link #setTableName(String)})
     * utilisee est celle de l'entite sur laquelle {@code save} est appele, pas celle des objets
     * du tableau — appelez cette methode sur une instance configuree, pas sur un {@code new
     * MyEntity()} non configure.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param objects les entites a inserer (non vide)
     * @param isClose {@code true} pour fermer la connexion apres l'appel
     * @param commit  {@code true} pour valider la transaction (rollback automatique sinon en cas d'erreur)
     */
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

    /**
     * Construit le fragment de condition ({@code OR ... ILIKE ...}) correspondant a
     * {@link #setRecherche(String, String...)} pour les champs String donnes. Bloc interne
     * utilise par {@code select()} ; la plupart des utilisateurs n'ont pas besoin de l'appeler
     * directement, {@link #setRecherche(String, String...)} suffit.
     */
    public String prepareResearchCondition(Field[] fields) throws Exception {
        return SqlBuilder.prepareResearchCondition(this, fields);
    }

    /**
     * Variante de {@link #prepareResearchCondition(Field[])} qui prend directement des noms
     * de colonnes SQL plutot que des {@link Field}. Utilise en interne lorsque
     * {@link #setRecherche(String, String...)} est appele avec des colonnes explicites.
     */
    public String prepareResearchConditionFromFieldNames(List<String> fields) {
        return SqlBuilder.prepareResearchConditionFromFieldNames(this, fields);
    }

    /**
     * Construit la requete SELECT complete (WHERE, recherche texte, tri, pagination) a partir
     * de l'etat courant de l'entite. Bloc interne utilise par {@code select()} ; exposee au cas
     * ou vous auriez besoin d'inspecter ou de reutiliser le SQL genere sans l'executer.
     */
    public String prepareSelectSQL(Connection con, String columns, String tableName, Field[] fields,
            Field[] allFields) throws Exception {
        return SqlBuilder.prepareSelectSQL(this, con, columns, tableName, fields, allFields);
    }

    /**
     * Execute la requete SQL fournie telle quelle et mappe chaque ligne du resultat sur une
     * nouvelle instance de l'entite. Utile pour les requetes personnalisees, notamment un
     * {@code JOIN} qui charge un objet lie en entier (voir la section "Relations" du README :
     * aliaser les colonnes en {@code <colonne>_<nom du champ objet>}, ex. {@code nom_categorie}).
     * Les eventuels champs deja non nuls sur l'entite courante sont lies aux {@code ?} du sql
     * dans leur ordre de declaration : passez une entite "vierge" si votre requete n'a pas de
     * parametre.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param isClose {@code true} pour fermer la connexion apres l'appel
     * @param sql     la requete SELECT a executer
     * @return les entites correspondant aux lignes du resultat (tableau vide si aucune)
     */
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

    /**
     * Selectionne les entites correspondant "par l'exemple" : les champs non nuls / non a leur
     * valeur par defaut de l'entite courante deviennent des conditions {@code = ?} (ou
     * {@code LIKE ?} si {@link #setFilter(boolean, boolean)} est active), combinees a
     * {@link #setRecherche(String, String...)}, {@link #setOtherConditions(String)},
     * {@link #setOrdre(String, String)} et {@link #setLimit(Integer, Integer)}/pagination si
     * configures. La requete est generee automatiquement (pas de {@code JOIN}) : pour une
     * entite avec un champ objet, seule la cle primaire de l'objet lie est remplie — voir
     * {@link #select(Connection, boolean, String)} pour charger la relation en entier.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param isClose {@code true} pour fermer la connexion apres l'appel
     * @return les entites trouvees (tableau vide si aucune)
     */
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

    /**
     * Variante de {@link #select(Connection, boolean, String)} qui execute le SQL via un
     * {@link Statement} simple plutot qu'un {@link PreparedStatement} : aucune liaison de
     * parametre {@code ?} n'est effectuee, le {@code sql} doit donc etre complet et deja
     * valide (attention aux injections si des valeurs y sont concatenees). A n'utiliser que si
     * vous n'avez pas besoin de parametres lies.
     */
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

    /**
     * Comme {@link #select(Connection, boolean)}, mais en ne selectionnant/mappant que les
     * colonnes listees dans {@code attributs} au lieu de tous les champs de l'entite.
     * Un attribut peut designer un champ propre (ex. {@code "nom"}) ou, avec le format
     * {@code "colonne_NomDeClasse"}, un champ d'un objet lie.
     *
     * @param con       connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param attributs les noms de colonnes/attributs a recuperer
     * @param isClose   {@code true} pour fermer la connexion apres l'appel
     * @return les entites trouvees, avec uniquement les attributs demandes renseignes
     */
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
    /**
     * Comme {@link #select(Connection, boolean)} mais ne renvoie que le premier resultat
     * (equivalent a {@code LIMIT 1}). La limite posee par cet appel est retiree ensuite : on
     * peut donc reutiliser la meme entite pour un autre appel {@code select()} sans la limite.
     *
     * @return la premiere entite trouvee, ou {@code null} si aucune ne correspond
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

    /**
     * Recupere l'entite dont la cle primaire (le champ annote {@code @AField(isId = true)})
     * vaut {@code id}.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param id      la valeur de la cle primaire recherchee
     * @param isClose {@code true} pour fermer la connexion apres l'appel
     * @return l'entite trouvee
     * @throws NotFoundException si aucune ligne ne correspond a cet id
     * @throws Exception         si l'entite ne declare aucun champ {@code @AField(isId = true)}
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

    /**
     * Variante de {@link #findById(Connection, Object, boolean)} qui utilise la valeur de cle
     * primaire deja affectee sur l'entite courante plutot qu'un parametre {@code id}.
     *
     * @throws NotFoundException si aucune ligne ne correspond a cet id
     */
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

    /**
     * Met a jour (UPDATE) la ligne dont la cle primaire vaut {@code idValue}, avec les valeurs
     * actuellement non nulles de l'entite courante (la cle primaire elle-meme n'est jamais
     * modifiee). Typiquement : recuperer l'entite via {@code findById}, modifier ses setters,
     * puis appeler {@code update} avec le meme id.
     *
     * @param con      connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param idValue  la valeur de cle primaire de la ligne a mettre a jour
     * @param isClose  {@code true} pour fermer la connexion apres l'appel
     * @param commit   {@code true} pour valider la transaction
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

    /**
     * Supprime (DELETE) la ligne dont la cle primaire vaut {@code idValue}.
     *
     * @param con     connexion a utiliser, ou {@code null} pour en ouvrir une nouvelle
     * @param idValue la valeur de cle primaire de la ligne a supprimer
     * @param isClose {@code true} pour fermer la connexion apres l'appel
     * @param commit  {@code true} pour valider la transaction
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
    /**
     * Ouvre une nouvelle connexion JDBC en utilisant les parametres de {@link Connexion}
     * (host/port/base/identifiants). Appele automatiquement par les methodes CRUD lorsqu'on
     * leur passe {@code con = null}.
     */
    public Connection getConnection() throws Exception {
        Connexion c = new Connexion();
        return c.getConnect();
    }

    /*
     * Requetes utilitaires (taille, pagination)
     */

    /**
     * Compte le nombre de lignes que renverrait la requete {@code sql} donnee (utilise via
     * {@code COUNT(*) FROM (sql) as sql}). Sert de base au calcul du nombre total de resultats
     * pour la pagination ; la plupart des utilisateurs passeront plutot par
     * {@link #setPaginable(boolean)} + {@link #setPagination(Pagination)}.
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

    /**
     * Compte le nombre total de lignes dans la table de l'entite {@code clazz} (sans aucun
     * filtre).
     */
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

    /**
     * Remplace le nom de table resolu pour cette instance (prend le pas sur
     * {@code @AClass(tableName = ...)}). Utile par exemple pour pointer temporairement sur une
     * vue SQL qui fait deja le JOIN necessaire pour charger un objet lie en entier — voir la
     * section "Relations" du README. Reservez cette technique a la lecture : une vue basee sur
     * un JOIN n'est generalement pas modifiable via save/update/delete.
     */
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

    /**
     * Ajoute un {@code OFFSET debut LIMIT (fin - debut)} au prochain {@code select()}.
     *
     * @param debut indice de depart (inclus), doit etre positif ou nul
     * @param fin   indice de fin (exclus), doit etre superieur ou egal a {@code debut}
     */
    public void setLimit(Integer debut, Integer fin) throws Exception {
        if (debut > fin)
            throw new Exception("Debut " + debut + " plus grand que Fin " + fin);
        this.setDebut(debut);
        this.setFin(fin);
    }

    String getOrdre() {
        return ordre;
    }

    /**
     * Ajoute un tri {@code ORDER BY column ordre} au prochain {@code select()}.
     *
     * @param column nom de la colonne SQL de tri
     * @param ordre  {@code "ASC"} ou {@code "DESC"} (insensible a la casse)
     */
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

    /**
     * Active les jokers {@code %} sur les comparaisons de chaines (conditions exactes en
     * {@code LIKE} et {@link #setRecherche(String, String...)} en {@code ILIKE}).
     *
     * @param start ajoute un {@code %} en fin de motif ({@code "val%"}) : matche les valeurs
     *              qui commencent par le terme recherche
     * @param end   ajoute un {@code %} en debut de motif ({@code "%val"}) : matche les valeurs
     *              qui se terminent par le terme recherche
     */
    public void setFilter(boolean start, boolean end) throws Exception {
        setFilterStringstart(start);
        setFilterStringend(end);
    }

    String getOtherConditions() {
        return otherConditions;
    }

    /**
     * Ajoute une condition SQL libre (fragment de clause WHERE, sans le mot-cle {@code WHERE})
     * au prochain {@code select()}, combinee en {@code AND} avec les autres conditions.
     * Aucune verification n'est faite sur ce texte : ne jamais y concatener une valeur
     * provenant de l'utilisateur final sans l'echapper (risque d'injection SQL).
     */
    public void setOtherConditions(String otherConditions) {
        this.otherConditions = otherConditions;
    }

    private boolean isPaginable() {
        return paginable;
    }

    /**
     * Active la pagination pour le prochain {@code select()} : le nombre total de resultats
     * est alors calcule automatiquement et {@link #setPagination(Pagination)} doit avoir ete
     * appele au prealable.
     */
    public void setPaginable(boolean paginable) {
        this.paginable = paginable;
    }

    /** Renvoie l'objet {@link Pagination} courant (rempli avec le total apres un select paginable). */
    public Pagination getPagination() {
        return pagination;
    }

    /**
     * Definit la page a recuperer (voir {@link Pagination#fromPageNumber(int, int)} /
     * {@link Pagination#fromBeginEnd(int, int)}). A utiliser avec {@link #setPaginable(boolean)}.
     */
    public void setPagination(Pagination pagination) {
        this.pagination = pagination;
    }

    String getRecherche() {
        return recherche;
    }

    /**
     * Active une recherche texte (combinee en {@code OR ... ILIKE ...}) sur les colonnes
     * String de l'entite pour le prochain {@code select()}. Le texte est d'abord decoupe sur
     * les espaces, chaque mot etant cherche independamment.
     *
     * @param recherche le texte a rechercher
     * @param colums    optionnel : noms de colonnes SQL explicites sur lesquelles restreindre
     *                  la recherche (par defaut, toutes les colonnes String de l'entite)
     */
    public void setRecherche(String recherche, String... colums) {
        this.recherche = recherche;
        for (String field : colums) {
            fieldsToResearch.add(field);
        }
    }

    /**
     * Exclut temporairement des champs de toutes les operations CRUD suivantes (save/select/
     * update/delete), en plus de ceux deja marques {@code @AField(ignored = true)}. Chaque
     * entree doit suivre le format {@code "<colonne>_<nomDeLaClasse commencant par minuscule>"},
     * ex. {@code "nom_materiel"} pour ignorer la colonne {@code nom} de l'entite {@code Materiel}.
     */
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

    /**
     * Definit manuellement le champ considere comme cle primaire. Normalement inutile : la
     * cle primaire est deja resolue automatiquement a partir du champ annote
     * {@code @AField(isId = true)} et mise en cache ici a la premiere resolution.
     *
     * @throws Exception si {@code primaryKey} est {@code null}
     */
    public void setPrimaryKey(Field primaryKey) throws Exception {
        if (primaryKey == null)
            throw new Exception("Primary key Null");
        this.primaryKey = primaryKey;
    }

    /**
     * Restreint les champs que {@code select()} va effectivement renseigner (utile pour ne
     * charger qu'une partie d'une entite avec des objets imbriques profonds). Chaque entree
     * est un chemin pointe {@code "champ.sousChamp"} (ex. {@code "materiel.categorie"}) ; les
     * chemins intermediaires sont deduits automatiquement, par exemple :
     * [materiel.categorie.etat.nom, materiel.categorie.etat.code]
     * => materiel.categorie, categorie.etat, etat.nom, etat.code
     * Voir aussi {@link #setFieldsToSetSimplified(String)} pour une syntaxe plus compacte.
     */
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

    /**
     * Equivalent de {@link #setFieldsToSet(String...)} avec une syntaxe imbriquee plus lisible
     * quand il y a beaucoup d'attributs, ex :
     * {@code "materiel(id, categorie(id, etat(nom, code)), designation)"}.
     */
    public void setFieldsToSetSimplified(String inputs) {
        this.fieldsToSet = ParserAttributs.parser(inputs);
    }
}
