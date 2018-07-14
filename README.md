# Krool
Kotlin resource pool based on non-blocking coroutines. Useful for sharing expensive resources in a concurrent environment.

## Gradle

```groovy
repositories {
    maven { url "https://dl.bintray.com/littlelightcz/Krool" }
}

dependencies {
    compile 'com.svetylkovo:krool:0.0.1'
}
```

## Usage

```kotlin
class ExpensiveResource(val name: String) {
    suspend fun performExpensiveOperation(number: Int): String {
        delay(500)
        return "$name (result: $number)"
    }
}

object KroolExample {

    @JvmStatic
    fun main(args: Array<String>) {

        // Create expensive resources
        val resources = (1..5).map { ExpensiveResource("ExpensiveResource $it") }

        // Create a resource pool
        val pool = krool(resources) {
            delayInterval = 50 // Change delayInterval if needed
        }

        // Use it!
        runBlocking {
            (1..10).map { number ->
                async {
                    pool.use { it.performExpensiveOperation(number) }
                }
            }.forEach {
                println(it.await())
            }
        }
    }
}
```

Console output:
```
ExpensiveResource 1 (result: 1)
ExpensiveResource 3 (result: 2)
ExpensiveResource 2 (result: 3)
ExpensiveResource 4 (result: 4)
ExpensiveResource 5 (result: 5)
ExpensiveResource 1 (result: 6)
ExpensiveResource 2 (result: 7)
ExpensiveResource 5 (result: 8)
ExpensiveResource 3 (result: 9)
ExpensiveResource 4 (result: 10)
```

