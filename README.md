# GenericDAO

Mini-framework Java qui généralise les opérations CRUD (Create, Read, Update, Delete) au-dessus de PostgreSQL via JDBC, sans ORM externe (pas d'Hibernate/JPA). Une classe métier hérite de `GenericDAO`, se décrit avec deux annotations (`@AClass`, `@AField`), et récupère gratuitement `save`, `select`, `update`, `delete`, `findById`, la recherche texte, la pagination et le mapping d'objets imbriqués — le tout par réflexion.

## Fonctionnalités

- CRUD générique : `save`, `select` (tous / un seul / par id), `update`, `delete`
- Insertion unitaire ou en lot (`save(Connection, T[], ...)`)
- Mapping automatique table ⇄ classe et colonne ⇄ champ via annotations, avec valeurs par défaut sensées si non précisées
- Détection et résolution des relations : un champ de type objet est mappé sur la clé primaire de l'objet lié (`categorie` → colonne `id_categorie`) — voir [Relations (objets imbriqués)](#relations-objets-imbriqués)
- Recherche texte multi-colonnes (`setRecherche`) avec filtres `LIKE`/`ILIKE` début/fin
- Pagination (`Pagination` + `setPaginable`) avec calcul automatique du nombre total de résultats
- Sélection restreinte à certains attributs, y compris sur des objets imbriqués (`setFieldsToSetSimplified("materiel(id, categorie(id, nom))")`)
- Tri (`setOrdre`), conditions additionnelles libres (`setOtherConditions`), exclusion de champs (`setIgnoredFields`)

## Prérequis

- JDK 17+
- Une base PostgreSQL accessible
- Le driver JDBC PostgreSQL (`postgresql-42.x.x.jar`), à télécharger séparément (non fourni dans ce dépôt)

## Structure du projet

```
Generic/
  annotation/   @AClass, @AField — décrivent la table et les colonnes
  connexion/    Connexion — ouverture de connexion JDBC (identifiants à adapter)
  dao/
    GenericDAO.java     — la classe à étendre, cœur du framework
    legacy/
      GenericDAO2.java  — ancienne version, conservée pour référence, ne pas utiliser
  exceptions/   NotFoundException, DatabaseException
  util/
    Pagination.java       — pagination par page ou par bornes début/fin
    Parser.java            — parsing de dates/timestamps
    ParserAttributs.java    — parsing de la syntaxe "materiel(id, categorie(id))"
    Chart.java              — utilitaires de génération de listes pour graphiques (indépendant du DAO)
test/
  MyEntity.java   — entité d'exemple simple (sans relation)
  Test.java       — teste chaque fonction de base du DAO contre une vraie base
  Categorie.java, Materiel.java  — exemple de relation "a un" (objet imbriqué)
  Personne.java, Employe.java    — exemple d'héritage (champs partagés via extends)
  TestAvance.java — teste les relations et l'héritage
run.bat           — compile tout le projet puis lance test.Test et test.TestAvance
```

## Démarrage rapide

### 1. Décrire une entité

```java
package test;

import Generic.annotation.AClass;
import Generic.annotation.AField;
import Generic.dao.GenericDAO;

@AClass(tableName = "generic_dao_test")   // optionnel : par défaut, nom de la classe
public class MyEntity extends GenericDAO {

    @AField(isId = true, column = "id")   // clé primaire, obligatoire
    private int id;

    @AField(column = "nom")
    private String nom;

    private String description;           // pas d'annotation => colonne "description"

    // getters / setters ...
}
```

### 2. Se connecter

Les identifiants de connexion se trouvent dans [Generic/connexion/Connexion.java](Generic/connexion/Connexion.java) (`HOST`, `PORT`, `DATABASE`, `USERNAME`, `PASSWORD`) — à adapter à votre environnement avant utilisation.

```java
Connection con = Connexion.getConnection(false); // false = autocommit désactivé
```

### 3. CRUD

```java
// CREATE
MyEntity e = new MyEntity();
e.setNom("Premier");
e.setDescription("Une ligne");
Object id = e.save(con, false, true); // (connexion, fermer la connexion ?, commit ?)

// READ (tous)
MyEntity[] all = new MyEntity().select(con, false);

// READ (par id)
MyEntity found = new MyEntity().findById(con, id, false);

// UPDATE
found.setDescription("Modifiée");
found.update(con, id, false, true);

// DELETE
new MyEntity().delete(con, id, false, true);
```

Chaque méthode CRUD prend `(Connection, boolean isClose, boolean commit)` (ou l'équivalent) : `isClose` ferme la connexion à la fin de l'appel, `commit` valide la transaction. Pour enchaîner plusieurs opérations sur la même transaction, passez `isClose = false` partout et fermez/committez vous-même à la fin.

### Insertion en lot

```java
MyEntity e2 = new MyEntity();
e2.setNom("Deuxieme");
MyEntity e3 = new MyEntity();
e3.setNom("Troisieme");
new MyEntity().save(con, new MyEntity[] { e2, e3 }, false, true);
```

À la différence de `save()` sur un seul objet, cette variante ne renvoie pas les id générés (elle retourne `void`).

### Recherche texte

```java
MyEntity search = new MyEntity();
search.setRecherche("Deuxieme");      // OR ILIKE sur toutes les colonnes String de l'entité
MyEntity[] results = search.select(con, false);
```

### Pagination

```java
MyEntity page = new MyEntity();
page.setPaginable(true);
page.setPagination(Pagination.fromPageNumber(1, 20)); // page 1, 20 éléments par page
MyEntity[] rows = page.select(con, false);
System.out.println(page.getPagination().getTotalSize()); // nombre total de résultats
```

### Relations (objets imbriqués)

Un champ dont le type n'est ni primitif, ni `String`/`Number`/`Date`/`Timestamp` est traité comme une relation "a un". Il est mappé sur une colonne `<clé primaire du champ objet>_<nom du champ>` :

```java
@AClass(tableName = "materiel_test")
public class Materiel extends GenericDAO {
    @AField(isId = true, column = "id")
    private int id;

    @AField(column = "categorie")
    private Categorie categorie;   // mappé sur la colonne "id_categorie"
    // ...
}
```

**Écriture.** L'objet lié doit déjà avoir sa clé primaire renseignée (l'objet complet n'est jamais inséré, seule sa clé primaire est écrite dans la colonne FK) :

```java
Categorie ref = new Categorie();
ref.setId(categorieId);       // suffit : c'est cette valeur qui sera écrite dans id_categorie
Materiel m = new Materiel();
m.setCategorie(ref);
m.save(con, false, true);
```

**Lecture.** `select()`/`findById()` générés automatiquement ne font pas de `JOIN` : ils ne remplissent que la clé primaire de l'objet lié (`categorie.id`, via la colonne `id_categorie` déjà présente dans la table), pas ses autres attributs (`categorie.nom` reste `null`). Pour charger l'objet lié en entier, il faut fournir une requête qui fait le `JOIN` soi-même et qui aliase chaque colonne voulue en `<colonne>_<nom du champ objet>` (ici `categorie` étant le nom du champ dans `Materiel`, on obtient `nom_categorie`) :

```java
String sql = "SELECT materiel_test.*, categorie_test.nom AS nom_categorie " +
             "FROM materiel_test JOIN categorie_test ON materiel_test.id_categorie = categorie_test.id";
Materiel[] complets = new Materiel().select(con, false, sql); // categorie.nom est rempli
```

**Alternative : une vue SQL.** Plutôt que d'écrire cette requête à chaque appel, on peut créer une vue PostgreSQL qui fait le `JOIN` une bonne fois pour toutes avec le même schéma d'alias, puis pointer l'entité dessus avec `setTableName` (qui prend le pas sur `@AClass(tableName = ...)`) :

```sql
CREATE VIEW materiel_avec_categorie AS
SELECT materiel_test.id, materiel_test.designation, materiel_test.id_categorie,
       categorie_test.nom AS nom_categorie
FROM materiel_test
JOIN categorie_test ON materiel_test.id_categorie = categorie_test.id;
```

```java
Materiel m = new Materiel();
m.setTableName("materiel_avec_categorie"); // remplace la table par la vue, juste pour cette instance
Materiel[] complets = m.select(con, false); // categorie.nom rempli, sans SQL personnalisé a chaque fois
```

⚠️ Une vue basée sur un `JOIN` n'est en général pas modifiable directement dans PostgreSQL (il faudrait des `INSTEAD OF` triggers). Réservez cette technique à la lecture (`select`/`findById`), et gardez une instance pointant sur la vraie table (`materiel_test`) pour `save`/`update`/`delete`.

⚠️ La règle d'alias est `<colonne>_<nom du champ objet dans la classe parente>`, pas littéralement le nom de la classe liée — les deux coïncident ici uniquement parce que le champ s'appelle `categorie`, comme la classe `Categorie`. Un champ nommé différemment (`private Categorie cat;`) attendrait des alias en `nom_cat`, pas `nom_categorie`.

## Annotations

**`@AClass`**
| Attribut | Rôle |
|---|---|
| `tableName` | Nom de la table (défaut : nom de la classe) |

**`@AField`**
| Attribut | Rôle |
|---|---|
| `isId` | Marque la clé primaire (une seule par entité) |
| `column` | Nom de la colonne (défaut : nom du champ Java) |
| `ignored` | Exclut le champ de toutes les opérations CRUD |
| `notIncremented` | Réservé pour les clés non auto-incrémentées |
| `sequence`, `sequenceBefore` | Réservés pour la gestion de séquences |

## Compiler et lancer les tests

`run.bat` compile chaque fichier avec `javac -d .`, puis lance :
- `test.Test` : crée une table de test et exerce chaque fonction de base (save unitaire et en lot, select, selectOne, findById, update, recherche, pagination, getTableSize, delete) ;
- `test.TestAvance` : même principe pour la relation objet imbriqué (`Materiel`/`Categorie`) et l'héritage (`Employe extends Personne`).

Chaque test nettoie ses propres tables à la fin.

Avant de lancer, éditez la ligne suivante dans `run.bat` avec le chemin réel de votre driver PostgreSQL :

```bat
set POSTGRES_JAR=D:\classpath\postgresql-42.5.0.jar
```

Puis :

```bash
run.bat
```

## Limites connues

- Les identifiants de connexion dans `Connexion.java` sont en dur dans le code (à externaliser avant tout usage partagé/public).
- `Generic/dao/legacy/GenericDAO2.java` est une ancienne implémentation conservée pour référence uniquement ; elle n'est plus maintenue et ne doit pas être utilisée.
- Pas de gestion de pool de connexions : chaque appel ouvre/utilise une `Connection` JDBC classique.
