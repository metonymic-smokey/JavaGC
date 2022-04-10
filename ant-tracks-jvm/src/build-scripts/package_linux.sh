version="$1"

dir_vm_jre=anttracks-vm-jre-"$version"
dir_vm_jdk=anttracks-vm-jdk-"$version"

rm -rf "$dir_vm_jre" "$dir_vm_jdk"

mkdir -p "$dir_vm_jre"/DEBIAN &&
mkdir -p "$dir_vm_jre"/usr/lib/anttracks &&

sed "s/VERSION/$version/g" control.template.vm.jre > "$dir_vm_jre"/DEBIAN/control &&
touch "$dir_vm_jre"/DEBIAN/conffiles &&
cp postinst.vm.jre "$dir_vm_jre"/DEBIAN/postinst &&
cp prerm.vm.jre "$dir_vm_jre"/DEBIAN/prerm &&

cp -r "./dist/release-64/j2re-image" "$dir_vm_jre"/usr/lib/anttracks/jre &&

chmod 555 "$dir_vm_jre"/DEBIAN/* &&
chmod +xr "$dir_vm_jre"/usr/lib/anttracks/jre/bin/* &&
#chown -R root:root "$dir_vm_jre"/ &&

dpkg-deb --build "$dir_vm_jre" &&





mkdir -p "$dir_vm_jdk"/DEBIAN &&
mkdir -p "$dir_vm_jdk"/usr/lib/anttracks &&

sed "s/VERSION/$version/g" control.template.vm.jdk > "$dir_vm_jdk"/DEBIAN/control &&
touch "$dir_vm_jdk"/DEBIAN/conffiles &&
cp postinst.vm.jdk "$dir_vm_jdk"/DEBIAN/postinst &&
cp prerm.vm.jdk "$dir_vm_jdk"/DEBIAN/prerm &&

cp -r "./dist/release-64/j2sdk-image" "$dir_vm_jdk"/usr/lib/anttracks/jdk &&

chmod 555 "$dir_vm_jdk"/DEBIAN/* &&
chmod +xr "$dir_vm_jdk"/usr/lib/anttracks/jdk/bin/* &&
chmod +xr "$dir_vm_jdk"/usr/lib/anttracks/jdk/jre/bin/* &&
#chown -R root:root "$dir_vm_jdk"/ &&

dpkg-deb --build "$dir_vm_jdk" &&

mv "$dir_vm_jre".deb ./dist/
mv "$dir_vm_jdk".deb ./dist/

result=$?
rm -rf "$dir_vm_jre" "$dir_vm_jdk"

if [ $result -ne 0 ]; then
	exit $result
fi

