/* 
 * Copyright (c) 2014, fiLLLip <filip at tomren.it>
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
package net.brennheit.mcashapi;

import com.google.api.client.http.GenericUrl;

/**
 *
 * @author fiLLLip <filip at tomren.it>
 */
public class MCashUrl extends GenericUrl {

    public MCashUrl(String encodedUrl) {
        super(encodedUrl);
    }

    private static String baseUrl = "https://api.mca.sh/merchant/v1";

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static void setBaseUrl(String baseUrl) {
        MCashUrl.baseUrl = baseUrl;
    }

    public static MCashUrl PaymentRequest() {
        String url = String.format("%s/payment_request/", baseUrl);
        return new MCashUrl(url);
    }

    public static MCashUrl PaymentRequest(String tid) {
        String url = String.format("%s/payment_request/%s/", baseUrl, tid);
        return new MCashUrl(url);
    }

    public static MCashUrl PaymentRequestOutcome(String tid) {
        String url = String.format("%s/payment_request/%s/outcome/", baseUrl, tid);
        return new MCashUrl(url);
    }

    public static MCashUrl Shortlink() {
        String url = String.format("%s/shortlink/", baseUrl);
        return new MCashUrl(url);
    }

    public static MCashUrl Shortlink(String shortlinkId) {
        String url = String.format("%s/shortlink/%s/", baseUrl, shortlinkId);
        return new MCashUrl(url);
    }

    public static MCashUrl ShortlinkLastScan(String shortlinkId) {
        String url = String.format("%s/shortlink/%s/last_scan/", baseUrl, shortlinkId);
        return new MCashUrl(url);
    }

    public static MCashUrl ShortlinkLastScan(String shortlinkId, long ttl) {
        String url = String.format("%s/shortlink/%s/last_scan/?ttl=%s", baseUrl, shortlinkId, ttl);
        return new MCashUrl(url);
    }

}
