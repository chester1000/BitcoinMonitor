package pl.d30.bitcoin.dash.service;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.google.analytics.tracking.android.EasyTracker;
import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;
import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.text.DecimalFormat;

import pl.d30.bitcoin.D30;
import pl.d30.bitcoin.R;
import pl.d30.bitcoin.dash.exchange.Exchange;

public class BitcoinMonitorDashService extends DashClockExtension {

    protected SharedPreferences sp;

    protected String currency = D30.DEF_CURRENCY;
    protected int source = Exchange.MTGOX;
    protected float a;

    protected int tries = 0;

    @Override
    protected void onInitialize(boolean isReconnect) {
        super.onInitialize(isReconnect);
        setUpdateWhenScreenOn(true);

        EasyTracker.getInstance().setContext(this);

        sp = getSharedPreferences(D30.PREF_FILE_BTC, MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onUpdateData(int reason) {

        source = Integer.parseInt(sp.getString(D30.IDX_SOURCE, "" + source));
        validateSource();

        currency = sp.getString(D30.IDX_CURRENCY, currency);
        validateCurrency();

        Ion.with(getApplicationContext(), getUrl())
            .setHeader("User-Agent", "DashClock Bitcoin Monitor " + D30.getAppVersion(getApplicationContext()) + ", " + D30.getDeviceInfo())
            .asJsonObject()
            .setCallback(new FutureCallback<JsonObject>() {
                @Override
                public void onCompleted(Exception e, JsonObject json) {
                if( e!=null) Log.w(D30.LOG, e.toString());
                if( json!=null && !updateWidget( getCurrentValue(json) ) ) handleError();
                }
            });
    }

    protected boolean updateWidget(String newValue) {

        final float finalValue;
        try {
            finalValue = Float.parseFloat(newValue);

        } catch(NumberFormatException e) {
            return hideUpdate();

        }
        float value = finalValue;

        // get amount
        String amount = sp.getString(D30.IDX_AMOUNT, "");
        if( !amount.isEmpty() ) {
            try {
                a = Float.parseFloat( amount );
                if( a<=0 ) fixAmount();
            }
            catch(NumberFormatException e) { fixAmount(); }

        } else fixAmount();


        // process amount
        if( a==1.0f ) amount = "1";
        else {
            value *= a;

            String[] tmp = amount.split("\\.");
            amount = tmp[0].replaceFirst("^0+(?!$)", "");
            if( amount.equals("") ) amount = "0";
            if( tmp.length>1 ) {
                String decimals = tmp[1].replaceAll("[0]+$", "");
                if(!decimals.equals("")) amount += "." + decimals;
            }
        }

        publishUpdate(
            getPrintableValue(getFormattedValue(value), true),
            getPrintableValue(newValue, false),
            amount
        );

        // TODO: will be needed for showing when errors during data fetching occur
        logEntry( finalValue );

        return true;
    }

    protected void publishUpdate(String status, String expTitle, String amount) {
        publishUpdate(new ExtensionData()
            .visible(true)
            .icon(R.drawable.ic_btc)
            .status(status)
            .expandedTitle(expTitle)
            .expandedBody(getString(R.string.expanded_body, amount, "BTC", getSourceName(source)))
            .clickIntent(new Intent(Intent.ACTION_VIEW).setData(Uri.parse("http://preev.com/btc/" + currency.toLowerCase()))));
    }

    protected boolean hideUpdate() { // TODO: or show info about outdated data
        publishUpdate(new ExtensionData().visible(false));
        return false;
    }

    protected void handleError() {

    }


    protected void validateSource() {
        // do nothing - any source is valid
    }

    protected void validateCurrency() {
        if( source==Exchange.BITSTAMP && !currency.equals(D30.USD) ) fixCurrency();
        else if( source==Exchange.BTCE && !currency.equals(D30.USD) && !currency.equals(D30.EUR) ) fixCurrency();
    }


    protected String getUrl() {
        switch( source ) {
            case Exchange.BTCE: return "https://btc-e.com/api/2/btc_" + currency.toLowerCase() + "/ticker";
            case Exchange.BITSTAMP: return "https://www.bitstamp.net/api/ticker/";
            default:
            case Exchange.MTGOX: return "https://data.mtgox.com/api/1/BTC" + currency.toUpperCase() + "/ticker";
        }
    }

    protected String getCurrentValue(JsonObject j) {
        switch( source ) {
            case Exchange.MTGOX: return getFromMtgox( j );
            case Exchange.BITSTAMP: return getFromBitstamp( j );
            case Exchange.BTCE: return getFromBtce( j );
            default: return null;
        }
    }

    protected String getFormattedValue(float value) {
        return value<10 ? new DecimalFormat("#.##").format(value) : "" + Math.round(value);
    }

    protected String getPrintableValue(String v, boolean compact) {

        // Prefixed:
        if( currency.equals("USD") ) return "$" + v;
        else if( currency.equals("GBP") ) return "£" + v;
        else if( currency.equals("JPY") ) return "¥" + v;

        // Suffixed:
        else if( currency.equals("EUR") ) return v + "€";
        else if( currency.equals("PLN") ) return v + "zł";
        else if( currency.equals("NOK") ) return v + "kr";

        // Prefixed w/long form
        else if( currency.equals("AUD") ) return (compact ? "A$" : "AU$") + v;
        else if( currency.equals("CAD") ) return (compact ? "C$" : "CA$") + v;
        else if( currency.equals("SGD") ) return (compact ? "S$" : "SG$") + v;

        else return null;
    }


    protected void logEntry(float value) {
        sp.edit()
            .putFloat("prevValue", value)
            .putLong("prevEpoch", System.currentTimeMillis() / 1000)
            .putInt("prevSource", source)
            .putString("prevCurrency", currency)
            .apply();
    }

    protected void fixSource() {
        sp.edit().putString(D30.IDX_SOURCE, Integer.toString(source = Exchange.MTGOX)).apply();
    }
    protected void fixCurrency() {
        sp.edit().putString(D30.IDX_CURRENCY, currency = D30.USD).apply();
    }
    protected void fixAmount() {
        sp.edit().putString(D30.IDX_AMOUNT, Float.toString(a = 1f)).apply();
    }





    // STATIC
    protected static String getFromMtgox(JsonObject j) {
        j = D30.Json.getObject(j, "return");
        if( j!=null ) {
            j = D30.Json.getObject(j, "last");
            if( j!=null ) return D30.Json.getString(j, "value");
        }
        return null;
    }
    protected static String getFromBitstamp(JsonObject j) {
        return D30.Json.getString(j, "last");
    }
    protected static String getFromBtce(JsonObject j) {
        j = D30.Json.getObject(j, "ticker");
        return j!=null ? D30.Json.getString(j, "last") : null;
    }

    protected static String getSourceName(int source, boolean pretty) {
        switch( source ) {
            default:
            case Exchange.MTGOX: return pretty ? "Mt.Gox" : "mtgox";
            case Exchange.BTCE: return pretty ? "BTC-e" : "btce";
            case Exchange.BITSTAMP: return pretty? "Bitstamp" : "bitstamp";
        }
    }
    protected static String getSourceName(int source) { return getSourceName(source, true); }
}