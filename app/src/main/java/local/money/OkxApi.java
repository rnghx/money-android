package local.money;

import org.json.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.math.BigDecimal;

final class OkxApi {
    static final Set<String> HOSTS=Set.of("openapi.okx.com","eea.okx.com","us.okx.com");
    static final Set<String> PATHS=Set.of("/api/v5/public/time","/api/v5/account/config","/api/v5/asset/asset-valuation?ccy=USD");
    static boolean allowed(URL url) { return url.getProtocol().equals("https")&&HOSTS.contains(url.getHost())&&(url.getPort()==-1||url.getPort()==443)&&url.getUserInfo()==null&&url.getRef()==null&&PATHS.contains(url.getFile()); }
    static String sign(String secret,String timestamp,String path) throws Exception {
        if(!PATHS.contains(path))throw new IllegalArgumentException("Unsupported read endpoint");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal((timestamp+"GET"+path).getBytes(StandardCharsets.UTF_8)));
    }
    static boolean readOnly(String permissions) { return permissions!=null&&Arrays.stream(permissions.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("read_only")); }
    private final JSONObject credentials;
    private long timeOffset;
    OkxApi(JSONObject credentials){this.credentials=credentials;}
    private JSONObject get(String path,boolean authenticated) throws Exception {
        String host=credentials.getString("host");URL url=new URL("https://"+host+path);if(!allowed(url))throw new IOException("Неподдерживаемый адрес OKX");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        try {
            c.setRequestMethod("GET");c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("Accept","application/json");
            if(authenticated){String ts=new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.ofEpochMilli(System.currentTimeMillis()+timeOffset));c.setRequestProperty("OK-ACCESS-KEY",credentials.getString("apiKey"));c.setRequestProperty("OK-ACCESS-PASSPHRASE",credentials.getString("passphrase"));c.setRequestProperty("OK-ACCESS-TIMESTAMP",ts);c.setRequestProperty("OK-ACCESS-SIGN",sign(credentials.getString("secret"),ts,path));}
            int status=c.getResponseCode();if(status<200||status>=300)throw new IOException("OKX недоступен ("+status+"). Проверь регион API и сеть.");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1){out.write(bytes,0,n);if(out.size()>1024*1024)throw new IOException("Слишком большой ответ OKX");}
                JSONObject reply=new JSONObject(out.toString("UTF-8"));String code=reply.optString("code");
                if(!code.equals("0"))throw new IOException(code.equals("50102")?"Не совпадает время OKX. Повтори обновление.":"OKX отклонил запрос ("+code.replaceAll("[^0-9]","")+"). Проверь ключ, passphrase, регион и ограничения IP.");
                return reply;
            }
        }finally{c.disconnect();}
    }
    JSONObject balance() throws Exception {
        long started=System.currentTimeMillis();JSONObject clock=get("/api/v5/public/time",false);long server=Long.parseLong(clock.getJSONArray("data").getJSONObject(0).getString("ts"));
        timeOffset=server-((started+System.currentTimeMillis())/2);
        JSONObject config=get("/api/v5/account/config",true).getJSONArray("data").getJSONObject(0);
        if(!readOnly(config.optString("perm")))throw new IOException("Нужен отдельный API-ключ только с Read. Trade и Withdraw должны быть выключены.");
        JSONObject data=get("/api/v5/asset/asset-valuation?ccy=USD",true).getJSONArray("data").getJSONObject(0);
        BigDecimal usd=new BigDecimal(data.getString("totalBal"));if(usd.abs().compareTo(new BigDecimal("1000000000000"))>0)throw new IOException("Неизвестная сумма OKX");
        JSONObject result=new JSONObject();result.put("usd",usd.toPlainString());result.put("timestamp",Long.parseLong(data.getString("ts")));result.put("fetchedAt",System.currentTimeMillis());
        JSONObject parts=new JSONObject(),details=data.optJSONObject("details");if(details!=null)for(String name:new String[]{"funding","trading","earn","classic"})if(details.has(name))parts.put(name,new BigDecimal(details.getString(name)).toPlainString());
        result.put("details",parts);return result;
    }
}
