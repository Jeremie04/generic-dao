package Generic.util;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class Chart {
    public static Object getObjectField(Object obj, String colNom) throws Exception {
        Class objClass = obj.getClass();
        System.out.println(colNom);
        if (colNom.contains("/")) {
            String nom = colNom.split("/")[0];
            String nom2 = colNom.split(nom + "/")[1];
            System.out.println("nom1 :" + nom + " nom2 :" + nom2);
            Field champ = objClass.getDeclaredField(nom);
            champ.setAccessible(true);
            Object objectField = champ.get(obj);
            return getObjectField(objectField, nom2);
        } else {
            Field champ = objClass.getDeclaredField(colNom);
            champ.setAccessible(true);
            return champ.get(obj);
        }
    }

    // ex : LabelName : billet , colNom : nom , colValue : prix
    public static String toJsList(String colNom, String colValue, Object[] objets) {
        String liste = "";
        try {
            for (Object obj : objets) {
                @SuppressWarnings("rawtypes")
                Class objClass = obj.getClass();
                Field champ1 = objClass.getDeclaredField(colNom);
                champ1.setAccessible(true);
                Object nom = champ1.get(obj);

                Field champ2 = objClass.getDeclaredField(colValue);
                champ2.setAccessible(true);
                Object valeur = champ2.get(obj);

                liste = liste + "{ " + colValue + ": " + valeur + ", " + colNom + ": \"" + nom + "\"},";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return liste;
    }

    public static String toJsListCharBar(String colNom, String colValue, Object[] objets) {
        String liste = "";
        try {
            for (Object obj : objets) {
                Object nom = getObjectField(obj, colNom);
                Object valeur = getObjectField(obj, colValue);

                String name = colNom.split("/")[0];
                liste = liste + "{ " + name + ": \"" + nom + "\",  " + colValue + ": " + valeur + "}, \n";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return liste;
    }
}
