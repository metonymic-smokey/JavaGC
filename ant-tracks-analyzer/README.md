# AntTracks Analyzer

The AntTracks Analyzer tool helps visualize and analyze the trace files from the AntTracks JVM.

This is modified from the [original source](#source) to fix some build issues and to add a CLI that can export data to JSON files without needing the GUI (hence can be automated).

## Requirements

<!-- TODO: ubuntu specific requirements - azul zulu -->
 - JDK 8 with JavaFX

For Ubuntu 20.04, the JDK+OpenFX from `apt` (default repositories) does not work. Instead, use [Azul Zulu OpenJDK 8 with JavaFX](https://www.azul.com/downloads/?version=java-8-lts&os=ubuntu&architecture=x86-64-bit&package=jdk-fx) (scroll to the bottom) - download the `.deb` file and install it.

## Build

<!-- TODO: Build steps -->

```
./gradlew jar
```

## Run

<!-- TODO -->

# Source

 - [AntTracks](http://mevss.jku.at/?page_id=1592)
 - [Source code (zip file)](https://ssw.jku.at/General/Staff/Weninger/AntTracks/Publish/Tool/anttracks-tool-source-3.1.0.0.zip)
