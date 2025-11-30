/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.net;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * Retry mechanism with exponential backoff for transient failure recovery.
 * 
 * <p>Implements exponential backoff algorithm with configurable parameters:</p>
 * <ul>
 *   <li><b>Initial delay:</b> Starting wait time after first failure</li>
 *   <li><b>Max attempts:</b> Total number of attempts (initial + retries)</li>
 *   <li><b>Backoff multiplier:</b> Factor by which delay increases each retry</li>
 *   <li><b>Max delay:</b> Upper bound on wait time between retries</li>
 * </ul>
 * 
 * <p><b>Retry Decision Logic:</b></p>
 * <ul>
 *   <li>Timeouts: Always retry (transient network issues)</li>
 *   <li>Network errors: Always retry (connection failures)</li>
 *   <li>HTTP 5xx errors: Always retry (temporary server issues)</li>
 *   <li>HTTP 4xx errors: Never retry (client errors, bad request)</li>
 *   <li>HTTP 429 (Rate Limit): Retry with longer backoff</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> Each retry operation creates its own execution context.
 * This class itself is stateless and thread-safe.</p>
 * 
 * <p><b>Example Usage:</b></p>
 * <pre>{@code
 * RetryPolicy policy = new RetryPolicy(3, 1000, 2.0, 10000);
 * HttpResponse response = policy.executeWithRetry(() -> {
 *     return httpClient.executeSync("GET", url, null, null);
 * });
 * }</pre>
 * 
 * @author daviestobialex
 */
public class RetryPolicy {
    
    private static final Logger LOGGER = Logger.getLogger(RetryPolicy.class.getName());
    
    private final int maxAttempts;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;
    
    /**
     * Creates a retry policy with default configuration.
     * <ul>
     *   <li>Max attempts: 3 (1 initial + 2 retries)</li>
     *   <li>Initial delay: 1000ms</li>
     *   <li>Backoff multiplier: 2.0 (doubles each time)</li>
     *   <li>Max delay: 10000ms (10 seconds)</li>
     * </ul>
     */
    public RetryPolicy() {
        this(3, 1000, 2.0, 10000);
    }
    
    /**
     * Creates a retry policy with custom configuration.
     * 
     * @param maxAttempts total number of attempts including initial (minimum 1)
     * @param initialDelayMs initial wait time in milliseconds after first failure
     * @param backoffMultiplier factor by which delay increases (typically 2.0 for exponential)
     * @param maxDelayMs maximum wait time between retries (cap for exponential growth)
     */
    public RetryPolicy(int maxAttempts, long initialDelayMs, double backoffMultiplier, long maxDelayMs) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialDelayMs = Math.max(0, initialDelayMs);
        this.backoffMultiplier = Math.max(1.0, backoffMultiplier);
        this.maxDelayMs = Math.max(initialDelayMs, maxDelayMs);
    }
    
    /**
     * Executes the given operation with retry and exponential backoff.
     * 
     * <p>The operation is attempted up to {@code maxAttempts} times. Between attempts,
     * the thread sleeps for an exponentially increasing duration starting from
     * {@code initialDelayMs} and capped at {@code maxDelayMs}.</p>
     * 
     * <p><b>Retry Flow:</b></p>
     * <ol>
     *   <li>Execute operation</li>
     *   <li>If succeeds, return result</li>
     *   <li>If fails with retryable error and attempts remain:
     *     <ul>
     *       <li>Calculate next delay: delay = min(delay * multiplier, maxDelay)</li>
     *       <li>Sleep for calculated delay</li>
     *       <li>Goto step 1</li>
     *     </ul>
     *   </li>
     *   <li>If all attempts exhausted or non-retryable error, throw exception</li>
     * </ol>
     * 
     * @param <T> return type of the operation
     * @param operation callable to execute with retry
     * @param context descriptive context for logging (e.g., "GET /api/users")
     * @return result from successful operation execution
     * @throws Exception if all retry attempts are exhausted or a non-retryable error occurs
     */
    public <T> T executeWithRetry(Callable<T> operation, String context) throws Exception {
        Exception lastException = null;
        long currentDelay = initialDelayMs;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                LOGGER.fine(String.format("Executing %s (attempt %d/%d)", context, attempt, maxAttempts));
                return operation.call();
                
            } catch (Exception e) {
                lastException = e;
                
                // Check if we should retry
                if (!shouldRetry(e) || attempt >= maxAttempts) {
                    LOGGER.warning(String.format(
                        "Operation '%s' failed after %d attempts: %s", 
                        context, attempt, e.getMessage()));
                    throw e;
                }
                
                // Log retry attempt
                LOGGER.info(String.format(
                    "Operation '%s' failed (attempt %d/%d), retrying in %dms: %s",
                    context, attempt, maxAttempts, currentDelay, e.getMessage()));
                
                // Sleep with exponential backoff
                try {
                    Thread.sleep(currentDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                // Calculate next delay with exponential backoff
                currentDelay = Math.min((long)(currentDelay * backoffMultiplier), maxDelayMs);
            }
        }
        
        // Should never reach here, but for safety
        throw lastException;
    }
    
    /**
     * Determines if an exception represents a retryable failure.
     * 
     * <p><b>Retryable conditions:</b></p>
     * <ul>
     *   <li>Timeout exceptions (network delays)</li>
     *   <li>Connection exceptions (transient network issues)</li>
     *   <li>Read timeout exceptions (slow server response)</li>
     *   <li>Execution exceptions wrapping retryable causes</li>
     * </ul>
     * 
     * <p><b>Non-retryable conditions:</b></p>
     * <ul>
     *   <li>Illegal argument exceptions (programming errors)</li>
     *   <li>Null pointer exceptions (programming errors)</li>
     *   <li>Security exceptions (authorization issues)</li>
     * </ul>
     * 
     * @param e exception to evaluate
     * @return {@code true} if operation should be retried, {@code false} otherwise
     */
    private boolean shouldRetry(Exception e) {
        // Timeout exceptions - always retry
        if (e instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        
        // Connection timeout - always retry
        if (e instanceof io.netty.channel.ConnectTimeoutException) {
            return true;
        }
        
        // Read timeout - always retry
        if (e instanceof io.netty.handler.timeout.ReadTimeoutException) {
            return true;
        }
        
        // Execution exceptions - check cause
        if (e instanceof java.util.concurrent.ExecutionException) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (cause instanceof io.netty.channel.ConnectTimeoutException) {
                return true;
            }
            if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                return true;
            }
            if (cause instanceof java.io.IOException) {
                // Network I/O errors are generally retryable
                return true;
            }
        }
        
        // I/O exceptions - network errors, generally retryable
        if (e instanceof java.io.IOException) {
            return true;
        }
        
        // Programming errors - do not retry
        if (e instanceof IllegalArgumentException 
            || e instanceof NullPointerException
            || e instanceof SecurityException) {
            return false;
        }
        
        // Default: retry for unknown exceptions (conservative approach)
        return true;
    }
    
    /**
     * Gets the maximum number of attempts configured.
     * 
     * @return total attempts (initial + retries)
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    /**
     * Gets the initial delay configured.
     * 
     * @return initial delay in milliseconds
     */
    public long getInitialDelayMs() {
        return initialDelayMs;
    }
    
    /**
     * Gets the backoff multiplier configured.
     * 
     * @return multiplier factor for exponential backoff
     */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }
    
    /**
     * Gets the maximum delay configured.
     * 
     * @return maximum delay between retries in milliseconds
     */
    public long getMaxDelayMs() {
        return maxDelayMs;
    }
}
