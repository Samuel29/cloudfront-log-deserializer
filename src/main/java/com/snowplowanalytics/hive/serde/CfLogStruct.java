/* 
 * Copyright (c) 2012 Orderly Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.hive.serde;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.serde2.SerDeException;

/**
 * CfLogStruct represents the Hive struct for a row in a CloudFront access log.
 * 
 * Contains a parse() method to perform an update-in-place for this instance
 * based on the current row's contents.
 * 
 * Constructor is empty because we do updates-in-place for performance reasons.
 * An immutable Scala case class would be nice but fear it would be s-l-o-w
 */
public class CfLogStruct {
	
	private static final Log log = LogFactory.getLog(CfLogStruct.class);

	// -------------------------------------------------------------------------------------------------------------------
	// Mutable properties for this Hive struct
	// -------------------------------------------------------------------------------------------------------------------

	public String dt;
	public String tm;
	public String edgelocation;
	public Integer bytessent;
	public String ipaddress;
	public String operation;
	public String domain;
	public String object;
	public Integer httpstatus;
	public String referrer;
	public String useragent;
	public String querystring;
	public String cookie;
	public String resulttype;
	public String requestid;
	public String hostheader;
	public String protocol;
	public Integer bytes;
	// var querymap: Map[String, String] TODO add this

	// -------------------------------------------------------------------------------------------------------------------
	// Static configuration
	// -------------------------------------------------------------------------------------------------------------------

	// Define the regular expression for extracting the fields
	// Adapted from Amazon's own cloudfront-loganalyzer.tgz
	private static final String w = "[\\s]+"; // Whitespace regex
	/* new log format, see https://forums.aws.amazon.com/ann.jspa?annID=2174 */
	private static final Pattern cfRegex =
	Pattern.compile("([\\S]+)" // Date / date
			+ w + "([\\S]+)" // Time / time
			+ w + "([\\S]+)" // EdgeLocation / x-edge-location
			+ w + "([\\S]+)" // BytesSent / sc-bytes
			+ w + "([\\S]+)" // IPAddress / c-ip
			+ w + "([\\S]+)" // Operation / cs-method
			+ w + "([\\S]+)" // Domain / cs(Host)
			+ w + "([\\S]+)" // Object / cs-uri-stem
			+ w + "([\\S]+)" // HttpStatus / sc-status
			+ w + "([\\S]+)" // Referrer / cs(Referer)
			+ w + "([\\S]+)" // UserAgent / cs(User Agent)
			+ w + "([\\S]+)" // Querystring / cs(Querystring)
			+ w + "([\\S]+)" // Cookie / cs(Cookie)
			+ w + "([\\S]+)" // ResultType / x-edge-result-type
			+ w + "([\\S]+)" // RequestId / x-edge-request-id
			+ w + "([\\S]+)" // HostHeader / x-host-header
			+ w + "([\\S]+)" // Protocol / cs-protocol
			+ w + "(.+)");   // Bytes / cs-bytes

	private static final Pattern cfRegex_before_2013_10_21 =
	Pattern.compile("([\\S]+)" // Date / date
			+ w + "([\\S]+)" // Time / time
			+ w + "([\\S]+)" // EdgeLocation / x-edge-location
			+ w + "([\\S]+)" // BytesSent / sc-bytes
			+ w + "([\\S]+)" // IPAddress / c-ip
			+ w + "([\\S]+)" // Operation / cs-method
			+ w + "([\\S]+)" // Domain / cs(Host)
			+ w + "([\\S]+)" // Object / cs-uri-stem
			+ w + "([\\S]+)" // HttpStatus / sc-status
			+ w + "([\\S]+)" // Referrer / cs(Referer)
			+ w + "([\\S]+)" // UserAgent / cs(User Agent)
			+ w + "([\\S]+)" // Querystring / cs(Querystring)
			+ w + "([\\S]+)" // Cookie / cs(Cookie)
			+ w + "([\\S]+)" // ResultType / x-edge-result-type
			+ w + "(.+)");   // RequestId / x-edge-request-id

	// -------------------------------------------------------------------------------------------------------------------
	// Deserialization logic
	// -------------------------------------------------------------------------------------------------------------------

	/**
	 * Parses the input row String into a Java object. For performance reasons
	 * this works in-place updating the fields within this CfLogStruct, rather
	 * than creating a new one.
	 * 
	 * @param row
	 *            The raw String containing the row contents
	 * @return This struct with all values updated
	 * @throws SerDeException
	 *             For any exception during parsing
	 */
	public Object parse(String row) throws SerDeException {

		// We have to handle any header rows
		if (row.startsWith("#Version:") || row.startsWith("#Fields:")) {
			return null; // Empty row will be discarded by Hive
		}

		Matcher matcher = cfRegex.matcher(row);

		try {
			// if the row is not matching the NEW log format, try the former one.
			if (!matcher.find()) {
				if (log.isDebugEnabled()) {
					log.debug("old log format");
				}
				matcher = cfRegex_before_2013_10_21.matcher(row);
				if (!matcher.find()) {
					throw new Exception("row didn't match either old or new patterns");
				}
			}
			this.dt = matcher.group(1);
			this.tm = matcher.group(2); // No need for toHiveDate any more -
								  // CloudFront date format matches Hive's
			this.edgelocation = matcher.group(3);
			this.bytessent = toInt(matcher.group(4));
			this.ipaddress = matcher.group(5);
			this.operation = matcher.group(6);
			this.domain = matcher.group(7);
			this.object = matcher.group(8);
			this.httpstatus = toInt(matcher.group(9));
			this.referrer = nullifyHyphen(matcher.group(10));
			this.useragent = matcher.group(11);
			this.querystring = nullifyHyphen(matcher.group(12));
			this.cookie = nullifyHyphen(matcher.group(13));
			this.resulttype = matcher.group(14);
			this.requestid = matcher.group(15);
			if (matcher.groupCount()>15) {
				this.hostheader = matcher.group(16);
				this.protocol = matcher.group(17);
				this.bytes = toInt(matcher.group(18));
			}
		} catch (Exception e) {
			throw new SerDeException("Could not parse row: \n" + row, e);
		}

		return this; // Return the CfLogStruct
	}

	// -------------------------------------------------------------------------------------------------------------------
	// Datatype conversions
	// -------------------------------------------------------------------------------------------------------------------

	/**
	 * Implicit conversion from String to Integer. To deal with the fact that
	 * AWS uses a single "-"" for null.
	 * 
	 * @param s
	 *            The String to check
	 * @return The Integer, or null if the String was "-"
	 */
	private Integer toInt(String s) {
		return (s.compareTo("-") == 0) ? null : Integer.valueOf(s);
	}

	/**
	 * Explicit conversion to turn a "-" String into null. Useful for "-" URIs
	 * (URI is set to "-" if e.g. S3 is accessed from a file:// protocol).
	 * 
	 * @param s
	 *            The String to check
	 * @return The original String, or null if the String was "-"
	 */
	private String nullifyHyphen(String s) {
		return (s.compareTo("-") == 0) ? null : s;
	}
}
