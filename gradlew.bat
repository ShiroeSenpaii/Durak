@ECHO OFF
where gradle >NUL 2>&1
IF ERRORLEVEL 1 (
  ECHO gradle command not found. Install Gradle or use GitHub Actions setup-gradle step.
  EXIT /B 1
)
gradle %*
