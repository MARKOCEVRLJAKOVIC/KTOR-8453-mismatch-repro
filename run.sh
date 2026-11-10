#!/usr/bin/env bash
# Runs the reproduction twice: with Ktor's POM as published, and with jakarta.servlet-api forced to 6.0.0.
set -u
cd "$(dirname "$0")"

run() {
    local label="$1"; shift
    echo
    echo "=================== $label ==================="
    mvn -q -B "$@" dependency:tree -Dverbose \
        -Dincludes=jakarta.servlet:jakarta.servlet-api,org.eclipse.jetty.ee10:* \
        -DoutputFile=target/tree.txt && cat target/tree.txt
    echo
    mvn -q -B "$@" clean compile exec:exec
    echo "exit code: $?"
}

run "default (Ktor POM as published)"
run "fixed (jakarta.servlet-api 6.0.0)" -Pfixed
