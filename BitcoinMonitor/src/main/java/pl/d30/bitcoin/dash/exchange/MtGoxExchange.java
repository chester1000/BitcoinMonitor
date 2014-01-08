package pl.d30.bitcoin.dash.exchange;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import pl.d30.bitcoin.D30;
import pl.d30.bitcoin.dash.cryptocoin.Coin;

public class MtGoxExchange extends Exchange {

    private static final String URL = "http://data.mtgox.com/api/2/%s%s/money/";
    private static final String URL_TICKER = "ticker_fast";
    private static final String URL_ORDER_BOOK = "depth/fetch";

    public static final String NAME = "mtgox";
    public static final String PRETTY_NAME = "Mt.Gox";

    private MtGoxExchange(Context context) {
        super(context);
    }

    protected void processTickerResponse(JsonObject json, int currency, int item, OnTickerDataAvailable cb) {
        JsonObject price = D30.Json.getObject(json, "data");
        if( price!=null ) {

            long ts;
            try {
                ts = getTimestamp(price);

            } catch(NumberFormatException ignored) {
                ts = 0l;
            }

            JsonObject priceLast = D30.Json.getObject(price, getPriceTypeName(PRICE_LAST));
            if( priceLast!=null ) {

                if(
                    lastValue==null
                    ||
                    lastValue.getItem()!=item
                    ||
                    lastValue.getCurrency()!=currency

                ) {
                    lastValue = new LastValue(extractValue(priceLast), currency, item);

                    JsonObject priceSell = D30.Json.getObject(price, getPriceTypeName(PRICE_SELL));
                    lastValue.setSellValue( extractValue(priceSell) );

                    JsonObject priceBuy = D30.Json.getObject(price, getPriceTypeName(PRICE_BUY));
                    lastValue.setBuyValue( extractValue(priceBuy) );

                } else lastValue.setLastValue(extractValue(priceLast));

                if( ts>0 ) lastValue.setTickerTimestamp(ts);

                if( cb!=null ) cb.onTicker(getId(), lastValue);
            }
        }
    }

    @Override
    protected void processOrderBookResponse(JsonObject json, int currency, int item, float amount, OnTickerDataAvailable cb) {
        super.processOrderBookResponse(D30.Json.getObject(json, "data"), currency, item, amount, cb);
    }

    @Override
    protected float getSellPrice(JsonArray asks, float amount) {
        float tmpAmount = 0f;
        JsonArray asksNew = new JsonArray();
        for(int i=asks.size()-1; i>0; i--) {
            JsonElement e = asks.get(i);
            tmpAmount += extractAmount(e);
            asksNew.add(e);
            if( tmpAmount>=amount ) break;
        }
        return super.getSellPrice(asksNew, amount);
    }
    @Override
    protected float getBuyPrice(JsonArray bids, float amount) {
        float tmpAmount = 0f;
        JsonArray bidsNew = new JsonArray();
        for(int i=bids.size()-1; i>0; i--) {
            JsonElement e = bids.get(i);
            tmpAmount += extractAmount(e);
            bidsNew.add(e);
            if( tmpAmount>=amount ) break;
        }
        return super.getSellPrice(bidsNew, amount);
    }

    protected Float extractPrice(JsonElement e) {
        return e.isJsonObject() ? D30.Json.getFloat( e.getAsJsonObject(), "price") : null;
    }
    protected Float extractAmount(JsonElement e) {
        return e.isJsonObject() ? D30.Json.getFloat(e.getAsJsonObject(), "amount") : null;
    }
    protected Long getTimestamp(JsonObject json) {
        return Long.parseLong(D30.Json.getString(json, "now"))/1000000;
    }

    // MtGox specific functions:
    private String extractValue(JsonObject priceObject) {
        return D30.Json.getString(priceObject, "value");
    }

    public int getId() {
        return MTGOX;
    }
    public String getName() {
        return NAME;
    }
    public String getPrettyName() {
        return PRETTY_NAME;
    }
    protected String getBaseUrl(int currency, int item) {
        return String.format(URL, Coin.getName(item).toUpperCase(), getCurrencyName(currency).toUpperCase());
    }
    protected String getTickerUrlSuffix() {
        return URL_TICKER;
    }
    protected String getOrderBookUrlSuffix() {
        return URL_ORDER_BOOK;
    }
    public boolean isCurrencySupported(int currency) {
        return true;
    }
    public boolean isItemSupported(int item) {
        return item==Coin.BTC;
    }


    // singleton magic
    private static MtGoxExchange mInstance = null;
    public static MtGoxExchange getInstance(Context context) {
        if( mInstance==null ) mInstance = new MtGoxExchange(context);
        return mInstance;
    }
}
