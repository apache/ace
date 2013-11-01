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
package org.apache.ace.webui.vaadin;

import java.util.Arrays;

import com.vaadin.event.ShortcutAction.ModifierKey;
import com.vaadin.terminal.gwt.server.WebBrowser;
import com.vaadin.ui.Button;

/**
 * Provides utility methods for creating shortcut listeners.
 */
public final class ShortcutHelper {

    public static void addCrossPlatformShortcut(WebBrowser browser, Button button, String description, int key, int... modifiers) {
        modifiers = Arrays.copyOf(modifiers, modifiers.length + 1);
        int platformModifier = getPlatformSpecificModifier(browser);
        if (browser.isMacOSX()) {
            modifiers[modifiers.length - 1] = platformModifier;
        }
        else {
            System.arraycopy(modifiers, 0, modifiers, 1, modifiers.length - 1);
            modifiers[0] = platformModifier;
        }
        addShortcut(browser, button, description, key, modifiers);
    }

    public static void addShortcut(WebBrowser browser, Button button, String description, int key, int... modifiers) {
        if (!browser.isTouchDevice()) {
            button.setClickShortcut(key, modifiers);
            button.setDescription(String.format("%s (%s)", description, formatShortcut(key, modifiers)));
        }
    }

    public static String formatModifier(int modifier) {
        if (ModifierKey.ALT == modifier) {
            return "ALT";
        }
        else if (ModifierKey.CTRL == modifier) {
            return "CTRL";
        }
        else if (ModifierKey.META == modifier) {
            return "\u2318";
        }
        else if (ModifierKey.SHIFT == modifier) {
            return "SHIFT";
        }
        return "";
    }

    public static String formatShortcut(int keycode, int... modifiers) {
        StringBuilder sb = new StringBuilder();
        for (int modifier : modifiers) {
            sb.append(formatModifier(modifier));
            if (modifier != ModifierKey.META) {
                sb.append(" + ");
            }
        }
        sb.append((char) keycode);
        return sb.toString();
    }

    public static int getPlatformSpecificModifier(WebBrowser browser) {
        if (browser.isMacOSX()) {
            return ModifierKey.META;
        }
        return ModifierKey.CTRL;
    }
}
