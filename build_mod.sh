#!/usr/bin/env bash
set -euo pipefail

ts=$(date +%Y%m%d_%H%M%S)
summary="build_logs/build_all_now_${ts}.txt"

mkdir -p build_logs dist_builds
: > "$summary"

versions=("1.16.5" "1.17.1" "1.18.2" "1.19" "1.19.2" "1.19.4" "1.20.1" "1.20.4" "1.21.1") # i still dont know why i am building 1.21.1 xd

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

get_gradle_prop() {
  # Reads simple `key=value` pairs from gradle.properties.
  local key="$1"
  local props_file="${root_dir}/gradle.properties"
  local line
  line="$(grep -E "^[[:space:]]*${key}=" "$props_file" | head -n 1 || true)"
  if [[ -z "$line" ]]; then
    echo "ERROR: Missing '${key}=' in ${props_file}" >&2
    exit 2
  fi
  echo "${line#*=}"
}

java_major_from_java() {
  local java_bin="$1"
  local ver
  ver="$("$java_bin" -version 2>&1 | awk -F\" '/version/ {print $2; exit}')"
  if [[ -z "$ver" ]]; then
    echo 0
    return
  fi
  if [[ "$ver" == 1.* ]]; then
    ver="${ver#1.}"
  fi
  echo "${ver%%.*}"
}

pick_java_home_17_plus() {
  local candidate
  local java_bin
  local major

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    major="$(java_major_from_java "${JAVA_HOME}/bin/java")"
    if [[ "$major" -ge 17 ]]; then
      echo "$JAVA_HOME"
      return
    fi
  fi

  # Prefer a project-local JDK if present (IntelliJ downloads, etc).
  for candidate in "${root_dir}/.jdks"/jdk-*; do
    [[ -d "$candidate" ]] || continue
    java_bin="${candidate}/bin/java"
    [[ -x "$java_bin" ]] || continue
    major="$(java_major_from_java "$java_bin")"
    if [[ "$major" -ge 17 ]]; then
      echo "$candidate"
      return
    fi
  done

  # Common Linux install locations.
  for candidate in /usr/lib/jvm/java-21-openjdk-* /usr/lib/jvm/java-17-openjdk-*; do
    [[ -d "$candidate" ]] || continue
    java_bin="${candidate}/bin/java"
    [[ -x "$java_bin" ]] || continue
    major="$(java_major_from_java "$java_bin")"
    if [[ "$major" -ge 17 ]]; then
      echo "$candidate"
      return
    fi
  done
}

jdk_home="$(pick_java_home_17_plus || true)"
if [[ -n "${jdk_home}" ]]; then
  export JAVA_HOME="$jdk_home"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java not found on PATH and no Java 17+ JDK was detected under ${root_dir}/.jdks/." >&2
  exit 2
fi

java_major="$(java_major_from_java "$(command -v java)")"
if [[ "$java_major" -lt 17 ]]; then
  echo "ERROR: Java 17+ is required to build this project (fabric-loom 1.7). Detected Java ${java_major}." >&2
  echo "Fix: install a JDK 17+ and re-run, or set JAVA_HOME to it." >&2
  exit 2
fi

archives_base_name="$(get_gradle_prop archives_base_name)"
mod_version="$(get_gradle_prop mod_version)"

for ver in "${versions[@]}"; do
  log="build_logs/build-${ver}-${ts}.log"
  echo "BUILDING $ver"
  if ./gradlew clean build -Pminecraft_version="$ver" --no-daemon > "$log" 2>&1; then
    jar="build/libs/${archives_base_name}-${mod_version}.jar"
    sources_jar="build/libs/${archives_base_name}-${mod_version}-sources.jar"

    if [[ ! -f "$jar" ]]; then
      jar="$(ls -1 build/libs/*.jar 2>/dev/null | grep -v -- '-sources\\.jar$' | head -n 1 || true)"
    fi
    if [[ ! -f "$sources_jar" ]]; then
      sources_jar="$(ls -1 build/libs/*-sources.jar 2>/dev/null | head -n 1 || true)"
    fi

    out_jar="dist_builds/${archives_base_name}-${mod_version}-${ver}.jar"
    out_sources="dist_builds/${archives_base_name}-${mod_version}-${ver}-sources.jar"

    cp -f "$jar" "$out_jar"
    if [[ -n "$sources_jar" && -f "$sources_jar" ]]; then
      cp -f "$sources_jar" "$out_sources"
    fi

    echo "$ver:SUCCESS:$log:${out_jar}" | tee -a "$summary"
  else
    echo "$ver:FAILED:$log" | tee -a "$summary"
    tail -n 80 "$log"
    exit 1
  fi
done

printf "\nSUMMARY=%s\n" "$summary"
cat "$summary"
