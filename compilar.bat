@echo off
title Compilando Sistema RPG
echo Compilando o mod...
echo.

call gradlew.bat build

echo.
if %ERRORLEVEL% EQU 0 (
    echo BUILD CONCLUIDO COM SUCESSO.
    echo O JAR esta na pasta build\libs.
) else (
    echo BUILD FALHOU. Leia a mensagem acima para identificar o erro.
)

echo.
pause
