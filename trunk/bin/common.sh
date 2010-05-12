#!/bin/bash

if [ -z "$JAVA" ] ; then
  if [ -z "$JAVA_HOME" ]; then
    export JAVA=java
  else
    export JAVA="$JAVA_HOME/bin/java"
  fi
fi

# Check java verson
function check_java_version() {
  # Produces a string like 1007 for java version 1.7 or 2021 for java version 2.21
  local java_version="$(perl -ne 'printf("%d%03d", $1, $2) if m/\b(\d+)\.(\d+)/; exit' <("$JAVA" -version 2>& 1))"
  if [ -z "$java_version" ] || [ "$java_version" -lt 1007 ] ; then
    echo Java Version 1.7 or later required
    exit -1
  fi
}
