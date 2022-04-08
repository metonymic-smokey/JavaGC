# This file is assumed to be put in the JDK root folder

#version_minor="$1"
#version_build="$2"
#destination="$3"

version_build="$1" # First build parameter is the build number
#version="$version_major"."$version_minor"."$version_build"-"$(date +%Y.%m.%d-%H:%M:%S)"
version="$version_build"-"$(date +%Y.%m.%d-%H:%M:%S)"

destination="./dist"

mkdir -p "$destination"
for bits in 64
do
	for type in slowdebug fastdebug release
	do
		bash build_AntTracksVM_type.sh "$type" "$version" "$bits" &&
		mkdir -p "$destination"/"$type"-"$bits" && mv ./build/*-"$type"/images/*-image "$destination"/"$type"-"$bits" && rm -rf ./build ||
		exit 1
	done
done