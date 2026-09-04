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
- Connexions fournies par un pool HikariCP partagé (voir [Pool de connexions](#pool-de-connexions)), transparent pour le code appelant

## Prérequis

- JDK 17+
- Une base PostgreSQL accessible
- Le driver JDBC PostgreSQL (`postgresql-42.x.x.jar`), à télécharger séparément (non fourni dans ce dépôt)
- `HikariCP-x.x.x.jar` et `slf4j-api-x.x.x.jar` (pool de connexions, voir [Pool de connexions](#pool-de-connexions)), également à télécharger séparément

> Ce README couvre uniquement l'utilisation du DAO dans votre code. Pour la structure interne du projet, le découpage des classes et comment lancer les tests, voir [ARCHITECTURE.md](ARCHITECTURE.md).

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

Cette connexion vient d'un pool HikariCP partagé (voir [Pool de connexions](#pool-de-connexions)) — vous n'avez rien de plus à faire, `con.close()` la rend simplement au pool au lieu de la fermer physiquement.

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

⚠️ La table (et plus généralement toute config `set...` de l'entité) utilisée pour l'insertion vient de l'objet sur lequel `save(...)` est **appelé**, pas des objets du tableau. Si vous utilisez `setTableName(...)` pour cibler une table différente de celle par défaut, appliquez-le sur l'instance qui appelle `save(...)`, pas seulement sur celles du lot.

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

**Lecture.** Par défaut, `select()`/`findById()` ne font pas de `JOIN` : ils ne remplissent que la clé primaire de l'objet lié (`categorie.id`, via la colonne `id_categorie` déjà présente dans la table), pas ses autres attributs (`categorie.nom` reste `null`) — pour ne jamais imposer le coût d'un `JOIN` à un appel qui n'en a pas besoin.

**Pour charger l'objet lié en entier, activez `setFetchRelations` sur le(s) champ(s) concerné(s)** ; le `JOIN` et l'aliasing de colonnes sont générés automatiquement par réflexion :

```java
Materiel filtre = new Materiel();
filtre.setFetchRelations("categorie"); // active le JOIN pour ce champ, pour ce select() uniquement
Materiel[] complets = filtre.select(con, false); // categorie.nom est rempli
```

Ne gère qu'un seul niveau de relation (l'objet lié ne doit pas lui-même avoir de champ objet à charger — il est silencieusement ignoré, sans erreur), et fait un `JOIN` simple — une ligne dont la relation est absente disparaît du résultat, comme avec n'importe quel `JOIN` SQL classique.

Chaque relation utilise un alias de table dédié (dérivé du nom du champ) plutôt que le nom de table brut : deux relations vers le même type sur une même entité, ou une relation auto-référente (ex. `Employe.manager` de type `Employe`), fonctionnent donc sans conflit de nom de table. Les colonnes de l'entité elle-même sont aussi toujours qualifiées par sa table, pour rester valides même si la table liée expose une colonne du même nom.

Le nom de table de l'objet lié utilisé dans le `JOIN` est celui de son `@AClass` (ou son nom de classe par défaut) — **pas** un éventuel `setTableName(...)` posé sur une autre instance de ce type. Si l'objet lié doit pointer vers une table différente de son défaut (table de test isolée, schéma multi-tenant, ...), précisez-le explicitement :

```java
filtre.setRelationTableName("categorie", "categorie_test_isole");
```

**Cas plus avancés** (plusieurs niveaux d'imbrication, `LEFT JOIN`, conditions personnalisées dans le `JOIN`) : écrivez la requête vous-même et passez-la à `select(Connection, boolean, String)`, avec le même schéma d'alias `<colonne>_<nom du champ objet>` :

```java
String sql = "SELECT materiel_test.*, categorie_test.nom AS nom_categorie " +
             "FROM materiel_test JOIN categorie_test ON materiel_test.id_categorie = categorie_test.id";
Materiel[] complets = new Materiel().select(con, false, sql); // categorie.nom est rempli
```

Ou bien créez une vue PostgreSQL qui fait ce `JOIN` une bonne fois pour toutes, puis pointez l'entité dessus avec `setTableName` (qui prend le pas sur `@AClass(tableName = ...)`) :

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

⚠️ Dans les trois approches ci-dessus, la règle d'alias est `<colonne>_<nom du champ objet dans la classe parente>`, pas littéralement le nom de la classe liée — les deux coïncident ici uniquement parce que le champ s'appelle `categorie`, comme la classe `Categorie`. Un champ nommé différemment (`private Categorie cat;`) attendrait des alias en `nom_cat`, pas `nom_categorie`.

### Relations "un-à-plusieurs" (`List<Item>` / `Item[]`)

Un champ collection (`List<Item>` ou `Item[]`) représente une relation "un-à-plusieurs" : la clé étrangère est portée par la table de `Item`, pas par celle du champ collection. Il faut donc préciser, via `@AField(mappedBy = "...")`, le nom du champ côté `Item` qui référence l'entité en retour — la réflexion seule ne peut pas le deviner sans ambiguïté (plusieurs champs du même type, relation auto-référente, ...) :

```java
@AClass(tableName = "auteur_test")
public class Auteur extends GenericDAO {
    @AField(isId = true) private int id;
    @AField private String nom;

    @AField(mappedBy = "auteur") // "auteur" = nom du champ Livre.auteur, pas de la classe Auteur
    private List<Livre> livres;
    // ...
}

@AClass(tableName = "livre_test")
public class Livre extends GenericDAO {
    @AField(isId = true) private int id;
    @AField private String titre;
    @AField private Auteur auteur; // relation "un" classique, colonne "id_auteur"
    // ...
}
```

Un champ collection n'est jamais une colonne de sa propre table (ni en lecture, ni en écriture) : il n'est jamais concerné par `save`/`update`, et est ignoré par `select()` tant que `setFetchRelations` n'est pas activé dessus.

**Chargement**, comme pour une relation "un" — même méthode, `setFetchRelations` :

```java
Auteur filtre = new Auteur();
filtre.setId(auteurId);
filtre.setFetchRelations("livres");
Auteur[] resultats = filtre.select(con, false); // resultats[0].getLivres() rempli
```

⚠️ Contrairement à une relation "un" (un seul `JOIN`), une relation "un-à-plusieurs" **n'utilise jamais de `JOIN`** : un `JOIN` classique dupliquerait chaque parent une fois par enfant. Le chargement se fait via une requête séparée par relation demandée (`SELECT * FROM livre_test WHERE id_auteur IN (...)`, pour tous les parents déjà chargés en une fois, pas un par un), puis un regroupement en mémoire — voir l'estimation de performance dans [ARCHITECTURE.md](ARCHITECTURE.md). `setRelationTableName(...)` fonctionne aussi pour ces champs, comme pour une relation "un".

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
| `mappedBy` | Sur un champ `List<Item>`/`Item[]` : nom du champ côté `Item` portant la clé étrangère (voir [Relations "un-à-plusieurs"](#relations-un-à-plusieurs-listitem--item)) |
| `notIncremented` | Réservé pour les clés non auto-incrémentées |
| `sequence`, `sequenceBefore` | Réservés pour la gestion de séquences |

## Pool de connexions

`Connexion` s'appuie sur un pool [HikariCP](https://github.com/brettwooldridge/HikariCP) partagé pour toute la JVM, créé une seule fois à la toute première connexion demandée (au lieu d'ouvrir une connexion physique neuve à chaque appel). Rien ne change dans votre code :

```java
Connection con = Connexion.getConnection(false); // emprunte une connexion au pool
// ... votre code ...
con.close(); // rend la connexion au pool (ne ferme pas la connexion physique)
```

**Réglages** (`Generic/connexion/Connexion.java`), lus une seule fois à la création du pool :

```java
Connexion.MAXIMUM_POOL_SIZE = 10;      // nombre de connexions physiques maintenues ouvertes
Connexion.CONNECTION_TIMEOUT_MS = 30_000; // attente max pour obtenir une connexion libre
```

Les modifier une fois le pool déjà démarré (donc après votre tout premier appel à `getConnection`/`getConnect`) n'a plus d'effet — c'est inhérent à un pool : on ne peut pas reconfigurer à la volée des connexions physiques déjà établies.

**Arrêt propre.** Pour une application longue durée (serveur, ...), fermez le pool explicitement à l'arrêt :

```java
Connexion.shutdownPool();
```

Inutile pour un script one-shot (comme les programmes `test.*` de ce dépôt) : le pool meurt avec le process JVM. Ils l'appellent quand même par propreté.

## Journalisation

Le SQL généré et les événements de cycle de vie (commit, connexion fermée, ...) sont journalisés via `java.util.logging` au niveau `FINE`, **masqué par défaut** — les échecs (rollback, fermeture de connexion) restent visibles par défaut au niveau `WARNING`. Pour retrouver l'affichage du SQL (utile en développement), un fichier [logging.properties](logging.properties) est fourni :

```bash
java -Djava.util.logging.config.file=logging.properties -cp %CP% test.Test
```

Dans `run.bat`, décommentez simplement la ligne `set LOG_OPTS=...` en haut du fichier.

## Limites connues

- Les identifiants de connexion dans `Connexion.java` sont en dur dans le code (à externaliser avant tout usage partagé/public).
- Un champ `int`/`double` à `0`, ou `boolean` à `false` (les valeurs par défaut de Java), est considéré comme "non renseigné" par `save`/`select`/`update` (voir `init()`) : impossible d'écrire explicitement ces valeurs par défaut, ou de filtrer un `select()` "par l'exemple" dessus. Pour une valeur `0`/`false` volontaire, passez par `setOtherConditions(...)` ou une requête SQL personnalisée.
- Les champs `static` d'une entité (constantes, etc.) sont ignorés par la réflexion — seuls les champs d'instance sont mappés sur des colonnes.
