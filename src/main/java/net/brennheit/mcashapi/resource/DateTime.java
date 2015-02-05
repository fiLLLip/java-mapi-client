/*
 * Copyright (c) 2014, fiLLLip <filip@tomren.it>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.brennheit.mcashapi.resource;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author fiLLLip <filip@tomren.it>
 */
public final class DateTime implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    /**
     * Regular expression for parsing RFC3339 date/times.
     */
    private static final Pattern MCASH_DATETIME_PATTERN = Pattern.compile(
            "^(\\d{4})-(\\d{2})-(\\d{2})" // yyyy-MM-dd
            + "( (\\d{2}):(\\d{2}):(\\d{2}))?"); // 'T'HH:mm:ss.milliseconds

    /**
     * Date/time value expressed as the number of ms since the Unix epoch.
     *
     * <p>
     * If the time zone is specified, this value is normalized to UTC, so to
     * format this date/time value, the time zone shift has to be applied.
     * </p>
     */
    private final long value;

    /**
     * Specifies whether this is a date-only value.
     */
    private final boolean dateOnly;

    /**
     * Instantiates {@link DateTime} from the number of milliseconds since the
     * Unix epoch.
     *
     * <p>
     * The time zone is interpreted as {@code TimeZone.getDefault()}, which may
     * vary with implementation.
     * </p>
     *
     * @param value number of milliseconds since the Unix epoch (January 1,
     * 1970, 00:00:00 GMT)
     */
    public DateTime(long value) {
        this(false, value);
    }

    /**
     * Instantiates {@link DateTime} from a {@link Date}.
     *
     * <p>
     * The time zone is interpreted as {@code TimeZone.getDefault()}, which may
     * vary with implementation.
     * </p>
     *
     * @param value date and time
     */
    public DateTime(Date value) {
        this(value.getTime());
    }

    /**
     * Instantiates {@link DateTime}, which may represent a date-only value,
     * from the number of milliseconds since the Unix epoch, and a shift from
     * UTC in minutes.
     *
     * @param dateOnly specifies if this should represent a date-only value
     * @param value number of milliseconds since the Unix epoch (January 1,
     * 1970, 00:00:00 GMT)
     */
    public DateTime(boolean dateOnly, long value) {
        this.dateOnly = dateOnly;
        this.value = value;
    }

    /**
     * Instantiates {@link DateTime} from an <a
     * href='http://tools.ietf.org/html/rfc3339'>RFC 3339</a>
     * date/time value.
     *
     * <p>
     * Upgrade warning: in prior version 1.17, this method required milliseconds
     * to be exactly 3 digits (if included), and did not throw an exception for
     * all types of invalid input values, but starting in version 1.18, the
     * parsing done by this method has become more strict to enforce that only
     * valid RFC3339 strings are entered, and if not, it throws a
     * {@link NumberFormatException}. Also, in accordance with the RFC3339
     * standard, any number of milliseconds digits is now allowed.
     * </p>
     *
     * @param value an <a href='http://tools.ietf.org/html/rfc3339'>RFC 3339</a>
     * date/time value.
     * @since 1.11
     */
    public DateTime(String value) {
        // Note, the following refactoring is being considered: Move the implementation of parseRfc3339
        // into this constructor. Implementation of parseRfc3339 can then do
        // "return new DateTime(str);".
        DateTime dateTime = parseMCashFormat(value);
        this.dateOnly = dateTime.dateOnly;
        this.value = dateTime.value;
    }

    /**
     * Returns the date/time value expressed as the number of milliseconds since
     * the Unix epoch.
     *
     * <p>
     * If the time zone is specified, this value is normalized to UTC, so to
     * format this date/time value, the time zone shift has to be applied.
     * </p>
     *
     * @since 1.5
     */
    public long getValue() {
        return value;
    }

    /**
     * Returns whether this is a date-only value.
     *
     * @since 1.5
     */
    public boolean isDateOnly() {
        return dateOnly;
    }

    /**
     * Returns the time zone shift from UTC in minutes or {@code 0} for
     * date-only value.
     *
     * @since 1.5
     */
    public int getTimeZoneShift() {
        return 0; //This is always UTC
    }

    /**
     * Formats the value as an mCASH UTC date and time format string.
     */
    public String toStringMCashDateTime() {
        StringBuilder sb = new StringBuilder();
        Calendar dateTime = new GregorianCalendar(GMT);
        long localTime = value;
        dateTime.setTimeInMillis(localTime);
        // date
        appendInt(sb, dateTime.get(Calendar.YEAR), 4);
        sb.append('-');
        appendInt(sb, dateTime.get(Calendar.MONTH) + 1, 2);
        sb.append('-');
        appendInt(sb, dateTime.get(Calendar.DAY_OF_MONTH), 2);
        if (!dateOnly) {
            // time
            sb.append(' ');
            appendInt(sb, dateTime.get(Calendar.HOUR_OF_DAY), 2);
            sb.append(':');
            appendInt(sb, dateTime.get(Calendar.MINUTE), 2);
            sb.append(':');
            appendInt(sb, dateTime.get(Calendar.SECOND), 2);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return toStringMCashDateTime();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * A check is added that the time zone is the same. If you ONLY want to
     * check equality of time value, check equality on the {@link #getValue()}.
     * </p>
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DateTime)) {
            return false;
        }
        DateTime other = (DateTime) o;
        return dateOnly == other.dateOnly && value == other.value;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new long[]{value, dateOnly ? 1 : 0});
    }

    /**
     * Parses an mCASH UTC date and time value.
     *
     * @param str Date/time string in mCASH UTC date and time format
     * @throws NumberFormatException if {@code str} doesn't match the mCASH UTC date and time
     * format; an exception is thrown if {@code str} doesn't match
     * {@code MCASH_DATETIME_PATTERN}.
     */
    public static DateTime parseMCashFormat(String str) throws NumberFormatException {
        Matcher matcher = MCASH_DATETIME_PATTERN.matcher(str);
        if (!matcher.matches()) {
            throw new NumberFormatException("Invalid date/time format: " + str);
        }

        int year = Integer.parseInt(matcher.group(1)); // yyyy
        int month = Integer.parseInt(matcher.group(2)) - 1; // MM
        int day = Integer.parseInt(matcher.group(3)); // dd
        boolean isTimeGiven = matcher.group(4) != null; // HH:mm:ss.milliseconds
        int hourOfDay = 0;
        int minute = 0;
        int second = 0;

        if (!isTimeGiven) {
            throw new NumberFormatException("Invalid date/time format, cannot specify time zone shift"
                    + " without specifying time: " + str);
        }

        if (isTimeGiven) {
            hourOfDay = Integer.parseInt(matcher.group(5)); // HH
            minute = Integer.parseInt(matcher.group(6)); // mm
            second = Integer.parseInt(matcher.group(7)); // ss
        }
        Calendar dateTime = new GregorianCalendar(GMT);
        dateTime.set(year, month, day, hourOfDay, minute, second);
        long value = dateTime.getTimeInMillis();

        return new DateTime(!isTimeGiven, value);
    }

    /**
     * Appends a zero-padded number to a string builder.
     */
    private static void appendInt(StringBuilder sb, int num, int numDigits) {
        if (num < 0) {
            sb.append('-');
            num = -num;
        }
        int x = num;
        while (x > 0) {
            x /= 10;
            numDigits--;
        }
        for (int i = 0; i < numDigits; i++) {
            sb.append('0');
        }
        if (num != 0) {
            sb.append(num);
        }
    }
}
