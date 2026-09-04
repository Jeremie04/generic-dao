package Generic.dao;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import Generic.annotation.AField;

/**
 * Charge les relations "un-a-plusieurs" (champ {@code List<Item>} ou {@code Item[]}) demandees
 * via {@code setFetchRelations(...)}. Contrairement a une relation "un" (voir
 * {@code SqlBuilder.appendRelationJoin}), un JOIN classique ferait exploser chaque parent en
 * autant de lignes que d'enfants, ce que {@code ResultSetMapper} (mapping "une ligne = un objet")
 * ne gere pas. La strategie ici est donc une requete batch separee par relation demandee :
 * {@code SELECT * FROM enfant WHERE fk IN (id1, id2, ...)}, execute une seule fois pour tous les
 * parents deja charges, puis les resultats sont regroupes en memoire par cle etrangere.
 * <p>
 * La cle etrangere est portee par la table enfant (sens inverse de la relation "un" habituelle) :
 * le champ enfant qui la porte doit etre nomme explicitement via {@code @AField(mappedBy = "...")}
 * sur le champ collection du parent, la reflexion seule ne pouvant pas deviner ce champ sans
 * ambiguite (plusieurs champs du meme type, relation auto-referente, ...).
 */
final class CollectionRelationLoader {

    private static final Logger LOG = Logger.getLogger(CollectionRelationLoader.class.getName());

    private CollectionRelationLoader() {
    }

    static <T> void loadCollectionRelations(GenericDAO self, Connection con, Class<T> parentClass, List<T> parents)
            throws Exception {
        if (parents.isEmpty() || self.getFetchRelations().isEmpty()) {
            return;
        }

        Field[] collectionFields = FieldReflection.getCollectionRelationFields(parentClass);
        if (collectionFields.length == 0) {
            return;
        }

        Field parentPrimaryKey = FieldReflection.getPrimaryKey(self, parentClass);
        if (parentPrimaryKey == null) {
            return; // pas de cle primaire : impossible de rattacher les enfants au bon parent
        }
        parentPrimaryKey.setAccessible(true);

        for (Field collectionField : collectionFields) {
            if (self.getFetchRelations().contains(collectionField.getName())) {
                loadOneCollectionRelation(self, con, parents, parentPrimaryKey, collectionField);
            }
        }
    }

    private static <T> void loadOneCollectionRelation(GenericDAO self, Connection con, List<T> parents,
            Field parentPrimaryKey, Field collectionField) throws Exception {
        AField annotation = collectionField.getAnnotation(AField.class);
        String mappedBy = (annotation != null) ? annotation.mappedBy() : "";
        if (mappedBy.isEmpty()) {
            throw new Exception("setFetchRelations(\"" + collectionField.getName()
                    + "\") : @AField(mappedBy = \"...\") est requis sur ce champ collection, pour nommer le "
                    + "champ de l'entite liee qui reference celle-ci en retour.");
        }

        Class<?> itemClass = FieldReflection.getCollectionElementType(collectionField);
        Field backReferenceField = findFieldInHierarchy(itemClass, mappedBy);
        if (backReferenceField == null) {
            throw new Exception("setFetchRelations(\"" + collectionField.getName() + "\") : mappedBy = \""
                    + mappedBy + "\" introuvable sur " + itemClass.getSimpleName());
        }

        // Meme convention de nommage que la relation "un" (SqlBuilder.appendRelationJoin) : la
        // cle etrangere est <cle primaire du parent>_<champ de relation cote enfant>, deja
        // utilisee telle quelle par le save()/select() habituel de l'entite enfant.
        String fkColumn = parentPrimaryKey.getName() + "_" + FieldReflection.getFieldName(backReferenceField);
        String tableOverride = self.getRelationTableNames().get(collectionField.getName());
        String childTable = (tableOverride != null) ? tableOverride : FieldReflection.getSimpleTableName(itemClass);

        Map<String, List<Object>> childrenByParentKey = new LinkedHashMap<>();
        List<Object> parentKeys = new ArrayList<>();
        for (T parent : parents) {
            Object key = parentPrimaryKey.get(parent);
            if (key != null) {
                parentKeys.add(key);
                childrenByParentKey.putIfAbsent(normalizeKey(key), new ArrayList<>());
            }
        }
        if (parentKeys.isEmpty()) {
            return;
        }

        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < parentKeys.size(); i++) {
            placeholders.append(i > 0 ? ", ?" : "?");
        }
        String sql = "SELECT * FROM " + childTable + " WHERE " + fkColumn + " IN (" + placeholders + ")";
        LOG.fine(sql);

        Field[] itemFields = FieldReflection.getFieldsNotIgnored(self, itemClass);
        try (PreparedStatement statement = con.prepareStatement(sql)) {
            for (int i = 0; i < parentKeys.size(); i++) {
                statement.setObject(i + 1, parentKeys.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<String, Integer> columnIndex = ResultSetMapper.buildColumnIndex(resultSet);
                Integer fkColumnIdx = columnIndex.get(fkColumn.toLowerCase());
                while (resultSet.next()) {
                    Object fkValue = (fkColumnIdx != null) ? resultSet.getObject(fkColumnIdx) : null;
                    List<Field> fieldsList = new ArrayList<>(Arrays.asList(itemFields));
                    Iterator<Field> fieldIterator = fieldsList.iterator();
                    Object child = ResultSetMapper.setRowFromResultSet(self, itemClass, resultSet, columnIndex,
                            fieldIterator, null);
                    List<Object> bucket = childrenByParentKey.get(normalizeKey(fkValue));
                    if (bucket != null) {
                        bucket.add(child);
                    }
                }
            }
        }

        assignToParents(parents, parentPrimaryKey, collectionField, itemClass, childrenByParentKey);
    }

    private static <T> void assignToParents(List<T> parents, Field parentPrimaryKey, Field collectionField,
            Class<?> itemClass, Map<String, List<Object>> childrenByParentKey) throws Exception {
        collectionField.setAccessible(true);
        for (T parent : parents) {
            Object key = parentPrimaryKey.get(parent);
            List<Object> children = (key != null) ? childrenByParentKey.get(normalizeKey(key)) : null;
            if (children == null) {
                children = new ArrayList<>();
            }
            if (collectionField.getType().isArray()) {
                Object array = Array.newInstance(itemClass, children.size());
                for (int i = 0; i < children.size(); i++) {
                    Array.set(array, i, children.get(i));
                }
                collectionField.set(parent, array);
            } else {
                collectionField.set(parent, new ArrayList<>(children));
            }
        }
    }

    // Les valeurs lues via JDBC (type dependant du driver/de la colonne, ex. Integer ou Long)
    // et via reflexion sur le champ Java cote parent (ex. int) peuvent differer de type pour une
    // meme valeur logique : normaliser en String pour le regroupement evite de dependre d'une
    // egalite exacte entre types numeriques potentiellement differents.
    private static String normalizeKey(Object value) {
        return (value != null) ? value.toString() : null;
    }

    private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != GenericDAO.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
