package org.openkilda.functionaltests.helpers

import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.junit.runners.model.MultipleFailureException

@Slf4j
class Wrappers {

    /**
     * Retries some operation for a given number of times. Retry is considered failed if any throwable is thrown.
     *
     * @param times number of retries
     * @param retryInterval pause between retries (in seconds)
     * @param handler exception handler (receives exception on each failure)
     * @param body operation to wrap around
     * @return result of the body execution
     * @throws MultipleFailureException if closure fails to execute in given amount of tries.
     */
    def static retry(int times = 5, double retryInterval = 2, Closure handler = { e -> log.debug("retry failed", e) },
            Closure body) {
        int retries = 0

        List<Throwable> ex = []
        while (retries++ < times) {
            try {
                return body.call()
            } catch (Throwable t) {
                ex.add(t)
                handler.call(t)
                sleep(retryInterval * 1000 as long)
            }
        }
        throw new MultipleFailureException(ex)
    }

    /**
     * Wait for closure to return a result.
     *
     * @param timeout time to wait in seconds
     * @param retryInterval pause between retries (in seconds)
     * @param closure : A closure that returns following:
     *          - Groovy truth or null if check is successful
     *          - False or throw if check is unsuccessful
     * @return True if wait was successful, throws WaitTimeoutException otherwise
     */
    static boolean wait(double timeout, double retryInterval = 0.5, Closure closure) {
        long endTime = System.currentTimeMillis() + (long) (timeout * 1000)
        long sleepTime = (long) (retryInterval * 1000)
        def thrown
        while (System.currentTimeMillis() < endTime) {
            try {
                def result = closure.call()
                if (result || result == null) {
                    return true
                } else {
                    thrown = null
                    sleep(sleepTime)
                }
            } catch (Throwable t) {
                thrown = t
                sleep(sleepTime)
            }
        }
        def message = "Condition was not satisfied within $timeout seconds"
        if (thrown) {
            message += ". Failed with exception: $thrown"
        }
        throw new WaitTimeoutException(message)
    }

    /**
     * Debug only. Logs how much time certain code block took to execute
     *
     * @param name some name of closure/code block that is being executed
     * @param closure code block to benchmark
     * @return result of the given closure
     */
    static def benchmark(name, closure) {
        def start = System.currentTimeMillis()
        def result = closure.call()
        def now = System.currentTimeMillis()
        log.debug("$name took " + (now - start) + "ms")
        return result
    }

    /**
     * Execute some code silently. No exceptions will be thrown. Any exception will be only logged.
     * Useful for cleanup blocks where you're not allowed to throw.
     *
     * @param closure
     */
    static void silent(closure) {
        try {
            closure.call()
        } catch (Throwable t) {
            log.error("", t)
        }
    }

    @InheritConstructors
    static class WaitTimeoutException extends RuntimeException {}
}
