#!/bin/sh
# Builds the jar, copies it to update/, writes update/latest.json, commits and pushes.
set -e
cd "$(dirname "$0")"
V=$(sed -n 's/^mod_version=//p' gradle.properties)
JAVA_HOME=${JAVA_HOME:-$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home} ./gradlew build -q --no-daemon
F=ezgamble-$V.jar
mkdir -p update
git rm -q --ignore-unmatch 'update/ezgamble-*.jar' >/dev/null
cp build/libs/$F update/$F
SHA=$(shasum -a 256 update/$F | cut -d' ' -f1)
printf '{\n  "version": "%s",\n  "file": "%s",\n  "sha256": "%s"\n}\n' "$V" "$F" "$SHA" > update/latest.json
git add -A
git commit -q -m "Release $V"
git push -q origin main
echo "published $V ($SHA)"
