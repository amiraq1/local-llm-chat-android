#!/bin/bash
# dev-clean.sh - Clean script for Android Project

echo "Cleaning Gradle project..."
./gradlew clean || true

echo "Removing build folders..."
rm -rf app/build mlc4j/build .gradle build

echo "Removing local gradle cache..."
rm -rf .gradle/

echo "Project cleaned successfully!"
