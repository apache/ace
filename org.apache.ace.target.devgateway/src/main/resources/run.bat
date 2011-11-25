@echo off
call java -jar pax-runner.jar --autoWrap --workingDirectory=. scan-dir:ace-bundles scan-dir:required-bundles scan-file:file:conf/platform.properties %1 %2 %3