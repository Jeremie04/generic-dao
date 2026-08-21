# Architecture de GenericDAO

Ce document explique comment le code est organisé et comment le compiler/tester. Si vous voulez juste **utiliser** `GenericDAO` dans votre projet, voir plutôt [README.md](README.md).

## Structure du projet

```
Generic/
  annotation/   @AClass, @AField — décrivent la table et les colonnes
  connexion/    Connexion — pool HikariCP partagé (identifiants/réglages à adapter)
  dao/
    GenericDAO.java       — la classe à étendre : état de requête + API CRUD publique (orchestration)
    FieldReflection.java  — résolution table/colonne/clé primaire, découverte des champs, conversion de valeurs
    SqlBuilder.java        — construction des requêtes SQL (INSERT/SELECT/UPDATE/DELETE, conditions)
    StatementBinder.java   — liaison des valeurs dans un PreparedStatement
    ResultSetMapper.java   — reconstruction des objets à partir d'un ResultSet
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
  TestPerformance.java — mesure le temps d'exécution de save/select sur un volume de données important
  PerfEntityLarge.java, TestPerformanceAvance.java — mêmes mesures avec une entité à 17 attributs et avec une relation (Materiel/Categorie)
  GenericDAOCrudTest.java, GenericDAORelationTest.java,
  GenericDAOInheritanceTest.java, GenericDAOFieldHandlingTest.java — mêmes scénarios que ci-dessus, en tests JUnit 5 avec assertions
run.bat           — compile tout le projet puis lance les tests JUnit et les programmes de démonstration ci-dessus
bin/              — fichiers .class compilés (généré par run.bat, ignoré par git, ne contient aucune source)
```

## Découpage de `Generic/dao/`

`GenericDAO` est la seule classe publique du package : c'est elle que les entités étendent. Elle porte l'état de la requête en cours (recherche, filtres, tri, pagination, ...) et expose l'API CRUD publique (`save`, `select`, `update`, `delete`, `findById`, ...), mais délègue tout le travail à quatre classes **package-private** (visibles uniquement entre elles, jamais depuis l'extérieur du package `Generic.dao`) :

- **`FieldReflection`** — pure réflexion : quels champs considérer (`getFieldsNotIgnored`), lequel est la clé primaire, quel nom de colonne/table utiliser, comment convertir une valeur JDBC vers le type Java du champ. Met en cache par classe le résultat de la découverte des champs (évite de refaire `Class.getDeclaredFields()` + filtrage par annotation à chaque appel).
- **`SqlBuilder`** — construit les chaînes SQL (INSERT/SELECT/UPDATE/DELETE, conditions WHERE, recherche texte, pagination) à partir de l'état porté par l'entité. Génère aussi, sur demande (`setFetchRelations`, voir README), le `JOIN` et l'aliasing de colonnes nécessaires pour charger un objet lié en entier — dérivés par réflexion plutôt qu'écrits à la main.
- **`StatementBinder`** — lie les valeurs Java aux paramètres `?` d'un `PreparedStatement`.
- **`ResultSetMapper`** — reconstruit une entité (y compris ses objets imbriqués) à partir d'un `ResultSet`, en pré-calculant une seule fois par requête l'index des noms de colonnes (`buildColumnIndex`) plutôt que de s'appuyer sur l'exception que lève `ResultSet.findColumn()` pour une colonne absente — coûteux si répété à chaque champ de chaque ligne.

Toutes leurs méthodes sont `static` et prennent l'entité concernée (`GenericDAO self`) en paramètre explicite plutôt que de la porter en état interne : elles n'ont pas de cycle de vie propre, ce sont des regroupements de fonctions autour d'une responsabilité.

`Generic/dao/legacy/GenericDAO2.java` est une ancienne implémentation (monolithique, avec quelques divergences de comportement par rapport à `GenericDAO`), conservée uniquement pour référence historique. Elle n'est plus maintenue et ne doit pas être utilisée ni comme base de départ pour une nouvelle fonctionnalité.

## `Connexion` et le pool HikariCP

Le pool (`HikariDataSource`) est construit une seule fois via l'idiome *initialization-on-demand holder* : une classe imbriquée `PoolHolder` avec un champ `static final` initialisé par un appel de méthode. La JVM garantit que ce bloc ne s'exécute qu'une fois, au premier accès, sans synchronisation explicite au runtime — c'est le lazy-singleton le moins coûteux disponible en Java, et il est thread-safe par construction (contrairement à un double-checked locking manuel).

`getConnect()`/`getConnection(boolean)` restent des méthodes d'instance/statique avec la même signature qu'avant (voir README) ; en interne, elles empruntent simplement une connexion à `PoolHolder.DATA_SOURCE` au lieu d'appeler `DriverManager.getConnection(...)`. Le reste de `GenericDAO` (qui fait `new Connexion().getConnect()` quand `con == null`) n'a nécessité aucune modification.

Point d'attention si vous touchez à ce code : si la toute première tentative de connexion échoue (base injoignable, identifiants invalides), la construction de `PoolHolder.DATA_SOURCE` lève une exception dans un initialiseur statique — la JVM la enveloppe en `ExceptionInInitializerError`, et **toute classe `PoolHolder` ayant échoué à s'initialiser reste définitivement inutilisable pour le reste du process** (elle lève `NoClassDefFoundError` à chaque tentative suivante, même si la base redevient joignable). Pour les programmes `test.*` de ce dépôt (des process one-shot), ce n'est pas un problème pratique ; pour un serveur long terme qui doit survivre à une base momentanément indisponible au démarrage, il faudrait remplacer le holder par un singleton avec nouvelle tentative explicite.

## Compiler et lancer les tests

`run.bat` compile chaque fichier avec `javac -d bin` (les `.class` vont dans `bin/`, jamais à côté des sources), puis lance :
- les tests JUnit (voir plus bas) ;
- `test.Test` : crée une table de test et exerce chaque fonction de base (save unitaire et en lot, select, selectOne, findById, update, recherche, pagination, getTableSize, delete) ;
- `test.TestAvance` : même principe pour la relation objet imbriqué (`Materiel`/`Categorie`) et l'héritage (`Employe extends Personne`) ;
- `test.TestPerformance` : insère 2000 lignes (constante `NB_LIGNES` modifiable) puis chronomètre `save()` unitaire vs en lot, `select()` sur la table complète, `select()` paginé et `findById()`, avec le temps moyen par ligne ;
- `test.TestPerformanceAvance` : mêmes mesures que `TestPerformance`, mais sur une entité à 17 attributs (`PerfEntityLarge`, sans relation) puis sur une entité avec relation (`Materiel`/`Categorie`, avec et sans `JOIN`) — pour voir si/de combien le nombre d'attributs et les objets imbriqués alourdissent le coût de la réflexion par rapport à l'entité simple.

Chaque test nettoie ses propres tables à la fin.

Avant de lancer, éditez dans `run.bat` les chemins vers votre driver PostgreSQL et vers HikariCP/SLF4J (voir la section "Pool de connexions" du README) :

```bat
set POSTGRES_JAR=D:\classpath\postgresql-42.5.0.jar
set HIKARI_JAR=D:\classpath\HikariCP-5.1.0.jar
set SLF4J_JAR=D:\classpath\slf4j-api-2.0.13.jar
```

Puis :

```bash
run.bat
```

## Tests JUnit

`test/Test.java`, `TestAvance.java`, `TestPerformance*.java` sont des démonstrations qui affichent leur résultat pour relecture manuelle. `GenericDAOCrudTest`, `GenericDAORelationTest`, `GenericDAOInheritanceTest` et `GenericDAOFieldHandlingTest` couvrent les mêmes scénarios avec de vraies assertions JUnit 5 — y compris des tests de non-régression pour chaque bug corrigé au fil de ce projet (`selectOne()`, la pagination, les champs hérités, les champs `static`, les booléens par défaut, ...).

Ils tournent contre votre base PostgreSQL locale (pas de Testcontainers/Docker), via un unique jar autonome — pas besoin de Maven/Gradle :

1. Téléchargez `junit-platform-console-standalone` (dernière version) depuis le [dossier Maven Central](https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/) (prenez le fichier `junit-platform-console-standalone-<version>.jar`, pas les `-sources`/`-javadoc`), et placez le chemin dans `run.bat` :
   ```bat
   set JUNIT_JAR=D:\classpath\junit-platform-console-standalone-1.10.2.jar
   ```
2. `run.bat` compile les 4 classes de test et lance :
   ```bat
   java -jar %JUNIT_JAR% execute --class-path %CP% --select-package test --details tree
   ```
   (`%CP%` regroupe `bin` et toutes les dépendances externes — voir le début de `run.bat`.)
   La syntaxe exacte des options peut varier selon la version du jar téléchargée ; en cas d'échec, `java -jar %JUNIT_JAR% --help` liste les options disponibles pour votre version.

## Journalisation (détails d'implémentation)

Le SQL généré et les événements de cycle de vie (commit, connexion fermée, ...) sont journalisés via `java.util.logging` au niveau `FINE`, masqué par défaut — les échecs (rollback, fermeture de connexion) restent visibles par défaut au niveau `WARNING`. Voir [logging.properties](logging.properties) et la section "Journalisation" du README pour l'activer.
