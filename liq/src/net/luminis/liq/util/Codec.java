package net.luminis.liq.util;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class Codec {
    
    public static String decode(String source) throws IllegalArgumentException {
        StringBuffer result = new StringBuffer();
        StringCharacterIterator sci = new StringCharacterIterator(source);
        for (char c = sci.current(); c != CharacterIterator.DONE; c = sci.next()) {
            if (c == '$') {
                c = sci.next();
                if (c != CharacterIterator.DONE) {
                    if (c == '$') {
                        result.append('$');
                    }
                    else if (c == 'k') {
                        result.append(',');
                    }
                    else if (c == 'n') {
                        result.append('\n');
                    }
                    else if (c == 'r') {
                        result.append('\r');
                    }
                    else {
                        throw new IllegalArgumentException("Unknown escape character: " + c);
                    }
                }
                else {
                    throw new IllegalArgumentException("Unexpected end of input: " + source);
                }
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    }
    
    public static String encode(String source) {
        StringBuffer result = new StringBuffer();
        StringCharacterIterator sci = new StringCharacterIterator(source);
        for (char c = sci.current(); c != CharacterIterator.DONE; c = sci.next()) {
            if (c == '$') {
                result.append("$$");
            }
            else if (c == ',') {
                result.append("$k");
            }
            else if (c == '\n') {
                result.append("$n");
            }
            else if (c == '\r') {
                result.append("$r");
            }
            else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
