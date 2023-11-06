### Modified copy of the [OSPREY3 package](https://github.com/donaldlab/OSPREY3)
I've slightly modified the original source code for my needs. These changes include:
1. added forcefield parameters for a small molecule substrate (residue name AC6), NADPH (residue name NDQ), and Mg2+ ions

#### To use this package
Clone this repo to a local directory. Install java 14 SDK (openjdk) if you haven't already. Then, use gradlew to compile the java and activate the Python sources: 
```
./gradlew compileJava && ./gradlew pythonDevelop
``` 
If you get an error about the JAVA_HOME variable, then comment-out the `export JAVA_HOME=` line from your shell configuration file, close and open a new terminal (`source config_file` will _not_ work here), then try again. You may need to un-comment the `export JAVA_HOME=` line before you try to run OSPREY!

If, for some reason, the compilation fails. Try restarting your machine. This has worked before.
