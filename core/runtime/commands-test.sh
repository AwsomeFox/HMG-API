#!/bin/sh
# Off-car test of the vehicle commands' guards (vehicle/Commands) against a fake car,
# and of what the MICOM watch never writes (vehicle/Scrub).
set -e
cd "$(dirname "$0")/../.."
CP=$(mktemp -d)
trap 'rm -rf "$CP"' EXIT
javac --release 8 -Xlint:-options -nowarn -d "$CP" core/runtime/java/hmgapi/vehicle/Commands.java core/runtime/java/hmgapi/vehicle/Scrub.java core/runtime/test/hmgapi/vehicle/CommandsTest.java
java -cp "$CP" hmgapi.vehicle.CommandsTest
