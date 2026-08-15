package Generic.util;

import java.util.*;

public class ParserAttributs {

    public static List<String> parser(String expression) {
        Set<String> resultat = new LinkedHashSet<>();
        parseRecursive(expression, "", "", resultat);
        return new ArrayList<>(resultat);
    }

    private static void parseRecursive(String expr, String rootPath, String localPath, Set<String> resultat) {
        expr = expr.trim();

        int idxParenthese = expr.indexOf('(');
        if (idxParenthese == -1) {
            // Feuille : ex "id" ou "designation"
            String field = expr;
            if (!localPath.isEmpty()) {
                resultat.add(localPath + "." + field); // ex: categorie.id
            } else if (!rootPath.isEmpty()) {
                resultat.add(rootPath + "." + field); // ex: materiel.id
            } else {
                resultat.add(field); // racine seule
            }
            return;
        }

        // Objet : ex "categorie(id, ...)"
        String objet = expr.substring(0, idxParenthese).trim();

        String newRootPath = rootPath.isEmpty() ? objet : rootPath + "." + objet;
        String newLocalPath = objet;

        if (!rootPath.isEmpty()) {
            resultat.add(localPath + "." + objet); // ex: materiel.categorie => categorie
        }

        String contenu = expr.substring(idxParenthese + 1, expr.lastIndexOf(')'));
        List<String> enfants = splitArguments(contenu);

        for (String enfant : enfants) {
            parseRecursive(enfant, newRootPath, newLocalPath, resultat);
        }
    }

    private static List<String> splitArguments(String s) {
        List<String> result = new ArrayList<>();
        int profondeur = 0;
        StringBuilder buffer = new StringBuilder();

        for (char c : s.toCharArray()) {
            if (c == ',' && profondeur == 0) {
                result.add(buffer.toString().trim());
                buffer.setLength(0);
            } else {
                if (c == '(')
                    profondeur++;
                if (c == ')')
                    profondeur--;
                buffer.append(c);
            }
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }

        return result;
    }

    // public static void main(String[] args) {
    // String input = "materiel(id, categorie(id, etat(nom, code)), designation)";
    // List<String> result = parser(input);
    // result.forEach(System.out::println);
    // }
}
