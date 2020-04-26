/*
 * Copyright 2017-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.protocols.raft.storage.snapshot;

import io.atomix.storage.buffer.Buffer;
import io.atomix.storage.buffer.HeapBuffer;
import org.junit.Test;

import static io.atomix.protocols.raft.storage.snapshot.SnapshotDescriptor.VERSION_1;
import static io.atomix.protocols.raft.storage.snapshot.SnapshotDescriptor.VERSION_2;
import static io.atomix.protocols.raft.storage.snapshot.SnapshotDescriptor.VERSION_POSITION;
import static org.junit.Assert.assertEquals;

/**
 * Snapshot descriptor test.
 */
public class SnapshotDescriptorTest {

  @Test
  public void testSnapshotDescriptor() throws Exception {
    SnapshotDescriptor descriptor = SnapshotDescriptor.builder()
        .withIndex(2)
        .withTimestamp(3)
        .withTerm(4)
        .build();
    assertEquals(2, descriptor.index());
    assertEquals(3, descriptor.timestamp());
    assertEquals(4, descriptor.term());
    assertEquals(VERSION_2, descriptor.version());
  }

  @Test
  public void testCopySnapshotDescriptor() throws Exception {
    SnapshotDescriptor descriptor = SnapshotDescriptor.builder()
        .withIndex(2)
        .withTimestamp(3)
        .withTerm(4)
        .build();
    Buffer buffer = HeapBuffer.allocate(SnapshotDescriptor.BYTES);
    descriptor.copyTo(buffer);
    buffer.flip();
    descriptor = new SnapshotDescriptor(buffer);
    assertEquals(2, descriptor.index());
    assertEquals(3, descriptor.timestamp());
    assertEquals(4, descriptor.term());
    assertEquals(VERSION_2, descriptor.version());
  }

  @Test
  public void testReadOldSnapshot() {
    Buffer tmpBuffer = HeapBuffer.allocate(SnapshotDescriptor.BYTES);
    SnapshotDescriptor.builder()
            .withIndex(2)
            .withTimestamp(3)
            .build()
            .copyTo(tmpBuffer);
    tmpBuffer.writeInt(VERSION_POSITION, VERSION_1);
    tmpBuffer.flip();

    SnapshotDescriptor descriptor = new SnapshotDescriptor(tmpBuffer);
    Buffer buffer = HeapBuffer.allocate(SnapshotDescriptor.BYTES);
    descriptor.copyTo(buffer);
    buffer.flip();
    descriptor = new SnapshotDescriptor(buffer);
    assertEquals(2, descriptor.index());
    assertEquals(3, descriptor.timestamp());
    assertEquals(1, descriptor.term());
    assertEquals(VERSION_1, descriptor.version());
  }

}
