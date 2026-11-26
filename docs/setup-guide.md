# Setup Guide

## Requirements

- JDK 21
- Maven 3.x
- Git

## Verify installation

```bash
java -version
mvn -version
git --version
```

## Open the correct folder

Open the `ClassPulse/` folder itself in your IDE. It contains the parent `pom.xml`. If the IDE does not recognise the modules, reload Maven projects and refresh the Java language server.

## Build

Run from the directory containing the root `pom.xml`:

```bash
mvn clean install
```

Do not run Maven from the folder above `ClassPulse`, because that directory does not contain the project POM.

## Windows PowerShell example

```powershell
cd "D:\path\to\ClassPulse"
mvn clean install
```

## macOS / Linux example

```bash
cd /path/to/ClassPulse
mvn clean install
```

## IDE notes

- Set the project JDK to Java 21.
- Import/open the root `pom.xml` as a Maven project.
- If red diagnostics remain, inspect the actual Problems panel messages rather than assuming they are harmless.
- A successful Maven build confirms compilation; it does not validate runtime networking behaviour.

## Run configuration

The two main classes currently print starter messages only. Add run configurations after the NIO server and client have been implemented.
