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
package org.apache.ace.client.rest;

import javax.servlet.http.HttpServletRequest;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class RESTClientTest {
	@Test
	public void testPathTransforms() {
		String path = "one/two/last%20path";
		RESTClientServlet s = new RESTClientServlet();
		HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
		Mockito.when(request.getPathInfo()).thenReturn(path);
		String[] elements = s.getPathElements(request);
		Assert.assertEquals(elements[0], "one");
		Assert.assertEquals(elements[1], "two");
		Assert.assertEquals(elements[2], "last path");
		String result = s.buildPathFromElements(elements);
		Assert.assertEquals(result, path);
	}}
