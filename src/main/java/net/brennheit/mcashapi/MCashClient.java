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

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.brennheit.mcashapi.types.*;

/**
 *
 * @author fiLLLip <filip at tomren.it>
 */
public class MCashClient {

    private HttpHeaders httpHeaders;
    private HttpRequestFactory requestFactory;
    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();
    static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#0.00", new DecimalFormatSymbols(Locale.ENGLISH));
    private final String posId;
    private final String ledger;

    /**
     *
     * @param baseUrl
     * @param merchantId
     * @param userId
     * @param authKey
     * @param authMethod
     * @param posId
     * @param ledger
     * @param testbedToken
     */
    public MCashClient(String baseUrl, String merchantId, String userId, String authKey, String authMethod, String posId, String ledger, String testbedToken) {
        MCashUrl.setBaseUrl(baseUrl);
        this.httpHeaders = MakeHeaders(merchantId, userId, authKey, authMethod, testbedToken);
        this.posId = posId;
        this.ledger = ledger;
        requestFactory = HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                request.setHeaders(httpHeaders);
                request.setParser(new JsonObjectParser(JSON_FACTORY));
            }
        });

    }

    /**
     * Tries to connect to REST WebService.
     * @return success
     */
    public boolean IsReady() {
        Socket socket = null;
        try {
            String hostname = (new URI(MCashUrl.getBaseUrl())).getHost();
            socket = new Socket(hostname, 80);
            return true;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        } catch (URISyntaxException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                }
            }
        }
        return false;
    }

    /**
     *
     * @param shortlinkId
     * @param ttl
     * @return
     */
    public ShortlinkLastScan GetShortLinkLastScan(String shortlinkId, long ttl) {
        MCashUrl url = MCashUrl.ShortlinkLastScan(shortlinkId, ttl);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = Request(request);
            ShortlinkLastScan lastScan = response.parseAs(ShortlinkLastScan.class);
            return lastScan;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     *
     * @param posTicketId
     * @param scanToken
     * @param amount
     * @param currency
     * @param additionalAmount
     * @param additionalAmountEdit
     * @param callbackUri
     * @return
     */
    public ResourceId CreatePaymentRequest(String posTicketId, String scanToken, double amount, String currency, double additionalAmount, boolean additionalAmountEdit, String callbackUri) {
        CreatePaymentRequest createPaymentRequest = new CreatePaymentRequest();
        createPaymentRequest.action = "SALE";
        createPaymentRequest.pos_id = this.posId;
        createPaymentRequest.pos_tid = posTicketId;
        createPaymentRequest.customer = scanToken;
        createPaymentRequest.currency = currency;
        createPaymentRequest.amount = MONEY_FORMAT.format(amount);
        createPaymentRequest.additional_amount = null;
        createPaymentRequest.additional_edit = false;
        createPaymentRequest.expires_in = 300;
        createPaymentRequest.ledger = this.ledger;
        if(callbackUri != null){
            createPaymentRequest.callback_uri = callbackUri;
        }
        if (additionalAmount > 0) {
            createPaymentRequest.additional_amount = MONEY_FORMAT.format(additionalAmount);
        } else {
            createPaymentRequest.additional_edit = additionalAmountEdit;
        }
        MCashUrl url = MCashUrl.PaymentRequest();
        try {
            HttpRequest request = requestFactory.buildPostRequest(url, BuildJsonContent(createPaymentRequest));
            HttpResponse response = Request(request);
            ResourceId resourceId = response.parseAs(ResourceId.class);
            return resourceId;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private void DoPaymentRequestAction(String ticketId, String action, String callbackUri){
        UpdatePaymentRequest updatePaymentRequest = new UpdatePaymentRequest();
        updatePaymentRequest.action = action;
        updatePaymentRequest.ledger = this.ledger;
        updatePaymentRequest.callback_uri = callbackUri;
        MCashUrl url = MCashUrl.PaymentRequest(ticketId);
        try {
            HttpRequest request = requestFactory.buildPutRequest(url, BuildJsonContent(updatePaymentRequest));
            Request(request);
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
    
    /**
     *
     * @param ticketId
     * @param callbackUri
     */
    public void AbortPaymentRequest(String ticketId, String callbackUri){
        DoPaymentRequestAction(ticketId, "abort", callbackUri);
    }
    
    /**
     *
     * @param ticketId
     * @param callbackUri
     */
    public void CapturePaymentRequest(String ticketId, String callbackUri){
        DoPaymentRequestAction(ticketId, "capture", callbackUri);
    }
    
    /**
     *
     * @param serialNumber
     * @param callbackUri
     * @return
     */
    public Shortlink CreateShortlink(String serialNumber, String callbackUri){
        Shortlink shortlink = new Shortlink();
        shortlink.callback_uri = callbackUri;
        shortlink.serial_number = serialNumber;
        MCashUrl url = MCashUrl.Shortlink();
        try {
            HttpRequest request = requestFactory.buildPostRequest(url, BuildJsonContent(shortlink));
            HttpResponse response = Request(request);
            shortlink = response.parseAs(Shortlink.class);
            return shortlink;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    /**
     *
     * @param ticketId
     * @return
     */
    public PaymentRequestOutcome PaymentRequestOutcome(String ticketId){
        MCashUrl url = MCashUrl.PaymentRequestOutcome(ticketId);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = Request(request);
            PaymentRequestOutcome outcome = response.parseAs(PaymentRequestOutcome.class);
            return outcome;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;        
    }

    private HttpResponse Request(HttpRequest request) throws IOException, HttpResponseException {
        HttpResponse response;
        int tries = 0;
        do {
            response = request.execute();
            tries++;
        } while ((response.getStatusCode() / 100) == 5 && tries < 10);
        if (response.getStatusCode() / 100 == 5) {
            throw new HttpResponseException(response);
        } else {
            return response;
        }
    }

    private JsonHttpContent BuildJsonContent(Object object) {
        return new JsonHttpContent(JSON_FACTORY, object);
    }

    private HttpHeaders MakeHeaders(String merchantId, String userId, String authKey, String authMethod, String testbedToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Mcash-Merchant", merchantId);
        headers.set("X-Mcash-User", userId);
        headers.setAccept("application/vnd.mcash.api.merchant.v1+json");
        if (testbedToken != null) {
            headers.set("X-Testbed-Token", testbedToken);
        }
        if (authMethod.equals("SECRET")) {
            headers.setAuthorization(String.format("SECRET %s", authKey));
        } else {
            throw new IllegalArgumentException("Invalid auth method " + authMethod);
        }
        return headers;
    }
}
