@echo off
if exist *.class del /q *.class
start /b javaw "-Dfile.encoding=UTF-8" Servidor.java
start javaw "-Dfile.encoding=UTF-8" Cliente.java
exit
