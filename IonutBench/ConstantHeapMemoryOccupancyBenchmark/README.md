## ConstantHeapMemoryOccupancyBenchmark 

### Instructions to run

1. `mvn archetype:generate -DinteractiveMode=false -DarchetypeGroupId=org.openjdk.jmh -DarchetypeArtifactId=jmh-java-benchmark-archetype -DgroupId=com.avenuecode.snippet -DartifactId=ConstantHeapMemoryOccupancyBenchmark -Dversion=1.0`  

2. `cd ConstantHeapMemoryOccupancyBenchmark`. This folder will have a `pom.xml` generated and the source directory(source directory and `pom.xml` added to this repo already).       

3. `mvn clean install` after adding the benchmark function in the Java file.     

4. `java -jar target/benchmarks.jar` 
