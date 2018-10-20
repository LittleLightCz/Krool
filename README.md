# Krool
Kotlin resource pool based on non-blocking coroutines. Useful for sharing expensive 
resources in a concurrent environment. (Kotlin 1.3 compatible)

## Gradle

```groovy
repositories {
    jcenter()
}

dependencies {
    compile 'com.svetylkovo:krool:0.1.0'
}
```

## Maven

Add Jcenter repository:
```xml
<repositories>
    <repository>
      <id>jcenter</id>
      <url>https://jcenter.bintray.com/</url>
    </repository>
</repositories>
```

Add dependency:
```xml
<dependency>
  <groupId>com.svetylkovo</groupId>
  <artifactId>krool</artifactId>
  <version>0.1.0</version>
  <type>pom</type>
</dependency>
```

## Example use case (non-trivial)
### Introduction

Let's imagine you're going to write an application that needs to access the database,
but only from time to time. Therefore you probably don't want to use any persistence framework
for this, but instead you will open the DB connection only when it's needed and close it
immediately after the required job is done. Moreover since you want to run a lot of SQLs
on that DB, you want to make it more performant by using multiple DB connections at once,
however there is a limit on the number of connections you can (want to) have.

**You task is:** Given a bunch of IDs fetch the corresponding URLs from the DB, where one ID can have
multiple URLs and then use an Http client to fetch the "precious dots" from all those URLs and 
in the final print how many precious dots you have collected. The goal is to make this run 
fast enough (therefore using parallelism). 

To accomplish this, you will need some kind of Object pool. Moreover even when creating such
pool, there are a few more problems to deal with. First, the connecting to the database itself
takes some time, so you don't want to do this sequentially, right? Second, some or all of these
connection attempts could fail for various reasons. `Krool` has a nice builder function to help
you handle both at once.

E.g. to asynchronously create a pool of 5 instances of your (fictional) `Db` class, you do:
```kotlin
val dbPool = krool(5) { Db() }
```
*(Please note that the initialization runs on `IO` pool by default, which you can change by specifying
the second parameter if you need to)*

If there were any errors during the initialization, the pool will still silently instantiate 
with the instances that succeeded and the thrown Exceptions are being stored in the 
`initErrors` property:
```kotlin
dbPool.initErrors
```

However if all of the connections fail, the pool won't instantiate, but it will throw an
Exception instead.

### Implementation
Let's start by defining our fictional Db class which will simulate a select operation in the
database including the initial connection delay:

```kotlin
class Db {
    init {
        println("Connecting to database ...")
        Thread.sleep(1000)
    }

    fun getUrls(id: Int): List<String> {
        println("Selecting URLs from database for ID $id on ${Thread.currentThread().name}")
        Thread.sleep(1000)
        return (1..2).map { "http://idservice.com/id/$id/page/$it" }
    }
}
```



























Console output:
```
Connecting to database ...
Connecting to database ...
Connecting to database ...
Connecting to database ...
Initializing Http client ...
Initializing Http client ...
Initializing Http client ...
Initializing Http client ...
Initializing Http client ...
Initializing Http client ...
Initializing Http client ...
Selecting URLs from database for ID 1 on DefaultDispatcher-worker-5
Selecting URLs from database for ID 5 on DefaultDispatcher-worker-3
Selecting URLs from database for ID 7 on DefaultDispatcher-worker-7
Selecting URLs from database for ID 6 on DefaultDispatcher-worker-11
Selecting URLs from database for ID 8 on DefaultDispatcher-worker-4
Selecting URLs from database for ID 4 on DefaultDispatcher-worker-10
Selecting URLs from database for ID 10 on DefaultDispatcher-worker-12
Selecting URLs from database for ID 9 on DefaultDispatcher-worker-8
Fetching precious dot from http://idservice.com/id/7/page/2 on DefaultDispatcher-worker-7
Fetching precious dot from http://idservice.com/id/5/page/2 on DefaultDispatcher-worker-3
Fetching precious dot from http://idservice.com/id/6/page/2 on DefaultDispatcher-worker-11
Fetching precious dot from http://idservice.com/id/5/page/1 on DefaultDispatcher-worker-2
Fetching precious dot from http://idservice.com/id/7/page/1 on DefaultDispatcher-worker-14
Fetching precious dot from http://idservice.com/id/6/page/1 on DefaultDispatcher-worker-6
Fetching precious dot from http://idservice.com/id/1/page/1 on DefaultDispatcher-worker-5
Selecting URLs from database for ID 3 on DefaultDispatcher-worker-10
Selecting URLs from database for ID 2 on DefaultDispatcher-worker-12
Fetching precious dot from http://idservice.com/id/1/page/2 on DefaultDispatcher-worker-7
Fetching precious dot from http://idservice.com/id/10/page/2 on DefaultDispatcher-worker-13
Fetching precious dot from http://idservice.com/id/4/page/1 on DefaultDispatcher-worker-4
Fetching precious dot from http://idservice.com/id/10/page/1 on DefaultDispatcher-worker-14
Fetching precious dot from http://idservice.com/id/4/page/2 on DefaultDispatcher-worker-16
Fetching precious dot from http://idservice.com/id/9/page/1 on DefaultDispatcher-worker-12
Fetching precious dot from http://idservice.com/id/8/page/2 on DefaultDispatcher-worker-8
Fetching precious dot from http://idservice.com/id/9/page/2 on DefaultDispatcher-worker-12
Fetching precious dot from http://idservice.com/id/2/page/1 on DefaultDispatcher-worker-13
Fetching precious dot from http://idservice.com/id/2/page/2 on DefaultDispatcher-worker-15
Fetching precious dot from http://idservice.com/id/3/page/2 on DefaultDispatcher-worker-16
Fetching precious dot from http://idservice.com/id/3/page/1 on DefaultDispatcher-worker-4
Fetching precious dot from http://idservice.com/id/8/page/1 on DefaultDispatcher-worker-8
Collected 20 precious dots!
Closing com.svetylkovo.krool.examples.HttpClient@3fa77460
Closing com.svetylkovo.krool.examples.HttpClient@619a5dff
Closing com.svetylkovo.krool.examples.HttpClient@1ed6993a
Closing com.svetylkovo.krool.examples.HttpClient@7e32c033
Closing com.svetylkovo.krool.examples.HttpClient@7ab2bfe1
Closing com.svetylkovo.krool.examples.HttpClient@497470ed
Closing com.svetylkovo.krool.examples.HttpClient@63c12fb0
Closing com.svetylkovo.krool.examples.Db@b1a58a3
Closing com.svetylkovo.krool.examples.Db@6438a396
Closing com.svetylkovo.krool.examples.Db@e2144e4
Closing com.svetylkovo.krool.examples.Db@6477463f
```

