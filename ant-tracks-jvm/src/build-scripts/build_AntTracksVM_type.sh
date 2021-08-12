# This file is assumed to be put in the JDK root folder

type="$1"
version="$2"
bits="$3"

latestWorkingJDK="jdk8u202-b00"
hg update --clean "$latestWorkingJDK"

# Download freetype/include on demand
# ./freetype/lib must contain the libraries, ./freetype/include must contain the headers
freetype="./freetype"
freetype_absolute="$(pwd)/freetype"
rm -rf $freetype

# Following link was broken on 04 May 2018:
#wget -O freetype.zip http://mirror.netcologne.de/savannah/freetype/ft271.zip
# Let's try this one again:

ls -la

wget -O $freetype_absolute.zip http://download.savannah.gnu.org/releases/freetype/ft271.zip

ls -la

unzip $freetype_absolute.zip -d $freetype && f="$freetype"/* && mv "$freetype"/*/* "$freetype" && rmdir "${f[@]}"

ls -la

case "$(uname -s)" in

   Darwin)
     # No build support for OS X
     echo 'Mac OS X'
     ;;

   Linux)
     # Link lib folder
     ln -s /usr/lib/x86_64-linux-gnu $freetype/lib
     ;;

   CYGWIN*|MINGW32*|MSYS*)
     # Copy lib folder
     mkdir $freetype/lib
     cp /cygdrive/C/freetype$bits/lib/* $freetype/lib
     ;;
esac

pushd jaxp
hg update --clean "$latestWorkingJDK"
popd
pushd jdk
hg update --clean "$latestWorkingJDK"
popd
pushd common
hg update --clean "$latestWorkingJDK"
popd
pushd nashorn
hg update --clean "$latestWorkingJDK"
popd
pushd langtools
hg update --clean "$latestWorkingJDK"
popd
pushd jaxws
hg update --clean "$latestWorkingJDK"
popd
pushd corba
hg update --clean "$latestWorkingJDK"
popd

if [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
	# export these only on linux, other compilers may crash with unknown options ...
	# export CFLAGS="$CFLAGS -Wno-deprecated-declarations"
	# export CXXFLAGS="$CXXFLAGS -Wno-deprecated-declarations"
  export CFLAGS="-w"
  export CXXFLAGS="-w"
fi

# This drastically slows down build, don't do this
# echo "clean $type"
# make dist-clean CONF="$type"

echo "configure $type" &&
bash configure --with-freetype="$freetype_absolute" --with-debug-level="$type" \
	--with-user-release-suffix="AntTracks-$version" \
	--enable-hotspot-test-in-build --with-target-bits="$bits" &&
	
echo "build $bits bit version of $type" &&
make CONF="$type" DEBUG_BINARIES=true DISABLE_HOTSPOT_OS_VERSION_CHECK=true images 2>&1 &&

#echo "package $type" &&
#find build/*/images/*-image -type f -iname "*.jar" -exec bash ./rezip.sh {} \; &&
#find build/*/images/*-image -type f -iname "*.diz" -exec bash ./rezip.sh {} \; &&
#find build/*/images/*-image -type f -iname "*.zip" -exec bash ./rezip.sh {} \; &&

echo "Fix JavaFX for $type" &&
bash build_AntTracksVM_type_FixJavaFX.sh

echo "finished $type"

result=$?

#if [ $result -ne 0 ]; then
#	lc=$(cat "$log_file" | grep -e "internal compiler error" -e "differs from the endian expected to be found in the target" -e "cannot access NonReadableChannelException" | wc -l)
#	if [ $lc -gt 0 ]; then
#		bash build_AntTracksVM_type.sh "$type" "$version"
#	else
#		exit $result
#	fi
#else
#	exit 0
#fi

exit $result
