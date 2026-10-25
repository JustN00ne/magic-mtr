#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

ts=$(date +%Y%m%d_%H%M%S)
summary="${root_dir}/build_logs/build_all_now_${ts}.txt"

mkdir -p "${root_dir}/build_logs" "${root_dir}/dist_builds"
: > "$summary"

# Build matrix (keep 1.19.2 and 1.19.4; do not build 1.19 or 1.21.1).
versions=("1.16.5" "1.17.1" "1.18.2" "1.19.2" "1.19.4" "1.20.1" "1.20.4")

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
artifact_base="${archives_base_name}-${mod_version}"

# Parallel builds must not share the same Gradle project directory (build/ outputs + .gradle caches),
# otherwise different minecraft_version builds can clobber each other.
work_root="${root_dir}/build/tmp/build_mod_${ts}"
status_dir="${root_dir}/build_logs/build_all_now_${ts}.d"
mkdir -p "$work_root" "$status_dir"

# Limit parallelism with BUILD_JOBS (default: nproc). Set BUILD_JOBS=1 to force sequential builds.
build_jobs="${BUILD_JOBS:-}"
if [[ -z "$build_jobs" ]]; then
  build_jobs="$(nproc 2>/dev/null || echo 4)"
fi
if ! [[ "$build_jobs" =~ ^[0-9]+$ ]]; then
  echo "ERROR: BUILD_JOBS must be an integer, got: ${build_jobs}" >&2
  exit 2
fi
if [[ "$build_jobs" -lt 1 ]]; then
  build_jobs=1
fi

running=0
any_failed=0

for ver in "${versions[@]}"; do
  while [[ "$running" -ge "$build_jobs" ]]; do
    # Wait for any build to finish to free up a slot; do not fail-fast.
    if ! wait -n; then any_failed=1; fi
    running=$((running - 1))
  done

  log="${root_dir}/build_logs/build-${ver}-${ts}.log"
  status_file="${status_dir}/${ver}.txt"
  out_jar="${root_dir}/dist_builds/${artifact_base}-${ver}.jar"
  out_sources="${root_dir}/dist_builds/${artifact_base}-${ver}-sources.jar"
  ver_work_dir="${work_root}/${ver}"

  echo "BUILDING $ver (log: ${log})"

  (
    set -euo pipefail

    # Default to FAILED unless we reach the success path.
    : > "$log"
    echo "${ver}:FAILED:${log}" > "$status_file"

    rm -rf "$ver_work_dir"
    mkdir -p "$ver_work_dir"

    rsync -a --delete \
      --exclude '.git/' \
      --exclude '.gradle/' \
      --exclude '.idea/' \
      --exclude '*.iml' \
      --exclude '.jdks/' \
      --exclude 'build/' \
      --exclude 'build_logs/' \
      --exclude 'dist_builds/' \
      --exclude 'run/' \
      --exclude 'Minecraft-Transit-Railway-master/' \
      --exclude 'Transport-Simulation-Core-master/' \
      --exclude 'buildSrc/.gradle/' \
      --exclude 'buildSrc/build/' \
      "${root_dir}/" "${ver_work_dir}/"

    cd "$ver_work_dir"

    if ./gradlew clean build -Pminecraft_version="$ver" --no-daemon >> "$log" 2>&1; then
      jar="build/libs/${artifact_base}.jar"
      sources_jar="build/libs/${artifact_base}-sources.jar"

      if [[ ! -f "$jar" ]]; then
        jar="$(ls -1 build/libs/*.jar 2>/dev/null | grep -v -- '-sources\\.jar$' | head -n 1 || true)"
      fi
      if [[ ! -f "$sources_jar" ]]; then
        sources_jar="$(ls -1 build/libs/*-sources.jar 2>/dev/null | head -n 1 || true)"
      fi

      if [[ -z "$jar" || ! -f "$jar" ]]; then
        echo "ERROR: build succeeded but no jar found under build/libs/" >> "$log"
        exit 1
      fi

      cp -f "$jar" "$out_jar"
      if [[ -n "$sources_jar" && -f "$sources_jar" ]]; then
        cp -f "$sources_jar" "$out_sources"
      fi

      echo "${ver}:SUCCESS:${log}:${out_jar}" > "$status_file"

      if [[ "${KEEP_WORKDIR:-0}" != "1" ]]; then
        rm -rf "$ver_work_dir"
      fi
    else
      # Keep the workdir for debugging by default on failure.
      exit 1
    fi
  ) &
  running=$((running + 1))
done

while [[ "$running" -gt 0 ]]; do
  if ! wait -n; then any_failed=1; fi
  running=$((running - 1))
done

: > "$summary"
for ver in "${versions[@]}"; do
  status_file="${status_dir}/${ver}.txt"
  if [[ -f "$status_file" ]]; then
    cat "$status_file" >> "$summary"
  else
    echo "${ver}:FAILED:${root_dir}/build_logs/build-${ver}-${ts}.log" >> "$summary"
    any_failed=1
  fi
done

printf "\nSUMMARY=%s\n" "$summary"
cat "$summary"

if [[ "$any_failed" -ne 0 ]]; then
  echo "" >&2
  echo "One or more builds failed. Last 80 lines from each failed build log:" >&2
  for ver in "${versions[@]}"; do
    if grep -q "^${ver}:FAILED:" "$summary"; then
      log="${root_dir}/build_logs/build-${ver}-${ts}.log"
      echo "" >&2
      echo "===== ${ver} (${log}) =====" >&2
      tail -n 80 "$log" >&2 || true
    fi
  done
  exit 1
fi
