# GenericDAO

Mini-framework Java qui généralise les opérations CRUD (Create, Read, Update, Delete) au-dessus de PostgreSQL via JDBC, sans ORM externe (pas d'Hibernate/JPA). Une classe métier hérite de `GenericDAO`, se décrit avec deux annotations (`@AClass`, `@AField`), et récupère gratuitement `save`, `select`, `update`, `delete`, `findById`, la recherche texte, la pagination et le mapping d'objets imbriqués — le tout par réflexion.

## Fonctionnalités

- CRUD générique : `save`, `select` (tous / un seul / par id), `update`, `delete`
- Insertion unitaire ou en lot (`save(Connection, T[], ...)`)
- Mapping automatique table ⇄ classe et colonne ⇄ champ via annotations, avec valeurs par défaut sensées si non précisées
- Détection et résolution des relations : un champ de type objet est mappé sur la clé primaire de l'objet lié (`categorie` → colonne `id_categorie`)
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
  MyEntity.java   — entité d'exemple utilisée par le test
  Test.java       — programme qui exerce chaque fonction du DAO contre une vraie base
run.bat           — compile tout le projet puis lance test.Test
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

`run.bat` compile chaque fichier avec `javac -d .`, puis lance la classe `test.Test`, qui crée une table de test, exerce chaque fonction du DAO (save unitaire et en lot, select, selectOne, findById, update, recherche, pagination, getTableSize, delete), puis nettoie la table.

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
