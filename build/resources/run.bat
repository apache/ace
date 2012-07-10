@echo off
call java -jar pax-runner.jar --ups --workingDirectory=. scan-dir:required-bundles scan-dir:ace-bundles scan-file:file:platform.properties %1 %2 %3