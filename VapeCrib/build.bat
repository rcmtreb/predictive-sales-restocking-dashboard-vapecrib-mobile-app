@echo off
echo Building VapeCrib App...
call gradlew clean
call gradlew assembleDebug
echo Build completed!
pause