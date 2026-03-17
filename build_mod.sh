ts=$(date +%Y%m%d_%H%M%S)
summary="build_logs/build_all_now_${ts}.txt"

mkdir -p build_logs dist_builds
: > "$summary"

versions=("1.16.5" "1.17.1" "1.18.2" "1.19" "1.19.2" "1.19.4" "1.20.1" "1.20.4" "1.21.1")

for ver in "${versions[@]}"; do
  log="build_logs/build-${ver}-${ts}.log"
  echo "BUILDING $ver"
  if ./gradlew clean build -Pminecraft_version="$ver" --no-daemon > "$log" 2>&1; then
    cp -f build/libs/MAGIC-0.1.0.jar "dist_builds/MAGIC-1.1.5-${ver}.jar"
    cp -f build/libs/MAGIC-0.1.0-sources.jar "dist_builds/MAGIC-${ver}-sources.jar"
    echo "$ver:SUCCESS:$log:dist_builds/MAGIC-1.1.0-${ver}.jar" | tee -a "$summary"
  else
    echo "$ver:FAILED:$log" | tee -a "$summary"
    tail -n 80 "$log"
    exit 1
  fi
done

printf "\nSUMMARY=%s\n" "$summary"
cat "$summary"