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
package org.nexis.store;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 
 * @author daviestobialex
 */
public class ManifestIndex implements Closeable {
    private final RandomAccessFile file;
    private final List<Entry> entries = new ArrayList<>(); // loaded in memory for fast lookup

    public ManifestIndex(File idxFile) throws IOException {
        this.file = new RandomAccessFile(idxFile, "rw");
        loadIndex();
    }

    public static class Entry {
        public final BigInteger cid;
        public final long offset;
        public final int length;

        public Entry(BigInteger cid, long offset, int length) {
            this.cid = cid;
            this.offset = offset;
            this.length = length;
        }
    }

    private void loadIndex() throws IOException {
        file.seek(0);
        while (file.getFilePointer() < file.length()) {
            byte[] cidBytes = new byte[32];
            file.readFully(cidBytes);
            BigInteger cid = new BigInteger(1, cidBytes);
            long offset = file.readLong();
            int length = file.readInt();
            entries.add(new Entry(cid, offset, length));
        }
        entries.sort(Comparator.comparing(e -> e.cid)); // keep sorted
    }

    public void addEntry(BigInteger cid, long offset, int length) throws IOException {
        Entry e = new Entry(cid, offset, length);
        entries.add(e);
        entries.sort(Comparator.comparing(x -> x.cid));
        saveIndex(); // naive: rewrite whole index (can optimize later)
    }

    public Entry find(BigInteger cid) {
        int i = Collections.binarySearch(entries, new Entry(cid, 0, 0),
                Comparator.comparing(e -> e.cid));
        return (i >= 0) ? entries.get(i) : null;
    }

    private void saveIndex() throws IOException {
        file.setLength(0);
        for (Entry e : entries) {
            byte[] cidBytes = toFixedBytes(e.cid, 32);
            file.write(cidBytes);
            file.writeLong(e.offset);
            file.writeInt(e.length);
        }
    }

    private byte[] toFixedBytes(BigInteger value, int size) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[size];
        System.arraycopy(raw, Math.max(0, raw.length - size), fixed,
                Math.max(0, size - raw.length), Math.min(size, raw.length));
        return fixed;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}