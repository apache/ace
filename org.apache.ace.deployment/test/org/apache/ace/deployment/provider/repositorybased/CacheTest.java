/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.deployment.provider.repositorybased;

import org.testng.Assert;
import org.testng.annotations.Test;

public class CacheTest {
	@Test()
	public void testFillCacheToLimitAndCheckIfEverythingFits() {
		LRUMap<String, String> map = new LRUMap<>();
		for (int i = 0; i < 1024; i++) {
			String key = "" + i;
			map.put(key, key);
		}
		for (int i = 0; i < 1024; i++) {
			String key = "" + i;
			Assert.assertEquals(map.get(key), key);
		}
	}

	@Test()
	public void testOverflowCacheAndValidateOldestElementDisappears() {
		LRUMap<String, String> map = new LRUMap<>();
		// add one too many
		for (int i = 0; i < 1025; i++) {
			String key = "" + i;
			map.put(key, key);
		}
		// retrieve in same order (first one should be gone)
		for (int i = 0; i < 1025; i++) {
			String key = "" + i;
			if (i == 0) {
				Assert.assertNull(map.get(key));
			}
			else {
				Assert.assertEquals(map.get(key), key);
			}
		}
		// access the second one
		map.get("1");
		// add another one
		String key = "1025";
		map.put(key, key);
		// make sure the third is gone now
		Assert.assertNull(map.get("2"));
	}
}
