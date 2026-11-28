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
    static final long TRADING_START=java.time.LocalDate.of(2026,11,27).atStartOfDay(java.time.ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli();
    static final Set<String> HOSTS=Set.of("openapi.okx.com","eea.okx.com","us.okx.com");
    static final Set<String> PATHS=Set.of("/api/v5/public/time","/api/v5/account/config","/api/v5/asset/asset-valuation?ccy=USD");
    static boolean allowed(URL url) { return url.getProtocol().equals("https")&&HOSTS.contains(url.getHost())&&(url.getPort()==-1||url.getPort()==443)&&url.getUserInfo()==null&&url.getRef()==null&&allowedPath(url.getFile()); }
    static boolean allowedPath(String path){return PATHS.contains(path)||path.matches("/api/v5/trade/fills-history\\?instType=(SWAP|FUTURES)&limit=100(?:&after=[0-9]{1,40})?")||path.matches("/api/v5/account/bills-archive\\?instType=(SWAP|FUTURES)&begin=[0-9]{1,16}&end=[0-9]{1,16}&limit=100(?:&after=[0-9]{1,40})?");}
    static String sign(String secret,String timestamp,String path) throws Exception {
        if(!allowedPath(path))throw new IllegalArgumentException("Unsupported read endpoint");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal((timestamp+"GET"+path).getBytes(StandardCharsets.UTF_8)));
    }
    static boolean readOnly(String permissions) { return permissions!=null&&Arrays.stream(permissions.split(",")).map(String::trim).filter(s->!s.isEmpty()).collect(java.util.stream.Collectors.toSet()).equals(Set.of("read_only")); }
    interface Reader {JSONObject read(String path,boolean authenticated) throws Exception;}
    private final JSONObject credentials;
    private final Reader reader;
    private long timeOffset;
    OkxApi(JSONObject credentials){this(credentials,null);}
    OkxApi(JSONObject credentials,Reader reader){this.credentials=credentials;this.reader=reader;}
    private JSONObject get(String path,boolean authenticated) throws Exception {
        String host=credentials.getString("host");URL url=new URL("https://"+host+path);if(!allowed(url))throw new IOException("Неподдерживаемый адрес OKX");
        if(reader!=null)return reader.read(path,authenticated);
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
        result.put("details",parts);
        String identity=credentials.getString("host")+":"+config.optString("uid",credentials.getString("apiKey"));byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));StringBuilder accountId=new StringBuilder();for(byte b:hash)accountId.append(String.format(Locale.ROOT,"%02x",b));result.put("accountId",accountId.toString());return result;
    }
    private JSONArray pages(String path,String[] fields,long since,long until) throws Exception {
        JSONArray all=new JSONArray();Set<String> seen=new HashSet<>();String cursor="";
        for(int page=0;page<2000;page++){
            if(reader==null)Thread.sleep(450);JSONArray rows=get(path+(cursor.isEmpty()?"":"&after="+cursor),true).getJSONArray("data");
            if(rows.length()==0)return all;
            String last="";
            for(int i=0;i<rows.length();i++){
                JSONObject raw=rows.getJSONObject(i);String id=raw.getString("billId");if(!id.matches("[0-9]{1,40}")||!seen.add(id))throw new IOException("OKX: повтор страницы истории, расчёт не обновлён");
                long ts=Long.parseLong(raw.getString("ts"));last=id;
                if(ts<since||ts>until)continue;JSONObject row=new JSONObject();for(String field:fields)if(raw.has(field))row.put(field,raw.get(field));row.put("billId",id);all.put(row);
            }

            if(last.equals(cursor))throw new IOException("OKX: история не завершена");cursor=last;
        }throw new IOException("OKX: слишком много страниц истории, расчёт не обновлён");
    }
    JSONObject tradingHistory(JSONObject previous,JSONObject balance) throws Exception {
        long until=System.currentTimeMillis()+timeOffset,since=Math.max(TRADING_START,Instant.ofEpochMilli(until).atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate().minusDays(89).atStartOfDay(java.time.ZoneId.of("Europe/Moscow")).toInstant().toEpochMilli());
        JSONArray fills=new JSONArray();String[] fillFields={"billId","tradeId","instId","instType","ts","fillTime","fillPx","fillSz","side","fee","feeCcy","subType"};
        for(String kind:new String[]{"SWAP","FUTURES"}){JSONArray list=pages("/api/v5/trade/fills-history?instType="+kind+"&limit=100",fillFields,since,until);for(int i=0;i<list.length();i++)fills.put(list.getJSONObject(i).put("instType",kind));}
        JSONArray bills=new JSONArray();for(String kind:new String[]{"SWAP","FUTURES"}){JSONArray list=pages("/api/v5/account/bills-archive?instType="+kind+"&begin="+since+"&end="+until+"&limit=100",new String[]{"billId","tradeId","instId","instType","ts","ccy","type","subType","pnl","fee","balChg","posBalChg"},since,until);for(int i=0;i<list.length();i++)bills.put(list.getJSONObject(i).put("instType",kind));}
        JSONObject old=previous!=null&&balance.getString("accountId").equals(previous.optString("accountId"))?previous.optJSONObject("trading"):null;
        JSONObject history=new JSONObject();history.put("fills",mergeHistory(old==null?null:old.optJSONArray("fills"),fills));history.put("bills",mergeHistory(old==null?null:old.optJSONArray("bills"),bills));history.put("from",TRADING_START);history.put("until",until);
        JSONArray ranges=old==null?new JSONArray():new JSONArray(old.optJSONArray("ranges")==null?"[]":old.getJSONArray("ranges").toString());ranges.put(new JSONObject().put("from",since).put("until",until));history.put("ranges",ranges);return history;
    }
    static JSONArray mergeHistory(JSONArray old,JSONArray fresh) throws Exception {
        Map<String,JSONObject> rows=new LinkedHashMap<>();for(JSONArray list:new JSONArray[]{old,fresh})if(list!=null)for(int i=0;i<list.length();i++){JSONObject row=list.getJSONObject(i);String id=row.getString("billId");JSONObject prior=rows.get(id);if(prior!=null&&!prior.optString("instId").equals(row.optString("instId")))throw new IOException("OKX: неоднозначная история");rows.put(id,row);}return new JSONArray(rows.values());
    }

}
