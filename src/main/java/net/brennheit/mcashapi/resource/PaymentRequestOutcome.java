/* 
 * Copyright (c) 2014, fiLLLip
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

import com.google.api.client.util.Key;
import java.util.List;

/**
 *
 * @author fiLLLip
 */
public class PaymentRequestOutcome {

    @Key
    public String currency;
    @Key
    public String amount;
    @Key
    public String additional_amount;
    @Key
    public String auth_amount;
    @Key
    public String auth_additional_amount;
    @Key
    public List<Capture> captures;
    @Key
    public String status;
    @Key
    public int status_code;
    @Key
    public String customer;
    @Key
    public String date_modified;

    public DateTime getDate_modified() {
        return new DateTime(date_modified);
    }
    
    public void setDate_modified(DateTime dateTime) {
        date_modified = dateTime.toStringMCashDateTime();
    }

    public DateTime getDate_expires() {
        return new DateTime(date_expires);
    }

    public void setDate_expires(DateTime dateTime) {
        date_expires = dateTime.toStringMCashDateTime();
    }
    
    @Key
    public String date_expires;
    @Key
    public boolean credit;
    @Key
    public String interchange_fee;
    @Key
    public String transaction_fee;
    @Key
    public String report_id;
    @Key
    public String report_uri;
    @Key
    public String ledger;
    @Key
    public String attachment_uri;
    @Key
    public String pos_id;
    @Key
    public String pos_tid;
    @Key
    public String tid;
}
