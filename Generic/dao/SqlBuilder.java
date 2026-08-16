package Generic.dao;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.List;
import java.util.StringJoiner;

/**
 * Construction des requetes SQL (INSERT/SELECT/UPDATE/DELETE) et des conditions
 * WHERE, a partir de l'etat de requete (recherche, filtres, pagination, tri, ...)
 * porte par l'entite GenericDAO passee en parametre.
 */
final class SqlBuilder {

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
        System.out.println(sql);
        return sql;
    }

    static String prepareAcondition(GenericDAO self, Field field) throws Exception {
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
        return fieldName + equal;
    }

    static String prepareResearchCondition(GenericDAO self, Field[] fields) throws Exception {
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
                    }

                    StringJoiner valueJoiner = new StringJoiner(" OR ");
                    for (String value : values) {
                        StringBuilder valueBuilder = new StringBuilder();
                        valueBuilder.append(fieldName).append(" ILIKE '");
                        if (self.isFilterStringend()) {
                            valueBuilder.append("%");
                        }
                        valueBuilder.append(value);
                        if (self.isFilterStringstart()) {
                            valueBuilder.append("%");
                        }
                        valueBuilder.append("'");
                        valueJoiner.add(valueBuilder.toString());
                    }

                    conditionBuilder.append(valueJoiner.toString()).append(" OR ");
                } else if (FieldReflection.isObject(field)) {
                    conditionBuilder.append(prepareResearchCondition(self, field.getType().getDeclaredFields()));
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
        StringBuilder conditionBuilder = new StringBuilder(" OR ");

        if (self.getRecherche() != null) {
            String[] values = self.getRecherche().split(" ");

            for (String fieldName : fields) {
                StringJoiner valueJoiner = new StringJoiner(" OR ");
                for (String value : values) {
                    StringBuilder valueBuilder = new StringBuilder();
                    valueBuilder.append(fieldName).append(" ILIKE '");
                    if (self.isFilterStringend()) {
                        valueBuilder.append("%");
                    }
                    valueBuilder.append(value);
                    if (self.isFilterStringstart()) {
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

    private static String getResearchCondition(GenericDAO self, Field[] fields) throws Exception {
        if (self.getFieldToResearch().isEmpty()) {
            String researchCondition = prepareResearchCondition(self, fields);
            return researchCondition.replace("  ", " ").replace("OR OR", "OR");
        } else {
            return prepareResearchConditionFromFieldNames(self, self.getFieldToResearch());
        }
    }

    static String prepareSelectSQL(GenericDAO self, Connection con, String columns, String tableName, Field[] fields,
            Field[] allFields) throws Exception {
        StringBuilder sqlBuilder = new StringBuilder();
        String columnString = (columns == null) ? "*" : columns;
        sqlBuilder.append("SELECT ").append(columnString).append(" FROM ").append(tableName);

        String researchCondition = getResearchCondition(self, allFields);
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
            String condition = prepareAcondition(self, fields[i]);
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
        System.out.println(sql);
        return sql;
    }

    static String prepareUpdateSQL(GenericDAO self, Class<?> clazz, Field[] fields, Field primaryKey)
            throws Exception {
        String tableName = FieldReflection.getTableName(self, clazz);
        String sql = "UPDATE " + tableName + " SET ";
        for (int i = 0; i < fields.length; i++) {
            if (!FieldReflection.isPrimaryKey(fields[i])) {
                String fieldName = FieldReflection.getFieldName(fields[i]);
                if (FieldReflection.isObject(fields[i])) {
                    fieldName = FieldReflection.getFieldNameIfObject(self, fields[i], fieldName, self);
                }
                sql = sql + fieldName + " =  ?";
                if (i < fields.length - 1) {
                    sql = sql + ", ";
                }
            }
        }
        sql = sql + " WHERE " + FieldReflection.getFieldName(primaryKey) + " = ?";

        System.out.println(sql);
        return sql;
    }

    static String prepareDeleteSQL(GenericDAO self, Class<?> clazz, Field primaryKey) {
        String tableName = FieldReflection.getTableName(self, clazz);
        String sql = "DELETE FROM " + tableName + " WHERE " + FieldReflection.getFieldName(primaryKey) + " = ?";
        System.out.println(sql);
        return sql;
    }
}
