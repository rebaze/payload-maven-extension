# payload-maven-extension

This Maven Core Extension is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)

[![Build Status](https://travis-ci.org/rebaze/payload-maven-extension.svg?branch=master)](https://travis-ci.org/rebaze/payload-maven-extension)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/payload-maven-extension/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/payload-maven-extension)

# Installation
This extension will probably work with Maven starting version 3.2.5.
Since this is a Maven Core Extension, you must either install it directly into MavenHome/lib/ext or install it into [extensions.xml](http://takari.io/2015/03/19/core-extensions.html) (starting Maven 3.3.1).

It is recommended to use Maven 3.3.1+ and configure the extension under file `yourprojectfolder/.mvn/extensions.xml` like so:

      <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
          <extension>
              <groupId>com.rebaze.maven</groupId>
              <artifactId>payload-maven-extension</artifactId>
              <version>0.2.0</version>
          </extension>
      </extensions>


# Usage
When installed, every maven build will produce a single file: `target/build.payload` in the reactor project listing all artifacts requested by that build.

Example output in file `target/build.payload`

    antlr:antlr:jar:2.7.2
    antlr:antlr:jar:sources:2.7.2
    antlr:antlr:pom:2.7.2
    antlr:antlr:pom:2.7.7
    aopalliance:aopalliance:jar:sources:1.0
    ..

The output may be used for analytical purpose or picked up by other tooling like [payload-maven-plugin:deploy](https://github.com/rebaze/payload-maven-plugin)

