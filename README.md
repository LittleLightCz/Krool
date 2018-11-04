# Krool
Kotlin resource/object pool based on non-blocking coroutines. Useful for sharing expensive 
or limited by number resources in a concurrent environment (Kotlin 1.3 compatible). Can be also
used for limited concurrency running on a thread pool with a lot of threads. 

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

## Example use case

Let's imagine you're going to write an application that needs to access the database,
but only from time to time. Therefore you probably don't want to use any persistence framework
for this, but instead you will open the DB connection only when it's needed and close it
immediately after the required job is done. Moreover since you want to run a lot of SQLs
on that DB, you want to make it more performant by using multiple DB connections at once,
however there is a limit on the number of connections you can (want to) have.

To accomplish this, you will need some kind of Object pool. Moreover even when creating such
pool, there are a few more problems to deal with. First, the connecting to the database itself
takes some time, so you don't want to do this sequentially, right? Second, some or all of these
connection attempts could fail for various reasons.
 
To address these issues, `Krool` has a nice builder function to help you handle both at once:

```kotlin
val dbPool = krool(5) { Db() }
```
This code will asynchronously create a pool of 5 instances of your (fictional) `Db` class, where
the initialization itself runs on `IO` pool by default, which you can change by specifying
the second parameter if you need to.

If there were any errors during the initialization, the pool will still silently instantiate 
with the instances that succeeded, where all thrown Exceptions are being stored in the 
`initErrors` property:
```kotlin
dbPool.initErrors
```

However if all connections fail, the Exception will be thrown right away.

In our example we would like to use the database connection to fetch a few URLs according 
to ID, therefore our `Db` class could look like this:

```kotlin
class Db {
    init {
        println("Connecting to database ...")
        Thread.sleep(1000)
    }

    fun getUrls(id: Int): List<String> {
        println("Selecting URLs from database for ID $id on ${Thread.currentThread().name}")
        Thread.sleep(1000)
        return (1..2).map { "http://urlservice.com/id/$id/page/$it" }
    }
    
    fun close() {
        println("Closing Db connection")
    }    
}
```

After we select those URLs, we just print them out. Note that if you're running blocking
operations you should always launch them on `Dispatchers.IO`, so our final code could
look like this:

```kotlin
fun main() = runBlocking {
    val dbPool = krool(5) { Db() }

    try {
        val ids = (1..10)

        val urls = ids.map { id ->
            async(Dispatchers.IO) {
                dbPool.use { db -> db.getUrls(id) }
            }
        }.awaitAll().flatten()

        println("Fetched URLs:")
        println(urls.joinToString("\n"))
    } finally {
        dbPool.closeWith { it.close() }
    }

    kroolContext.close()
}
```

Since all `Krool` instances share the same `kroolContext`, don't forget to close it before
your application exits to avoid thread hanging. Also note that although `Dispatchers.IO` 
has a lot more threads available, there are max 5 parallel URL selects happening at the 
same time.

The output is:
```
Connecting to database ...
Connecting to database ...
Connecting to database ...
Connecting to database ...
Connecting to database ...
Selecting URLs from database for ID 8 on DefaultDispatcher-worker-14
Selecting URLs from database for ID 4 on DefaultDispatcher-worker-8
Selecting URLs from database for ID 6 on DefaultDispatcher-worker-7
Selecting URLs from database for ID 1 on DefaultDispatcher-worker-2
Selecting URLs from database for ID 9 on DefaultDispatcher-worker-9
Selecting URLs from database for ID 5 on DefaultDispatcher-worker-9
Selecting URLs from database for ID 2 on DefaultDispatcher-worker-1
Selecting URLs from database for ID 7 on DefaultDispatcher-worker-4
Selecting URLs from database for ID 3 on DefaultDispatcher-worker-2
Selecting URLs from database for ID 10 on DefaultDispatcher-worker-7
Fetched URLs:
http://urlservice.com/id/1/page/1
http://urlservice.com/id/1/page/2
http://urlservice.com/id/2/page/1
http://urlservice.com/id/2/page/2
http://urlservice.com/id/3/page/1
http://urlservice.com/id/3/page/2
http://urlservice.com/id/4/page/1
http://urlservice.com/id/4/page/2
http://urlservice.com/id/5/page/1
http://urlservice.com/id/5/page/2
http://urlservice.com/id/6/page/1
http://urlservice.com/id/6/page/2
http://urlservice.com/id/7/page/1
http://urlservice.com/id/7/page/2
http://urlservice.com/id/8/page/1
http://urlservice.com/id/8/page/2
http://urlservice.com/id/9/page/1
http://urlservice.com/id/9/page/2
http://urlservice.com/id/10/page/1
http://urlservice.com/id/10/page/2
Closing Db connection
Closing Db connection
Closing Db connection
Closing Db connection
Closing Db connection
```