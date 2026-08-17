@echo off
setlocal

rem === Chemin vers le driver JDBC PostgreSQL (a adapter a votre machine) ===
set POSTGRES_JAR=D:\classpath\postgresql-42.5.0.jar

rem === Le SQL genere n'est plus affiche par defaut (journalise au niveau FINE).
rem === Decommentez la ligne suivante pour le voir a nouveau (voir logging.properties) :
rem set LOG_OPTS=-Djava.util.logging.config.file=logging.properties
set LOG_OPTS=

rem === Chemin vers le jar JUnit 5 autonome (console-standalone), a adapter - voir README ===
set JUNIT_JAR=D:\classpath\junit-platform-console-standalone-1.14.3.jar

echo === Compilation ===

javac -d bin Generic\annotation\AClass.java
if errorlevel 1 goto :error

javac -d bin Generic\annotation\AField.java
if errorlevel 1 goto :error

javac -d bin Generic\exceptions\NotFoundException.java
if errorlevel 1 goto :error

javac -d bin Generic\exceptions\DatabaseException.java
if errorlevel 1 goto :error

javac -d bin Generic\util\Pagination.java
if errorlevel 1 goto :error

javac -d bin Generic\util\Parser.java
if errorlevel 1 goto :error

javac -d bin Generic\util\ParserAttributs.java
if errorlevel 1 goto :error

javac -d bin Generic\util\Chart.java
if errorlevel 1 goto :error

javac -d bin Generic\connexion\Connexion.java
if errorlevel 1 goto :error

javac -d bin Generic\dao\GenericDAO.java
if errorlevel 1 goto :error

javac -d bin test\MyEntity.java
if errorlevel 1 goto :error

javac -d bin test\Test.java
if errorlevel 1 goto :error

javac -d bin test\Categorie.java
if errorlevel 1 goto :error

javac -d bin test\Personne.java
if errorlevel 1 goto :error

javac -d bin test\Materiel.java
if errorlevel 1 goto :error

javac -d bin test\Employe.java
if errorlevel 1 goto :error

javac -d bin test\TestAvance.java
if errorlevel 1 goto :error

javac -d bin test\TestPerformance.java
if errorlevel 1 goto :error

javac -d bin test\PerfEntityLarge.java
if errorlevel 1 goto :error

javac -d bin test\TestPerformanceAvance.java
if errorlevel 1 goto :error

javac -d bin -cp bin;%JUNIT_JAR% test\GenericDAOCrudTest.java
if errorlevel 1 goto :error

javac -d bin -cp bin;%JUNIT_JAR% test\GenericDAORelationTest.java
if errorlevel 1 goto :error

javac -d bin -cp bin;%JUNIT_JAR% test\GenericDAOInheritanceTest.java
if errorlevel 1 goto :error

javac -d bin -cp bin;%JUNIT_JAR% test\GenericDAOFieldHandlingTest.java
if errorlevel 1 goto :error

echo.
echo === Compilation reussie ===

echo.
echo === Tests JUnit (assertions automatiques) ===
java %LOG_OPTS% -jar %JUNIT_JAR% execute --class-path bin;%POSTGRES_JAR% --select-package test --details tree
rem Si cette syntaxe echoue (varie selon la version du jar telechargee), essayez :
rem java -jar %JUNIT_JAR% --help

echo.
echo === Execution des tests de base (test.Test) ===
java %LOG_OPTS% -cp bin;%POSTGRES_JAR% test.Test

echo.
echo === Execution des tests avances (test.TestAvance) ===
java %LOG_OPTS% -cp bin;%POSTGRES_JAR% test.TestAvance

echo.
echo === Mesure de performance (test.TestPerformance) ===
java %LOG_OPTS% -cp bin;%POSTGRES_JAR% test.TestPerformance

echo.
echo === Mesure de performance avancee (test.TestPerformanceAvance) ===
java %LOG_OPTS% -cp bin;%POSTGRES_JAR% test.TestPerformanceAvance

echo.
pause
goto :eof

:error
echo.
echo *** Echec de la compilation ***
pause
exit /b 1
