#!/bin/sh

# Spring Boot 4 expects comma-separated config import locations. Normalize old values and drop obsolete defaults.
if [ -n "$SPRING_CONFIG_IMPORT" ]; then
  sanitized=$(printf '%s' "$SPRING_CONFIG_IMPORT" | tr ';' ',')
  DEFAULT1="optional:file:./"
  DEFAULT2="optional:file:./config/"
  DEFAULT3="optional:file:./config/*/"
  filtered=""
  OLDIFS=$IFS
  IFS=','
  for entry in $sanitized; do
    entry=$(printf '%s' "$entry" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    case "$entry" in
      ""|"$DEFAULT1"|"$DEFAULT2"|"$DEFAULT3") continue ;;
    esac
    if [ -z "$filtered" ]; then
      filtered="$entry"
    else
      filtered="$filtered,$entry"
    fi
  done
  IFS=$OLDIFS
  if [ -z "$filtered" ]; then
    unset SPRING_CONFIG_IMPORT
  else
    export SPRING_CONFIG_IMPORT="$filtered"
  fi
fi

exec java -jar /app/app.jar "$@"
