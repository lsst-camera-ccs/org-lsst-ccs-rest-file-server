java -agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/jersey/ -cp 'target/distribution/share/java/*' org.lsst.ccs.rest.file.server.cli.TopLevelCommand ls
