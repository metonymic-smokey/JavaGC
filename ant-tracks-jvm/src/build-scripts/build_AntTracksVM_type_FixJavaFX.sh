openjfx_dir=/usr/share/java/openjfx
openjdk_dir=/usr/lib/jvm/java-8-openjdk-amd64
working_dir=$PWD

# Download OpenJFX 8
sudo apt install openjfx=8u161-b12-1ubuntu2 libopenjfx-java=8u161-b12-1ubuntu2 libopenjfx-jni=8u161-b12-1ubuntu2

release_jre_build32=$(echo $working_dir/build/*release*32*/images/j2re-image)
release_jdk_build32=$(echo $working_dir/build/*release/images/j2sdk-image)

fastdebug_jre_build32=$(echo $working_dir/build/*fast*32*/images/j2re-image)
fastdebug_jdk_build32=$(echo $working_dir/build/*fast*32*/images/j2sdk-image)

slowdebug_jre_build32=$(echo $working_dir/build/*slow*32*/images/j2re-image)
slowdebug_jdk_build32=$(echo $working_dir/build/*slow*32*/images/j2sdk-image)

release_jre_build64=$(echo $working_dir/build/*release*64*/images/j2re-image)
release_jdk_build64=$(echo $working_dir/build/*release*64*/images/j2sdk-image)

fastdebug_jre_build64=$(echo $working_dir/build/*fast*64*/images/j2re-image)
fastdebug_jdk_build64=$(echo $working_dir/build/*fast*64*/images/j2sdk-image)

slowdebug_jre_build64=$(echo $working_dir/build/*slow*64*/images/j2re-image)
slowdebug_jdk_build64=$(echo $working_dir/build/*slow*64*/images/j2sdk-image)

echo "working dir: $working_dir"

echo "slowdebug_jdk_build64: ${slowdebug_jdk_build64}"
echo "slowdebug_jre_build64: ${slowdebug_jre_build64}"

echo "fastdebug_jdk_build64: ${fastdebug_jdk_build64}"
echo "fastdebug_jre_build64: ${fastdebug_jre_build64}"

echo "release_jdk_build64: ${release_jdk_build64}"
echo "release_jre_build64: ${release_jre_build64}"

echo "slowdebug_jdk_build32: ${slowdebug_jdk_build32}"
echo "slowdebug_jre_build32: ${slowdebug_jre_build32}"

echo "fastdebug_jdk_build32: ${fastdebug_jdk_build32}"
echo "fastdebug_jre_build32: ${fastdebug_jre_build32}"

echo "release_jdk_build32: ${release_jdk_build32}"
echo "release_jre_build32: ${release_jre_build32}"

if [ -e $openjfx_dir ]
then
	echo "OpenJFX dir $openjfx_dir exists"
	for build_dir in $release_jre_build32 $release_jdk_build32 $fastdebug_jre_build32 $fastdebug_jre_build32 $slowdebug_jre_build32 $slowdebug_jdk_build32 $release_jre_build64 $release_jdk_build64 $fastdebug_jre_build64 $fastdebug_jre_build64 $slowdebug_jre_build64 $slowdebug_jdk_build64
	do
		if [ -e $build_dir ] 
		then 
			echo "Copying JavaFX files (.jar and .properties) from $openjfx_dir to build directory $build_dir"
			pushd "$openjfx_dir"
			find . -iname "*.jar" | cpio -pdm  $build_dir
			find . -iname "*.properties" | cpio -pdm  $build_dir
			
			echo "Copying files added by libopenjfx-jni"
			openjdk_path_length=$(echo "$openjdk_dir" | wc -c)
			for filepath in $(dpkg-query -L libopenjfx-jni | grep .so)
			do
				echo ""
				echo "$filepath"
				file=$(bash -c "filepath=$filepath && echo "'${'"filepath"':$(('"$openjdk_path_length"'))}')
				echo "$file"
				target="$build_dir/$file"
				echo "$target"
				target_dir=$(dirname "$target")
				echo "$target_dir"
				echo ""
				
				rm -f "$target"				
				mkdir -p "$target_dir"
				cp "$filepath" "$target"
			done
			popd
		else
			echo "No build directory found at $build_dir"
		fi
	done
else
	echo "OpenJFX dir $openjfx_dir DOES NOT exist, cancel."
fi