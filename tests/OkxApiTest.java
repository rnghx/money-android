package local.money;
import java.net.URL;
import org.json.*;
import java.lang.reflect.*;
public class OkxApiTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        check(OkxApi.allowed(new URL("https://openapi.okx.com/api/v5/asset/asset-valuation?ccy=USD")));
        check(OkxApi.allowed(new URL("https://eea.okx.com/api/v5/account/config")));
        check(OkxApi.allowed(new URL("https://us.okx.com/api/v5/public/time")));
        check(!OkxApi.allowed(new URL("https://openapi.okx.com/api/v5/trade/order")));
        check(!OkxApi.allowed(new URL("https://openapi.okx.com/api/v5/asset/withdrawal")));
        check(!OkxApi.allowed(new URL("https://openapi.okx.com.evil.example/api/v5/account/config")));
        check(!OkxApi.allowed(new URL("http://openapi.okx.com/api/v5/account/config")));
        check(!OkxApi.allowed(new URL("https://name@openapi.okx.com/api/v5/account/config")));
        check(OkxApi.readOnly("read_only"));
        check(!OkxApi.readOnly("read_only,trade"));
        check(!OkxApi.readOnly("read_only,withdraw"));
        check(!OkxApi.readOnly(""));
        check(OkxApi.sign("test-secret","2026-11-09T12:00:00.000Z","/api/v5/asset/asset-valuation?ccy=USD").equals("70vlstOJRQ59pljRCLkyep6h4bhqQ296l1V6gB2lE10="));
        check(OkxApi.allowed(new URL("https://openapi.okx.com/api/v5/trade/fills-history?instType=SWAP&limit=100&after=123")));
        check(OkxApi.allowed(new URL("https://openapi.okx.com/api/v5/account/bills-archive?instType=FUTURES&begin=1&end=100&limit=100")));
        check(!OkxApi.allowedPath("/api/v5/trade/fills-history?instType=SWAP&limit=100&after=123&other=1"));
        check(!OkxApi.allowedPath("/api/v5/trade/fills-history?instType=SPOT&limit=100"));
        check(OkxApi.TRADING_START==java.time.Instant.parse("2026-10-31T21:00:00Z").toEpochMilli());
        final int[] page={0};OkxApi fixture=new OkxApi(new JSONObject("{\"host\":\"openapi.okx.com\"}"),(path,auth)->{check(auth);JSONArray data=new JSONArray();int first=page[0]++*100;for(int i=first;i<Math.min(250,first+100);i++)data.put(new JSONObject().put("billId",String.valueOf(i+1)).put("ts",String.valueOf(1000+i)).put("instId","BTC-USDT"));return new JSONObject().put("data",data);});
        Method pages=OkxApi.class.getDeclaredMethod("pages",String.class,String[].class,long.class,long.class);pages.setAccessible(true);
        JSONArray all=(JSONArray)pages.invoke(fixture,"/api/v5/trade/fills-history?instType=SWAP&limit=100",new String[]{"billId","ts","instId"},1L,9999L);check(all.length()==250);check(page[0]==4);
        OkxApi repeat=new OkxApi(new JSONObject("{\"host\":\"openapi.okx.com\"}"),(path,auth)->{JSONArray data=new JSONArray();for(int i=0;i<100;i++)data.put(new JSONObject().put("billId",String.valueOf(i+1)).put("ts","1000"));return new JSONObject().put("data",data);});
        boolean repeated=false;try{pages.invoke(repeat,"/api/v5/trade/fills-history?instType=SWAP&limit=100",new String[]{"billId","ts"},1L,9999L);}catch(InvocationTargetException e){repeated=e.getCause() instanceof java.io.IOException;}check(repeated);
        JSONArray old=new JSONArray("[{\"billId\":\"1\",\"instId\":\"BTC-USDT\",\"fee\":\"-1\"}]");JSONArray fresh=new JSONArray("[{\"billId\":\"1\",\"instId\":\"BTC-USDT\",\"fee\":\"-2\"},{\"billId\":\"2\"}]");JSONArray merged=OkxApi.mergeHistory(old,fresh);check(merged.length()==2);check(merged.getJSONObject(0).getString("fee").equals("-2"));
        System.out.println("OKX checks passed: official hosts, GET read endpoints, read-only permissions, independent HMAC vector.");
    }
}
