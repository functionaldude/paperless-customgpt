#!/bin/sh

# Spring Boot 4 expects comma-separated config import locations. Provide a compatible default and normalize old values.
DEFAULT_IMPORT="optional:file:./,optional:file:./config/"

if [ -z "$SPRING_CONFIG_IMPORT" ]; then
  export SPRING_CONFIG_IMPORT="$DEFAULT_IMPORT"
else
  sanitized=$(printf '%s' "$SPRING_CONFIG_IMPORT" | tr ';' ',')
  if [ "$sanitized" != "$SPRING_CONFIG_IMPORT" ]; then
    export SPRING_CONFIG_IMPORT="$sanitized"
  fi
fi

exec java -jar /app/app.jar "$@"
