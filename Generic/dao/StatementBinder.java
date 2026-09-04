package Generic.dao;

import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.util.List;

/**
 * Liaison des valeurs Java dans les parametres "?" d'un PreparedStatement
 * (INSERT/UPDATE/DELETE/SELECT).
 */
final class StatementBinder {

    private StatementBinder() {
    }

    static PreparedStatement setValueToField(GenericDAO self, PreparedStatement stat, Object value, int index)
            throws Exception {
        if (value instanceof String) {
            String val = (String) value;
            if (self.getRecherche() != null) {
                if (self.isFilterStringend()) {
                    val = "%" + val;
                }
                if (self.isFilterStringstart()) {
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

    static PreparedStatement prepareStatement(GenericDAO self, PreparedStatement stat, Field[] fields, Object obj)
            throws Exception {
        int index = 1;
        for (Field field : fields) {
            field.setAccessible(true);
            if (FieldReflection.isObject(field)) { // si objet, représenter la valeur de son primary key
                Object fieldObjet = field.get(obj);
                Field fieldPrimaryKey = FieldReflection.getPrimaryKey(self, fieldObjet.getClass());
                fieldPrimaryKey.setAccessible(true);
                Object fieldPrimaryKeyValue = fieldPrimaryKey.get(fieldObjet);
                stat = setValueToField(self, stat, fieldPrimaryKeyValue, index);
                index++;
            } else {
                Object value = field.get(obj);
                stat = setValueToField(self, stat, value, index);
                index++;
            }
        }
        return stat;
    }

    static PreparedStatement prepareUpdateStatement(GenericDAO self, PreparedStatement stat, Field[] fields,
            Object idValue, Field primaryKey) throws Exception {
        return prepareUpdateStatement(self, stat, fields, idValue, primaryKey, self);
    }

    // Variante utilisee par update(Connection, T[], ...) (mise a jour en lot) : lit les valeurs
    // de "obj" (chaque element du tableau) plutot que de "self" (l'instance sur laquelle update()
    // a ete appele), qui ne sert plus alors qu'a resoudre la config de requete (ignoredFields,
    // initialValue, ...), comme pour save(Connection, T[], ...).
    static PreparedStatement prepareUpdateStatement(GenericDAO self, PreparedStatement stat, Field[] fields,
            Object idValue, Field primaryKey, Object obj) throws Exception {
        int index = 1;
        for (Field field : fields) {
            field.setAccessible(true);
            if (!FieldReflection.isPrimaryKey(field)) {
                if (FieldReflection.isObject(field)) {
                    Object fieldObjet = field.get(obj);
                    Field fieldPrimaryKey = FieldReflection.getPrimaryKey(self, fieldObjet.getClass());
                    fieldPrimaryKey.setAccessible(true);
                    Object fieldPrimaryKeyValue = fieldPrimaryKey.get(fieldObjet);
                    stat = setValueToField(self, stat, fieldPrimaryKeyValue, index);
                } else {
                    Object value = field.get(obj);
                    stat = setValueToField(self, stat, value, index);
                }
                index++;
            }
        }
        setValueToField(self, stat, idValue, index);
        return stat;
    }

    // Lie une liste de valeurs "brutes" (pas issues d'un Field) a partir de startIndex : utilise
    // pour les valeurs de setRecherche(...) (ILIKE ?), liees apres les conditions exactes.
    // Reutilise setValueToField (donc le formatage %...% pour une String, deja applique la meme
    // facon qu'avant pour ne pas dupliquer cette logique).
    static PreparedStatement bindValues(GenericDAO self, PreparedStatement stat, List<Object> values, int startIndex)
            throws Exception {
        int index = startIndex;
        for (Object value : values) {
            stat = setValueToField(self, stat, value, index);
            index++;
        }
        return stat;
    }
}
