#!/bin/bash
#This script expects WiX Toolset to be installed at /cygdrive/c/Program Files (x86)/WiX Toolset v3.11/bin/

version="$1"
wix="/cygdrive/c/Program Files (x86)/WiX Toolset v3.11/bin/"

mkdir .tmp &&
cp -a "./dist/release-64/" .tmp &&

chmod -R 777 . &&
"$wix"/heat.exe dir ".tmp\release-64\j2re-image" -gg -cg ApplicationFiles -ke -srd -dr DYNAMIC -var var.foo -sfrag -o WindowsInstallerSettingsJREFiles.wxs &&
chmod -R 777 . &&
"$wix"/candle.exe -d"foo=.tmp\release-64\j2re-image" WindowsInstallerSettingsJREFiles.wxs &&
chmod -R 777 . &&
"$wix"/candle.exe -d"ProductVersion=$version" -d"ProductUpgradeCode=$(uuidgen)" WindowsInstallerSettingsJRE.wxs &&
chmod -R 777 . &&
"$wix"/light.exe WindowsInstallerSettingsJRE.wixobj WindowsInstallerSettingsJREFiles.wixobj -o AntTracksJRE-$version.msi &&
chmod -R 777 . &&

"$wix"/heat.exe dir ".tmp\release-64\j2sdk-image" -gg -cg ApplicationFiles -ke -srd -dr DYNAMIC -var var.foo -sfrag -o WindowsInstallerSettingsJDKFiles.wxs &&
chmod -R 777 . &&
"$wix"/candle.exe -d"foo=.tmp\release-64\j2sdk-image" WindowsInstallerSettingsJDKFiles.wxs &&
chmod -R 777 . &&
"$wix"/candle.exe -d"ProductVersion=$version" -d"ProductUpgradeCode=$(uuidgen)" WindowsInstallerSettingsJDK.wxs &&
chmod -R 777 . &&
"$wix"/light.exe WindowsInstallerSettingsJDK.wixobj WindowsInstallerSettingsJDKFiles.wixobj -o AntTracksJDK-$version.msi
chmod -R 777 . &&
result=$?

rm -r .tmp

exit $result


