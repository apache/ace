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
package org.apache.ace.client;

import org.apache.ace.client.Main.StatusHandler;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * Basic table for using a valueobject per row. Remember to call the constructor with 
 * the right colunm names.
 */
public abstract class ObjectTable<T> extends FlexTable {
    private final String[] m_columnNames;
    private final StatusHandler m_handler;

    public ObjectTable(StatusHandler handler, String... columnNames) {
        m_handler = handler;
        m_columnNames = columnNames;
        for (int i = 0; i < m_columnNames.length; i++) {
            setText(0, i, m_columnNames[i]);
        }
    }

    /**
     * Interprets the given value object for some column.
     */
    protected abstract String getValue(T object, int column);
    
    /**
     * Invokes the necessary service call to get the latest
     * set of value objects from the server, passing the given callback.
     */
    protected abstract void callService(AsyncCallback<T[]> callback);
    
    void updateTable() {
        callService(new AsyncCallback<T[]>() {
            public void onFailure(Throwable caught) {
                m_handler.handleFail(getClass());
            }
            public void onSuccess(T[] result) {
                m_handler.handleSuccess(getClass());
                int row = 1;
                for (T t : result) {
                    for (int i = 0; i < m_columnNames.length; i++) {
                        setText(row, i, getValue(t, i));
                    }
                    row++;
                }
                while (row < getRowCount()) {
                    // Looks like we removed something...
                    removeRow(row);
                }
            }
        });
    }
}
