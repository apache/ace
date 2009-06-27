package net.luminis.liq.repository;

/**
 * Class that captures a simple, modifiable range.
 */
public class Range {
    private long m_low;
    private long m_high;
    
    public Range(String representation) {
        int i = representation.indexOf('-');
        if (i == -1) {
            m_low = m_high = Long.parseLong(representation);
        }
        else {
            long low = Long.parseLong(representation.substring(0, i));
            long high = Long.parseLong(representation.substring(i + 1));
            if (low <= high) {
                m_low = low;
                m_high = high;
            }
            else {
                throw new IllegalArgumentException("illegal range");
            }
        }
    }
    
    public Range(long number) {
        m_low = m_high = number;
    }
    
    public Range(long low, long high) {
        if (low <= high) {
            m_low = low;
            m_high = high;
        }
        else {
            throw new IllegalArgumentException("illegal range");
        }
    }
    
    public long getLow() {
        return m_low;
    }
    
    public void setLow(long low) {
        m_low = low;
        if (m_high < m_low) {
            m_high = m_low;
        }
    }

    public long getHigh() {
        return m_high;
    }
    
    public void setHigh(long high) {
        m_high = high;
        if (m_low > m_high) {
            m_low = m_high;
        }
    }
    
    public boolean contains(long number) {
        return (m_low <= number) && (m_high >= number);
    }
    
    public String toRepresentation() {
        if (m_low == m_high) {
            return Long.toString(m_low);
        }
        else {
            return Long.toString(m_low) + '-' + Long.toString(m_high);
        }
    }
}
