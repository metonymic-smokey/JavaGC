# ant-tracks-jvm

Sources and build files for the AntTracks JVM

## Usage

### Build container

This will take a while (30+ minutes on the first run, subsequent runs will used cached layers - 2-5 mins)
```
docker build -t ant-tracks-jvm .
```

### Build JVM

```
docker run -it ant-tracks-jvm
```

After building, find the built JVM at the following locations:
 - `dist/slowdebug-64/j2sdk-image/bin/java`

### Incremental build/caching

TODO: modify Dockerfile or find a way to build only the changes after modifying the JVM source code. This will useful for developing the JVM, as builds can take time (5-15 minutes).
