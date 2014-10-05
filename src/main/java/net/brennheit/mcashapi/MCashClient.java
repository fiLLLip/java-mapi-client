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
import net.brennheit.mcashapi.resource.*;
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
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.brennheit.mcashapi.listener.IListenForPaymentUpdated;
import net.brennheit.mcashapi.listener.IListenForReportClosed;
import net.brennheit.mcashapi.listener.IListenForShortlinkScan;

/**
 *
 * @author fiLLLip <filip at tomren.it>
 */
public class MCashClient implements AutoCloseable{

    private HttpHeaders httpHeaders;
    private HttpRequestFactory requestFactory;
    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();
    static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#0.00", new DecimalFormatSymbols(Locale.ENGLISH));
    private final String posId;
    private final String ledger;
    protected Vector paymentFinishedListeners;
    protected Vector reportClosedListeners;
    protected Vector shortlinkScannedListeners;
    private Timer paymentFinishedTimer;
    private Timer reportClosedTimer;
    private Timer shortlinkScannedTimer;
    private String globalTicketId;
    private String openReportUri;
    private String shortlinkId;
    private Date shortlinkStartListeningTime;

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
        MCashUrl.setBaseUri(baseUrl);
        this.httpHeaders = createHeaders(merchantId, userId, authKey, authMethod, testbedToken);
        this.posId = posId;
        this.ledger = ledger;
        requestFactory = HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                request.setHeaders(httpHeaders);
                JsonObjectParser jsonObjectParser = new JsonObjectParser(JSON_FACTORY);
                request.setParser(jsonObjectParser);
            }
        });
    }
    

    @Override
    public void close() {
        removeAllEventListeners();
        cancelAllTimers();
    }
    
    private void removeAllEventListeners(){
        if (this.paymentFinishedListeners != null) {
            this.paymentFinishedListeners.removeAllElements();
        }
        if (this.shortlinkScannedListeners != null) {
            this.shortlinkScannedListeners.removeAllElements();
        }
        if (this.reportClosedListeners != null) {
            this.reportClosedListeners.removeAllElements();
        }
    }
    
    private void cancelAllTimers(){
        if (paymentFinishedTimer != null) {
            paymentFinishedTimer.cancel();
            paymentFinishedTimer.purge();
            paymentFinishedTimer = null;
        }
        if (shortlinkScannedTimer != null) {
            shortlinkScannedTimer.cancel();
            shortlinkScannedTimer.purge();
            shortlinkScannedTimer = null;
        }
        if (reportClosedTimer != null) {
            reportClosedTimer.cancel();
            reportClosedTimer.purge();
            reportClosedTimer = null;
        }
    }

    /**
     * Add listener to listen for payment to finish. Finish is either status "ok" or "fail".
     * @param listener
     */
    public void addPaymentFinishedEventListener(IListenForPaymentUpdated listener) {
        if (this.paymentFinishedListeners == null) {
            this.paymentFinishedListeners = new Vector();
        }
        this.paymentFinishedListeners.addElement(listener);
    }

    protected void firePaymentFinishedEvent(PaymentRequestOutcome requestOutcome) {
        if (this.paymentFinishedListeners != null && !this.paymentFinishedListeners.isEmpty()) {
            Enumeration e = this.paymentFinishedListeners.elements();
            while (e.hasMoreElements()) {
                IListenForPaymentUpdated iListenForPaymentUpdated = (IListenForPaymentUpdated) e.nextElement();
                iListenForPaymentUpdated.paymentFinished(requestOutcome);

            }
        }
    }

    /**
     * Start poller on payment result.
     * @param ticketId 
     */
    public void startPaymentFinishedListener(String ticketId) {
        this.globalTicketId = ticketId;
        checkPaymentFinishedWithTimer();
    }

    private void checkPaymentFinished() {
        if (globalTicketId == null) {
            return;
        }
        PaymentRequestOutcome requestOutcome = getPaymentRequestOutcome(this.globalTicketId);
        if(requestOutcome == null) return;
        switch (requestOutcome.status.toLowerCase()) {
            case "pending":
                // Awaiting approvement by customer
                break;
            case "auth":
                // Approved by customer, run capture
                capturePaymentRequest(this.globalTicketId, null);
                break;
            case "ok":
                firePaymentFinishedEvent(requestOutcome);
                this.globalTicketId = null;
                return;
            case "fail":
                firePaymentFinishedEvent(requestOutcome);
                this.globalTicketId = null;
                return;
        }
    }

    private void checkPaymentFinishedWithTimer() {
        if (paymentFinishedTimer != null) {
            paymentFinishedTimer.cancel();
            paymentFinishedTimer.purge();
            paymentFinishedTimer = null;
        }
        checkPaymentFinished();
        if (this.globalTicketId != null) {
            paymentFinishedTimer = new Timer();
            paymentFinishedTimer.schedule(new CheckPaymentFinishedTask(), 1000);
        }
    }

    private class CheckPaymentFinishedTask extends TimerTask {

        @Override
        public void run() {
            checkPaymentFinishedWithTimer();
        }

    }

    /**
     * Add listener to listen for shortlink to be scanned.
     * @param listener 
     */
    public void addShortlinkScannedEventListener(IListenForShortlinkScan listener) {
        if (this.shortlinkScannedListeners == null) {
            this.shortlinkScannedListeners = new Vector();
        }
        this.shortlinkScannedListeners.addElement(listener);
    }

    protected void fireShortlinkScannedEvent(ShortlinkLastScan shortlinkLastScan) {
        if (this.shortlinkScannedListeners != null && !this.shortlinkScannedListeners.isEmpty()) {
            Enumeration e = this.shortlinkScannedListeners.elements();
            while (e.hasMoreElements()) {
                IListenForShortlinkScan iListenForShortlinkScan = (IListenForShortlinkScan) e.nextElement();
                iListenForShortlinkScan.shortlinkScanned(shortlinkLastScan);

            }
        }
    }

    /**
     * Start poller on shortlink scan.
     * @param shortlinkId ID of shortlink
     * @param startListeningTime Time of earliest possible scan
     */
    public void startShortlinkScannedListener(String shortlinkId, Date startListeningTime) {
        this.shortlinkId = shortlinkId;
        this.shortlinkStartListeningTime = startListeningTime;
        checkShortlinkScannedWithTimer();
    }

    private void checkShortlinkScanned() {
        if (this.shortlinkId == null || this.shortlinkStartListeningTime == null) {
            return;
        }
        long ttl = ((new Date()).getTime() - this.shortlinkStartListeningTime.getTime()) / 1000;
        ShortlinkLastScan shortlinkLastScan = getShortLinkLastScan(this.shortlinkId, ttl);
        if (shortlinkLastScan != null && shortlinkLastScan.id != null) {
            fireShortlinkScannedEvent(shortlinkLastScan);
            this.shortlinkId = null;
            this.shortlinkStartListeningTime = null;
        }
    }

    private void checkShortlinkScannedWithTimer() {
        if (shortlinkScannedTimer != null) {
            shortlinkScannedTimer.cancel();
            shortlinkScannedTimer.purge();
            shortlinkScannedTimer = null;
        }
        checkShortlinkScanned();
        if (this.shortlinkId != null && this.shortlinkStartListeningTime != null) {
            shortlinkScannedTimer = new Timer();
            shortlinkScannedTimer.schedule(new CheckShortlinkScannedTask(), 1000);
        }
    }

    private class CheckShortlinkScannedTask extends TimerTask {

        @Override
        public void run() {
            checkShortlinkScannedWithTimer();
        }

    }

    /**
     * Add listener to report to be closed.
     * @param listener 
     */
    public void addReportClosedEventListener(IListenForReportClosed listener) {
        if (this.reportClosedListeners == null) {
            this.reportClosedListeners = new Vector();
        }
        this.reportClosedListeners.addElement(listener);
    }

    protected void fireReportClosedEvent(ReportInfo reportInfo) {
        if (this.reportClosedListeners != null && !this.reportClosedListeners.isEmpty()) {
            Enumeration e = this.reportClosedListeners.elements();
            while (e.hasMoreElements()) {
                IListenForReportClosed iListenForPaymentUpdated = (IListenForReportClosed) e.nextElement();
                iListenForPaymentUpdated.reportClosed(reportInfo);

            }
        }
    }

    /**
     * Start closing report and polling for result. May take a while before
     * event fires due to latency on report closing.
     * @throws Exception 
     */
    public void startReportClosedListener() throws Exception {
        closeOpenReport();
    }

    private void closeOpenReport() throws Exception {
        LedgerOverview ledgerOverview = getLedgerOverview();
        if (ledgerOverview == null) {
            throw new Exception("Could not find ledger overview.");
        }
        String ledgerUri = null;
        for (String uri : ledgerOverview.uris) {
            if (uri.contains(this.ledger)) {
                ledgerUri = uri;
                break;
            }
        }
        if (ledgerUri == null) {
            throw new Exception("Could not find selected ledger.");
        }
        LedgerDetail ledgerDetail = getLedgerDetail();
        ReportInfo reportInfo = getReportInfoFromOpenUri(ledgerDetail.open_report_uri);
        if (reportInfo == null || !reportInfo.status.equals("open")) {
            throw new Exception("Already closed or closing report.");
        }
        closeReportFromOpenUri(ledgerDetail.open_report_uri);
        this.openReportUri = ledgerDetail.open_report_uri;
        reportInfo = getReportInfoFromOpenUri(this.openReportUri);
        if (reportInfo == null || (!reportInfo.status.equals("closing") && !reportInfo.status.equals("closed"))) {
            throw new Exception("Close report failed.");
        }
        checkReportClosedWithTimer();
    }

    private void checkReportClosed() {
        if (this.openReportUri == null) {
            return;
        }
        ReportInfo reportInfo = getReportInfoFromOpenUri(this.openReportUri);
        if (reportInfo != null && reportInfo.status.equals("closed")) {
            fireReportClosedEvent(reportInfo);
            this.openReportUri = null;
        }
    }

    private void checkReportClosedWithTimer() {
        if (reportClosedTimer != null) {
            reportClosedTimer.cancel();
            reportClosedTimer.purge();
            reportClosedTimer = null;
        }
        checkReportClosed();
        if (this.openReportUri != null) {
            reportClosedTimer = new Timer();
            reportClosedTimer.schedule(new CheckReportClosedTask(), 2000);
        }
    }

    private class CheckReportClosedTask extends TimerTask {

        @Override
        public void run() {
            checkReportClosedWithTimer();
        }

    }

    /**
     * Tries to connect to REST WebService.
     *
     * @return success
     */
    public boolean isReady() {
        Socket socket = null;
        try {
            String hostname = (new URI(MCashUrl.getBaseUri())).getHost();
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
    public ShortlinkLastScan getShortLinkLastScan(String shortlinkId, long ttl) {
        MCashUrl url = MCashUrl.ShortlinkLastScan(shortlinkId, ttl);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = doHttpRequest(request);
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
    public ResourceId createPaymentRequest(String posTicketId, String scanToken, double amount, String currency, double additionalAmount, boolean additionalAmountEdit, String callbackUri, boolean allowCredit) {
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
        createPaymentRequest.allow_credit = allowCredit;
        if (callbackUri != null) {
            createPaymentRequest.callback_uri = callbackUri;
        }
        if (additionalAmount > 0) {
            createPaymentRequest.additional_amount = MONEY_FORMAT.format(additionalAmount);
        } else {
            createPaymentRequest.additional_edit = additionalAmountEdit;
        }
        MCashUrl url = MCashUrl.PaymentRequest();
        try {
            HttpRequest request = requestFactory.buildPostRequest(url, buildJsonContent(createPaymentRequest));
            HttpResponse response = doHttpRequest(request);
            ResourceId resourceId = response.parseAs(ResourceId.class);
            return resourceId;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private void doPaymentRequestAction(String ticketId, String action, String callbackUri) {
        UpdatePaymentRequest updatePaymentRequest = new UpdatePaymentRequest();
        updatePaymentRequest.action = action;
        updatePaymentRequest.ledger = this.ledger;
        updatePaymentRequest.callback_uri = callbackUri;
        MCashUrl url = MCashUrl.PaymentRequest(ticketId);
        try {
            HttpRequest request = requestFactory.buildPutRequest(url, buildJsonContent(updatePaymentRequest));
            doHttpRequest(request);
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    /**
     *
     * @param ticketId
     * @param callbackUri
     */
    public void abortPaymentRequest(String ticketId, String callbackUri) {
        doPaymentRequestAction(ticketId, "abort", callbackUri);
    }

    /**
     *
     * @param ticketId
     * @param callbackUri
     */
    public void capturePaymentRequest(String ticketId, String callbackUri) {
        doPaymentRequestAction(ticketId, "capture", callbackUri);
    }

    /**
     *
     * @param serialNumber
     * @param callbackUri
     * @return
     */
    public ResourceId createShortlink(String serialNumber, String callbackUri) {
        Shortlink shortlink = new Shortlink();
        shortlink.callback_uri = callbackUri;
        shortlink.serial_number = serialNumber;
        MCashUrl url = MCashUrl.Shortlink();
        try {
            HttpRequest request = requestFactory.buildPostRequest(url, buildJsonContent(shortlink));
            HttpResponse response = doHttpRequest(request);
            ResourceId resourceId = response.parseAs(ResourceId.class);
            return resourceId;
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
    public PaymentRequestOutcome getPaymentRequestOutcome(String ticketId) {
        MCashUrl url = MCashUrl.PaymentRequestOutcome(ticketId);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = doHttpRequest(request);
            PaymentRequestOutcome outcome = response.parseAs(PaymentRequestOutcome.class);
            return outcome;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private HttpResponse doHttpRequest(HttpRequest request) throws IOException, HttpResponseException {
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

    /**
     *
     * @return
     */
    public LedgerOverview getLedgerOverview() {
        MCashUrl url = MCashUrl.Ledger();
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = doHttpRequest(request);
            LedgerOverview ledgerOverview = response.parseAs(LedgerOverview.class);
            return ledgerOverview;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     *
     * @param ledger
     * @return
     */
    public LedgerDetail getLedgerDetail(String ledger) {
        MCashUrl url = MCashUrl.LedgerDetail(ledger);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = doHttpRequest(request);
            LedgerDetail ledgerDetail = response.parseAs(LedgerDetail.class);
            return ledgerDetail;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Uses ledger specified in constructor
     *
     * @return
     */
    public LedgerDetail getLedgerDetail() {
        return getLedgerDetail(ledger);
    }

    /**
     *
     * @param ledger
     * @param reportId
     * @return
     */
    public ReportInfo getReportInfo(String ledger, String reportId) {
        MCashUrl url = MCashUrl.Report(ledger, reportId);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = doHttpRequest(request);
            ReportInfo reportInfo = response.parseAs(ReportInfo.class);
            return reportInfo;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Uses ledger specified in constructor
     *
     * @param reportId
     * @return
     */
    public ReportInfo getReportInfo(String reportId) {
        return getReportInfo(ledger, reportId);
    }

    /**
     *
     * @param uri
     * @return
     */
    public ReportInfo getReportInfoFromOpenUri(String uri) {
        GenericUrl url = new GenericUrl(uri);
        try {
            HttpRequest request = requestFactory.buildGetRequest(url);
            HttpResponse response = doHttpRequest(request);
            ReportInfo reportInfo = response.parseAs(ReportInfo.class);
            return reportInfo;
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     *
     * @param ledger
     * @param reportId
     */
    public void closeReport(String ledger, String reportId) {
        MCashUrl url = MCashUrl.Report(ledger, reportId);
        try {
            HttpRequest request = requestFactory.buildPutRequest(url, null);
            doHttpRequest(request);
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Uses ledger specified in constructor
     *
     * @param reportId
     */
    public void closeReport(String reportId) {
        closeReport(ledger, reportId);
    }

    public void closeReportFromOpenUri(String uri) {
        GenericUrl url = new GenericUrl(uri);
        try {
            HttpRequest request = requestFactory.buildPutRequest(url, null);
            doHttpRequest(request);
        } catch (IOException ex) {
            Logger.getLogger(MCashClient.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private JsonHttpContent buildJsonContent(Object object) {
        return new JsonHttpContent(JSON_FACTORY, object);
    }

    private HttpHeaders createHeaders(String merchantId, String userId, String authKey, String authMethod, String testbedToken) {
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
