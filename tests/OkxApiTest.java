package local.money;
import java.net.URL;
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
        System.out.println("OKX checks passed: official hosts, GET read endpoints, read-only permissions, independent HMAC vector.");
    }
}
