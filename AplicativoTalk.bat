@echo off
if exist *.class del /q *.class

rem Compila rapidinho (a piscada e inevitavel aqui, mas e rapido)
javac --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics -encoding UTF-8 Cliente.java
if %errorlevel% neq 0 (
    echo [ERRO] A compilacao falhou!
    pause
    exit /b
)

rem Inicia o Servidor escondidinho no fundo
start /b javaw "-Dfile.encoding=UTF-8" Servidor.java

rem Inicia o Cliente como um App (javaw nao abre janela preta)
rem O "start" faz o .bat seguir em frente e fechar enquanto o app roda
start javaw --module-path "lib" --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.graphics "-Dfile.encoding=UTF-8" -Djava.library.path="lib" -Dprism.order=sw Cliente

rem Fecha o terminal na hora
exit
