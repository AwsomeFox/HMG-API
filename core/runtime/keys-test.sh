#!/bin/sh
# Off-car test of the button press logic (hook/KeyPresses: short, hold,
# down; what's held back from the car and what it gets), with a fake clock.
set -e
cd "$(dirname "$0")/../.."
CP=$(mktemp -d)
trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" core/runtime/java/hmgapi/hook/KeyPresses.java core/runtime/test/hmgapi/hook/KeyPressesTest.java
java -cp "$CP" hmgapi.hook.KeyPressesTest
