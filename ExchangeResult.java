package com.nationswaw.model;

public class ExchangeResult {
    private final boolean success;
    private final String message;
    private final double amountFrom, amountTo, spreadAmount, iofAmount, rateUsed;
    private final String fromCurrency, toCurrency;

    private ExchangeResult(boolean success, String message, double amountFrom, double amountTo,
                           double spread, double iof, String from, String to, double rate) {
        this.success = success; this.message = message;
        this.amountFrom = amountFrom; this.amountTo = amountTo;
        this.spreadAmount = spread; this.iofAmount = iof;
        this.fromCurrency = from; this.toCurrency = to; this.rateUsed = rate;
    }

    public static ExchangeResult success(double from, double to, double spread, double iof, String f, String t, double rate) {
        return new ExchangeResult(true, "OK", from, to, spread, iof, f, t, rate);
    }
    public static ExchangeResult fail(String msg) {
        return new ExchangeResult(false, msg, 0, 0, 0, 0, null, null, 0);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public double getAmountFrom() { return amountFrom; }
    public double getAmountTo() { return amountTo; }
    public double getSpreadAmount() { return spreadAmount; }
    public double getIofAmount() { return iofAmount; }
    public String getFromCurrency() { return fromCurrency; }
    public String getToCurrency() { return toCurrency; }
    public double getRateUsed() { return rateUsed; }
}
