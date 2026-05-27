@echo off
rem Tenta deletar os arquivos class antigos. Se algum estiver travado (app rodando), ignora.
del /q *.class >nul 2>&1

if exist Cliente.class (
    goto rodar
)

rem Compila rapidinho se não estiver compilado/rodando
javac --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics -encoding UTF-8 Cliente.java
if %errorlevel% neq 0 (
    echo [ERRO] A compilacao falhou!
    pause
    exit /b
)

:rodar
rem Inicia o Servidor escondidinho no fundo (se já estiver aberto, falha silenciosamente e segue)
start /b javaw "-Dfile.encoding=UTF-8" Servidor.java

rem Inicia o Cliente como um App
start javaw --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics "-Dfile.encoding=UTF-8" -Djava.library.path="lib" -Dprism.order=sw Cliente

exit
