@echo off
setlocal

rem === Chemin vers le driver JDBC PostgreSQL (a adapter a votre machine) ===
set POSTGRES_JAR=D:\classpath\postgresql-42.5.0.jar

rem === Pool de connexions HikariCP (voir README, section "Pool de connexions") ===
set HIKARI_JAR=D:\classpath\HikariCP-7.0.2.jar
set SLF4J_JAR=D:\classpath\slf4j-api-2.0.9.jar

rem === Le SQL genere n'est plus affiche par defaut (journalise au niveau FINE).
rem === Decommentez la ligne suivante pour le voir a nouveau (voir logging.properties) :
rem set LOG_OPTS=-Djava.util.logging.config.file=logging.properties
set LOG_OPTS=

rem === Chemin vers le jar JUnit 5 autonome (console-standalone), a adapter - voir README ===
set JUNIT_JAR=D:\classpath\junit-platform-console-standalone-1.14.3.jar

rem === Classpath commun : sources deja compilees (bin) + toutes les dependances externes.
rem === Utilise pour TOUTES les compilations/executions, meme les fichiers qui n'en ont pas
rem === directement besoin (Connexion.java etant recompile implicitement des qu'un autre
rem === fichier le reference, il doit voir Hikari/SLF4J a chaque etape en aval de la sienne).
set CP=bin;%POSTGRES_JAR%;%HIKARI_JAR%;%SLF4J_JAR%

echo === Compilation ===

javac -d bin -cp %CP% -sourcepath . Generic\annotation\AClass.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\annotation\AField.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\exceptions\NotFoundException.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\exceptions\DatabaseException.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\util\Pagination.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\util\Parser.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\util\ParserAttributs.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\util\Chart.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\connexion\Connexion.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\dao\FieldReflection.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\dao\StatementBinder.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\dao\SqlBuilder.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\dao\ResultSetMapper.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . Generic\dao\GenericDAO.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\MyEntity.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Test.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Categorie.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Personne.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Materiel.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Employe.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\TestAvance.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\TestPerformance.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\PerfEntityLarge.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\TestPerformanceAvance.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Noeud.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Auteur.java
if errorlevel 1 goto :error

javac -d bin -cp %CP% -sourcepath . test\Livre.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAOCrudTest.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAORelationTest.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAORelationEdgeCasesTest.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAOCollectionRelationTest.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAOBatchAndSearchTest.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAOInheritanceTest.java
if errorlevel 1 goto :error

javac -d bin -cp %CP%;%JUNIT_JAR% -sourcepath . test\GenericDAOFieldHandlingTest.java
if errorlevel 1 goto :error

echo.
echo === Compilation reussie ===

echo.
echo === Tests JUnit (assertions automatiques) ===
java %LOG_OPTS% -jar %JUNIT_JAR% execute --class-path %CP% --select-package test --details tree
rem Si cette syntaxe echoue (varie selon la version du jar telechargee), essayez :
rem java -jar %JUNIT_JAR% --help

echo.
echo === Execution des tests de base (test.Test) ===
java %LOG_OPTS% -cp %CP% test.Test

echo.
echo === Execution des tests avances (test.TestAvance) ===
java %LOG_OPTS% -cp %CP% test.TestAvance

echo.
echo === Mesure de performance (test.TestPerformance) ===
java %LOG_OPTS% -cp %CP% test.TestPerformance

echo.
echo === Mesure de performance avancee (test.TestPerformanceAvance) ===
java %LOG_OPTS% -cp %CP% test.TestPerformanceAvance

echo.
pause
goto :eof

:error
echo.
echo *** Echec de la compilation ***
pause
exit /b 1
