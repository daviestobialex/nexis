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

package org.nexis.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.nexis.base.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stream Utilities. Bitcoinj is moving towards functional-style programming, immutable data structures, and
 * unmodifiable lists. Since we are currently limited to Java 8, this class contains utility methods that can simplify
 * code in many places.
 */
public class StreamUtils {
   /**
     * Return a collector that collects a {@link Stream} into an unmodifiable list.
     * <p>
     * Java 10 provides {@code Collectors.toUnmodifiableList()} and Java 16 provides {@code Stream.toList()}.
     * If those are not available, use this utility method.
     * @param <T>
     * @return 
     */
    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
    }
    
    /**
     * Max initial size of variable length arrays and ArrayLists that could be attacked.
     * Avoids this attack: Attacker sends a msg indicating it will contain a huge number (e.g. 2 billion) elements (e.g. transaction inputs) and
     * forces bitcoinj to try to allocate a huge piece of the memory resulting in OutOfMemoryError.
    */
    public static final int MAX_INITIAL_ARRAY_LENGTH = 20;

    private static final Logger log = LoggerFactory.getLogger(StreamUtils.class);

    public static String toString(List<byte[]> stack) {
        List<String> parts = new ArrayList<>(stack.size());
        for (byte[] push : stack)
            parts.add('[' + ByteUtils.formatHex(push) + ']');
        return InternalUtils.SPACE_JOINER.join(parts);
    }
}
