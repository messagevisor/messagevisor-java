#!/usr/bin/env bash

set -euo pipefail

find_main_jar() {
  find "$1/build/libs" -maxdepth 1 -type f -name "$2-*.jar" \
    ! -name "*-sources.jar" \
    ! -name "*-javadoc.jar" \
    -print -quit
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Missing published artifact: $1" >&2
    exit 1
  fi
}

require_jar_entry() {
  if ! jar tf "$1" | grep -Fxq "$2"; then
    echo "$1 does not contain $2" >&2
    exit 1
  fi
}

reject_jar_prefix() {
  if jar tf "$1" | grep -q "^$2"; then
    echo "$1 unexpectedly contains classes under $2" >&2
    exit 1
  fi
}

verify_publication() {
  local project="$1"
  local artifact="$2"
  local main_class="$3"
  local jar_file
  local pom_file="$project/build/publications/mavenJava/pom-default.xml"

  jar_file="$(find_main_jar "$project" "$artifact")"
  if [[ -z "$jar_file" ]]; then
    echo "Missing main JAR for $artifact" >&2
    exit 1
  fi

  require_file "$project/build/libs/$artifact-$version-sources.jar"
  require_file "$project/build/libs/$artifact-$version-javadoc.jar"
  require_file "$pom_file"
  require_jar_entry "$jar_file" "$main_class"

  if ! grep -Fq '<name>MIT License</name>' "$pom_file"; then
    echo "$pom_file does not declare the MIT licence" >&2
    exit 1
  fi

  if ! grep -Fq "<version>$version</version>" "$pom_file"; then
    echo "$pom_file does not use version $version" >&2
    exit 1
  fi
}

version="$(sed -n 's/^version=//p' gradle.properties)"
if [[ -z "$version" ]]; then
  echo "Could not resolve the project version from gradle.properties" >&2
  exit 1
fi

verify_publication "sdk" "messagevisor-sdk" "com/messagevisor/sdk/Messagevisor.class"
verify_publication "module-icu" "messagevisor-module-icu" "com/messagevisor/modules/icu/IcuModule.class"
verify_publication "module-interpolation" "messagevisor-module-interpolation" "com/messagevisor/modules/interpolation/InterpolationModule.class"
verify_publication "module-missing-translations" "messagevisor-module-missing-translations" "com/messagevisor/modules/missingtranslations/MissingTranslationsModule.class"

sdk_jar="$(find_main_jar sdk messagevisor-sdk)"
reject_jar_prefix "$sdk_jar" "com/messagevisor/modules/"

for project in module-icu module-interpolation module-missing-translations; do
  module_jar="$(find_main_jar "$project" "messagevisor-$project")"
  reject_jar_prefix "$module_jar" "com/messagevisor/sdk/"

  pom_file="$project/build/publications/mavenJava/pom-default.xml"
  if ! grep -Fq '<artifactId>messagevisor-sdk</artifactId>' "$pom_file"; then
    echo "$pom_file does not depend on messagevisor-sdk" >&2
    exit 1
  fi

  if ! grep -A2 -F '<artifactId>messagevisor-sdk</artifactId>' "$pom_file" | grep -Fq "<version>$version</version>"; then
    echo "$pom_file does not depend on messagevisor-sdk $version" >&2
    exit 1
  fi
done

sdk_pom="sdk/build/publications/mavenJava/pom-default.xml"
if grep -q '<artifactId>messagevisor-module-' "$sdk_pom"; then
  echo "$sdk_pom unexpectedly depends on a Messagevisor module" >&2
  exit 1
fi

echo "Messagevisor Java publication artifacts are isolated and complete."
