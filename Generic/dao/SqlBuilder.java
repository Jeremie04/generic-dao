package Generic.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Logger;

/**
 * Construction des requetes SQL (INSERT/SELECT/UPDATE/DELETE) et des conditions
 * WHERE, a partir de l'etat de requete (recherche, filtres, pagination, tri, ...)
 * porte par l'entite GenericDAO passee en parametre.
 */
final class SqlBuilder {

    private static final Logger LOG = Logger.getLogger(SqlBuilder.class.getName());

    private SqlBuilder() {
    }

    static String prepareSaveSQL(GenericDAO self, Class<?> clazz, Field[] fields) throws Exception {
        String tableName = FieldReflection.getTableName(self, clazz);
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner values = new StringJoiner(", ");

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = FieldReflection.isObject(field)
                    ? FieldReflection.getFieldNameIfObject(self, field, FieldReflection.getFieldName(field), self)
                    : FieldReflection.getFieldName(field);
            columns.add(fieldName);
            values.add("?");
        }

        String sql = "INSERT INTO " + tableName + " (" + columns.toString() + ") VALUES (" + values.toString() + ")";
        Field primaryKey = FieldReflection.getPrimaryKey(self, clazz);
        if (primaryKey == null)
            throw new Exception("Aucune clé primaire trouvé");
        sql = sql + " RETURNING " + FieldReflection.getFieldName(primaryKey);
        LOG.fine(sql);
        return sql;
    }

    static String prepareAcondition(GenericDAO self, String tableName, Field field) throws Exception {
        // Un champ herite d'une classe parente designe toujours une colonne de la meme table :
        // pas de suffixe de nom de classe ici (contrairement a prepareResearchCondition, qui
        // gere le cas distinct d'un objet lie dans une autre table).
        String fieldName = FieldReflection.getFieldName(field);
        String equal = " = ? ";
        if (FieldReflection.isObject(field)) {
            fieldName = FieldReflection.getFieldNameIfObject(self, field, fieldName, self);
        }
        if (self.doesFilterExist()
                && (field.getType().equals(String.class) || field.getType().equals(Character.class))) {
            equal = " LIKE ?";
        }
        // Qualifie toujours par la table : sans ça, une condition sur un champ de l'entite
        // (ex. "nom = ?") devient ambigue des qu'un JOIN automatique (setFetchRelations) est
        // actif et que la table liee a une colonne du meme nom.
        return tableName + "." + fieldName + equal;
    }

    // setRecherche(...) est la fonction la plus exposee a de l'input utilisateur direct (une
    // barre de recherche, typiquement) : ses valeurs ne doivent jamais etre concatenees dans le
    // texte SQL (injection possible, et une simple apostrophe dans le terme cherche cassait deja
    // la requete sans intention malveillante). Les deux methodes ci-dessous emettent donc des
    // "ILIKE ?" et accumulent les valeurs reelles (sans le formatage %...%, applique a la liaison
    // par StatementBinder comme pour n'importe quel autre champ) dans self.getRechercheBoundValues(),
    // dans le meme ordre que les "?" apparaissent dans le SQL genere. Vide puis repeuple cette
    // liste a chaque appel : elle est relue par GenericDAO juste apres avoir construit le SQL.
    static String prepareResearchCondition(GenericDAO self, String tableName, Field[] fields) throws Exception {
        self.getRechercheBoundValues().clear();
        return buildResearchCondition(self, tableName, fields);
    }

    private static String buildResearchCondition(GenericDAO self, String tableName, Field[] fields) throws Exception {
        StringBuilder conditionBuilder = new StringBuilder(" OR ");

        if (self.getRecherche() != null) {
            String[] values = self.getRecherche().split(" ");
            for (Field field : fields) {
                if (field.getType() == String.class && !FieldReflection.isPrimaryKey(field)) {
                    String fieldName = FieldReflection.getFieldName(field);
                    if (field.getDeclaringClass() != self.getClass()
                            && !fieldName.toLowerCase()
                                    .endsWith("_" + field.getDeclaringClass().getSimpleName().toLowerCase())) {
                        fieldName = fieldName + "_" + field.getDeclaringClass().getSimpleName();
                    } else {
                        // Champ propre a l'entite (ou herite, deja gere ci-dessus) : qualifie par
                        // la table pour rester valide si un JOIN automatique (setFetchRelations)
                        // expose une colonne du meme nom sur la table liee.
                        fieldName = tableName + "." + fieldName;
                    }

                    StringJoiner valueJoiner = new StringJoiner(" OR ");
                    for (String value : values) {
                        valueJoiner.add(fieldName + " ILIKE ?");
                        self.getRechercheBoundValues().add(value);
                    }

                    conditionBuilder.append(valueJoiner.toString()).append(" OR ");
                } else if (FieldReflection.isObject(field)) {
                    conditionBuilder
                            .append(buildResearchCondition(self, tableName, field.getType().getDeclaredFields()));
                }
            }
        }

        String condition = conditionBuilder.toString();
        if (condition.endsWith(" OR ")) {
            condition = condition.substring(0, condition.length() - 4); // Remove the last " OR "
        }

        return condition;
    }

    static String prepareResearchConditionFromFieldNames(GenericDAO self, List<String> fields) {
        self.getRechercheBoundValues().clear();
        StringBuilder conditionBuilder = new StringBuilder(" OR ");

        if (self.getRecherche() != null) {
            String[] values = self.getRecherche().split(" ");

            for (String fieldName : fields) {
                StringJoiner valueJoiner = new StringJoiner(" OR ");
                for (String value : values) {
                    valueJoiner.add(fieldName + " ILIKE ?");
                    self.getRechercheBoundValues().add(value);
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

    private static String getResearchCondition(GenericDAO self, String tableName, Field[] fields) throws Exception {
        if (self.getFieldToResearch().isEmpty()) {
            String researchCondition = prepareResearchCondition(self, tableName, fields);
            return researchCondition.replace("  ", " ").replace("OR OR", "OR");
        } else {
            return prepareResearchConditionFromFieldNames(self, self.getFieldToResearch());
        }
    }

    static String prepareSelectSQL(GenericDAO self, Connection con, String columns, String tableName, Field[] fields,
            Field[] allFields) throws Exception {
        StringBuilder sqlBuilder = new StringBuilder();

        // JOIN automatique (setFetchRelations) : opt-in par relation, pour ne jamais imposer
        // le cout d'un JOIN a un select() qui n'en a pas demande.
        StringJoiner joinClauses = new StringJoiner(" ");
        StringJoiner extraColumns = new StringJoiner(", ");
        if (!self.getFetchRelations().isEmpty()) {
            for (Field field : allFields) {
                if (FieldReflection.isObject(field) && self.getFetchRelations().contains(field.getName())) {
                    appendRelationJoin(self, tableName, field, joinClauses, extraColumns);
                }
            }
        }

        String columnString;
        if (columns != null) {
            columnString = columns; // selection d'attributs explicite : le JOIN automatique ne s'y applique pas
        } else if (extraColumns.length() > 0) {
            columnString = tableName + ".*, " + extraColumns;
        } else {
            columnString = "*";
        }
        sqlBuilder.append("SELECT ").append(columnString).append(" FROM ").append(tableName);
        if (joinClauses.length() > 0) {
            sqlBuilder.append(" ").append(joinClauses);
        }

        String researchCondition = getResearchCondition(self, tableName, allFields);
        // researchCondition commence par " OR " pour s'enchainer apres les conditions exactes
        // (fields) ou apres otherConditions. Sans rien avant elle, ce " OR " en tete produirait
        // un "WHERE OR ..." invalide : on le retire dans ce cas precis.
        if (fields.length == 0 && self.getOtherConditions().isEmpty()) {
            researchCondition = researchCondition.replaceFirst("^\\s*OR\\s+", " ");
        }
        if (fields.length > 0 || (!researchCondition.isEmpty()
                && !researchCondition.equals(" ")
                && !researchCondition.toLowerCase().contains("WHERE"))) {
            sqlBuilder.append(" WHERE ");
        }

        if (sqlBuilder.indexOf("WHERE") == -1 && !self.getOtherConditions().isEmpty()
                && !self.getOtherConditions().toLowerCase().contains("where")) {
            sqlBuilder.append(" WHERE ");
        }
        sqlBuilder.append(self.getOtherConditions());

        if (!self.getOtherConditions().isEmpty() && fields.length > 0) {
            sqlBuilder.append(" AND ");
        }

        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sqlBuilder.append(" AND ");
            }
            String condition = prepareAcondition(self, tableName, fields[i]);
            sqlBuilder.append(condition);
        }

        sqlBuilder.append(researchCondition);

        self.prepagePaginationIfAllowed(con, self.getClass(), sqlBuilder.toString(), fields);

        sqlBuilder.append(" ").append(self.getOrdre());

        if (self.getDebut() != null && self.getFin() != null) {
            int fin = self.getFin() - self.getDebut();
            sqlBuilder.append(" OFFSET ").append(self.getDebut()).append(" LIMIT ").append(fin);
        }

        String sql = sqlBuilder.toString().replace("  ", " ").replace(" WHERE WHERE ", " WHERE ");
        LOG.fine(sql);
        return sql;
    }

    /**
     * Construit le JOIN et les colonnes aliasees pour une relation demandee via
     * setFetchRelations(...) : equivalent au JOIN qu'on ecrirait a la main (voir README,
     * section "Relations"), derive par reflexion. Ne gere qu'un seul niveau de relation ; les
     * champs objet du type lie ne sont pas suivis recursivement.
     */
    private static void appendRelationJoin(GenericDAO self, String tableName, Field relationField,
            StringJoiner joinClauses, StringJoiner extraColumns) throws Exception {
        Class<?> relatedClass = relationField.getType();
        String tableOverride = self.getRelationTableNames().get(relationField.getName());
        String relatedTable = (tableOverride != null) ? tableOverride : FieldReflection.getSimpleTableName(relatedClass);
        Field relatedPrimaryKey = FieldReflection.getPrimaryKey(self, relatedClass);
        if (relatedPrimaryKey == null) {
            throw new Exception("setFetchRelations(\"" + relationField.getName()
                    + "\") : aucune cle primaire (@AField(isId = true)) trouvee sur "
                    + relatedClass.getSimpleName());
        }
        String fkColumn = relatedPrimaryKey.getName() + "_" + FieldReflection.getFieldName(relationField);
        // Base sur la colonne resolue (respecte @AField(column = ...) sur le champ-relation
        // lui-meme), pas sur le nom du champ Java brut : doit rester coherent avec le suffixe
        // utilise a la lecture par ResultSetMapper.setRowFromResultSet.
        String aliasSuffix = GenericDAO.toSnakeCase(FieldReflection.getFieldName(relationField));
        // Alias de table propre a cette relation (jamais le nom de table brut) : evite un
        // "table specified more than once" si deux relations pointent vers le meme type, ou
        // si la relation est auto-referente (ex. Employe.manager de type Employe).
        String tableAlias = aliasSuffix;

        joinClauses.add("JOIN " + relatedTable + " AS " + tableAlias + " ON " + tableName + "." + fkColumn + " = "
                + tableAlias + "." + FieldReflection.getFieldName(relatedPrimaryKey));

        for (Field relatedField : FieldReflection.getFieldsNotIgnored(self, relatedClass)) {
            if (FieldReflection.isPrimaryKey(relatedField) || FieldReflection.isObject(relatedField)) {
                // La cle primaire est deja couverte par la colonne FK (deja presente dans la
                // table de base) ; une relation imbriquee du type lie n'est pas suivie (un seul
                // niveau de JOIN automatique).
                continue;
            }
            String col = FieldReflection.getFieldName(relatedField);
            extraColumns.add(tableAlias + "." + col + " AS " + col + "_" + aliasSuffix);
        }
    }

    static String prepareUpdateSQL(GenericDAO self, Class<?> clazz, Field[] fields, Field primaryKey)
            throws Exception {
        String tableName = FieldReflection.getTableName(self, clazz);
        // StringJoiner ne place une virgule qu'entre deux champs reellement ajoutes : contrairement
        // a une virgule basee sur l'index dans le tableau, ça reste correct meme si le champ ignore
        // (la cle primaire) se trouve en derniere position du tableau.
        StringJoiner setClause = new StringJoiner(", ");
        for (Field field : fields) {
            if (!FieldReflection.isPrimaryKey(field)) {
                String fieldName = FieldReflection.getFieldName(field);
                if (FieldReflection.isObject(field)) {
                    fieldName = FieldReflection.getFieldNameIfObject(self, field, fieldName, self);
                }
                setClause.add(fieldName + " = ?");
            }
        }
        String sql = "UPDATE " + tableName + " SET " + setClause
                + " WHERE " + FieldReflection.getFieldName(primaryKey) + " = ?";

        LOG.fine(sql);
        return sql;
    }

    static String prepareDeleteSQL(GenericDAO self, Class<?> clazz, Field primaryKey) {
        String tableName = FieldReflection.getTableName(self, clazz);
        String sql = "DELETE FROM " + tableName + " WHERE " + FieldReflection.getFieldName(primaryKey) + " = ?";
        LOG.fine(sql);
        return sql;
    }
}
