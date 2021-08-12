# This file is assumed to be put in the JDK root folder

path="$1"
tmp="$path.unzipped.__tmp__"

echo -n "rezipping $path ... " &&
unzip -qq "$path" -d "$tmp" && rm "$path" &&
pushd "$tmp" &> /dev/null && zip -qry9 ../$(basename "$path") * && popd &> /dev/null && rm -r "$tmp" &&
echo "ok" || echo "failed"
