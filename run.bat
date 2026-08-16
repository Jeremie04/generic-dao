@echo off
setlocal

rem === Chemin vers le driver JDBC PostgreSQL (a adapter a votre machine) ===
set POSTGRES_JAR=D:\classpath\postgresql-42.5.0.jar

rem === Le SQL genere n'est plus affiche par defaut (journalise au niveau FINE).
rem === Decommentez la ligne suivante pour le voir a nouveau (voir logging.properties) :
rem set LOG_OPTS=-Djava.util.logging.config.file=logging.properties
set LOG_OPTS=

echo === Compilation ===

javac -d . Generic\annotation\AClass.java
if errorlevel 1 goto :error

javac -d . Generic\annotation\AField.java
if errorlevel 1 goto :error

javac -d . Generic\exceptions\NotFoundException.java
if errorlevel 1 goto :error

javac -d . Generic\exceptions\DatabaseException.java
if errorlevel 1 goto :error

javac -d . Generic\util\Pagination.java
if errorlevel 1 goto :error

javac -d . Generic\util\Parser.java
if errorlevel 1 goto :error

javac -d . Generic\util\ParserAttributs.java
if errorlevel 1 goto :error

javac -d . Generic\util\Chart.java
if errorlevel 1 goto :error

javac -d . Generic\connexion\Connexion.java
if errorlevel 1 goto :error

javac -d . Generic\dao\GenericDAO.java
if errorlevel 1 goto :error

javac -d . test\MyEntity.java
if errorlevel 1 goto :error

javac -d . test\Test.java
if errorlevel 1 goto :error

javac -d . test\Categorie.java
if errorlevel 1 goto :error

javac -d . test\Personne.java
if errorlevel 1 goto :error

javac -d . test\Materiel.java
if errorlevel 1 goto :error

javac -d . test\Employe.java
if errorlevel 1 goto :error

javac -d . test\TestAvance.java
if errorlevel 1 goto :error

javac -d . test\TestPerformance.java
if errorlevel 1 goto :error

javac -d . test\PerfEntityLarge.java
if errorlevel 1 goto :error

javac -d . test\TestPerformanceAvance.java
if errorlevel 1 goto :error

echo.
echo === Compilation reussie ===

echo.
echo === Execution des tests de base (test.Test) ===
java %LOG_OPTS% -cp .;%POSTGRES_JAR% test.Test

echo.
echo === Execution des tests avances (test.TestAvance) ===
java %LOG_OPTS% -cp .;%POSTGRES_JAR% test.TestAvance

echo.
echo === Mesure de performance (test.TestPerformance) ===
java %LOG_OPTS% -cp .;%POSTGRES_JAR% test.TestPerformance

echo.
echo === Mesure de performance avancee (test.TestPerformanceAvance) ===
java %LOG_OPTS% -cp .;%POSTGRES_JAR% test.TestPerformanceAvance

echo.
pause
goto :eof

:error
echo.
echo *** Echec de la compilation ***
pause
exit /b 1
