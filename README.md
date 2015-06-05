# payload-maven-extension

This Maven Core Extension is available under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)

[![Build Status](https://travis-ci.org/rebaze/payload-maven-extension.svg?branch=master)](https://travis-ci.org/rebaze/payload-maven-extension)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/payload-maven-extension/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.rebaze.maven/payload-maven-extension)

# Installation
This extension will probably work with Maven starting version 3.2.5.
Since this is a Maven Core Extension, you must either install it directly into MavenHome/lib/ext or install it into [extensions.xml](http://takari.io/2015/03/19/core-extensions.html) (starting Maven 3.3.1).

# Usage
When installed, every maven build will produce a single build.payload file in target/ of the reactor project listing all artifacts requested by that build.
The output may be used for analytical purpose or picked up by other tooling like [payload-maven-plugin:deploy](https://github.com/rebaze/payload-maven-plugin)
