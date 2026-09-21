#!/bin/sh
# Gradle Wrapper Script
exec "$JAVA_HOME/bin/java" -Dorg.gradle.jvmargs="-Xmx2048m -Dfile.encoding=UTF-8" -classpath "$GRADLE_HOME/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
