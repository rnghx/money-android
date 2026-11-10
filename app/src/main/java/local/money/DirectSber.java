package local.money;

// Read-only interoperability client. Protocol reference: ZenPlugins PR #1222,
// revision 51ad568234809e4f401995d5f969026423ba64e8 (GPL-3.0).
// No payment or transfer endpoints are implemented.
import android.os.Build;
import org.json.*;
import org.xmlpull.v1.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class DirectSber {
    static class RegistrationExpired extends IOException { RegistrationExpired(){super("Регистрация Сбера отозвана");} }
    interface Confirm { String ask() throws Exception; }
    interface Persist { void save(JSONObject access) throws Exception; }
    static final String VERSION="17.13.0";
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private String host,session;
    private final Set<String> owned=new HashSet<>();
    private JSONObject capital;
    private record Reply(String body,String contentType) {}
    static boolean allowed(URL url) {
        return url.getProtocol().equals("https") && url.getUserInfo()==null &&
            ((url.getHost().equals("online.sberbank.ru")&&url.getPort()==4477)||
             (url.getHost().matches("[a-z0-9-]+\\.online\\.sberbank\\.ru")&&url.getPort()==8543));
    }
    private Reply request(String address,String type,String body) throws Exception {
        URL url=new URL(address);if(!allowed(url))throw new IOException("Неподдерживаемый адрес банка");
        HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        try {
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type",type);c.setRequestProperty("User-Agent","Mobile Device");c.setRequestProperty("Accept-Charset","UTF-8");c.setRequestProperty("Accept-Encoding","identity");
            for(var entry:cookies.get(url.toURI(),Collections.emptyMap()).entrySet())c.setRequestProperty(entry.getKey(),String.join("; ",entry.getValue()));
            if(session!=null&&url.getPort()==8543)c.setRequestProperty("UCS_SESSION_ID",session);
            try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
            int status=c.getResponseCode();cookies.put(url.toURI(),c.getHeaderFields());
            if(status<200||status>=300)throw new IOException(status==401||status==403?"Банк отклонил вход. Повтори подключение.":"Банк недоступен ("+status+")");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                byte[] block=new byte[8192];int size;while((size=in.read(block))!=-1){buffer.write(block,0,size);if(buffer.size()>16*1024*1024)throw new IOException("Слишком большой ответ банка");}
                return new Reply(buffer.toString("UTF-8"),c.getContentType());
            }
        } finally {c.disconnect();}
    }
    private Map<String,String> form(String path,Map<String,String> values) throws Exception {
        StringBuilder data=new StringBuilder();for(var entry:values.entrySet()){if(data.length()>0)data.append('&');data.append(URLEncoder.encode(entry.getKey(),"UTF-8")).append('=').append(URLEncoder.encode(entry.getValue(),"UTF-8"));}
        String xml=request("https://online.sberbank.ru:4477/CSAMAPI/"+path,"application/x-www-form-urlencoded; charset=UTF-8",data.toString()).body;
        if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY"))throw new IOException("Неподдерживаемый XML Сбера");
        XmlPullParser parser=XmlPullParserFactory.newInstance().newPullParser();parser.setInput(new StringReader(xml));
        Map<String,String> fields=new HashMap<>();List<String> pathParts=new ArrayList<>();int event;
        while((event=parser.next())!=XmlPullParser.END_DOCUMENT){
            if(event==XmlPullParser.DOCDECL)throw new IOException("Неподдерживаемый XML Сбера");
            if(event==XmlPullParser.START_TAG)pathParts.add(parser.getName());
            else if(event==XmlPullParser.END_TAG){if(!pathParts.isEmpty())pathParts.remove(pathParts.size()-1);}
            else if(event==XmlPullParser.TEXT&&!parser.getText().trim().isEmpty()&&!pathParts.isEmpty()){
                String tag=pathParts.get(pathParts.size()-1),value=parser.getText().trim();fields.put(tag,value);
                if((tag.equals("code")&&pathParts.size()>1&&pathParts.get(pathParts.size()-2).equals("status"))||tag.equals("status"))fields.put("_status",value);
            }
        }return fields;
    }
    private Map<String,String> common() {
        Map<String,String> out=new LinkedHashMap<>();out.put("version","9.20");out.put("appType","android");out.put("appVersion",VERSION);out.put("deviceName",Build.MODEL);return out;
    }
    private void accepted(Map<String,String> response) throws IOException {
        if(!"0".equals(response.get("_status")))throw new IOException("Сбер не подтвердил вход. Проверь логин, код входа и подтверждение банка.");
    }
    private String sdk(JSONObject access) throws Exception {
        // Use installation-local identity and real model/OS; do not collect SIM,
        // IMEI, Wi-Fi, location or fabricate geographical coordinates.
        JSONObject data=new JSONObject();data.put("TIMESTAMP",Instant.now().toString());data.put("DeviceModel",Build.MODEL);data.put("deviceName",Build.MODEL);data.put("DeviceSystemName","Android");data.put("DeviceSystemVersion",String.valueOf(Build.VERSION.SDK_INT));data.put("Languages",Locale.getDefault().getLanguage());data.put("OS_ID",access.getString("deviceId"));data.put("RSA_ApplicationKey",access.getString("deviceId").toUpperCase(Locale.ROOT));data.put("SDK_VERSION","2.0.1");data.put("GeoLocationInfo",new JSONArray());data.put("MultitaskingSupported",true);return data.toString();
    }
    void authenticate(JSONObject access,boolean register,Confirm confirm,Persist persist) throws Exception {
        cookies.getCookieStore().removeAll();session=null;host=null;
        String guid=access.optString("guid","");Map<String,String> response;
        if(guid.isEmpty()) {
            if(!register)throw new IOException("Подключи Сбер заново");
            Map<String,String> values=common();values.put("operation","register");values.put("login",access.getString("login"));values.put("devID",access.getString("deviceId"));values.put("devIDOld",access.optString("deviceOld",access.getString("deviceId")));
            response=form("registerApp.do",values);accepted(response);guid=response.getOrDefault("mGUID","");if(guid.isEmpty())throw new IOException("Банк не выдал идентификатор подключения");
            String kind=response.getOrDefault("type","");
            if(kind.equals("smsp")) {
                String code=confirm.ask();if(code==null||!code.matches("\\d{4,10}"))throw new IOException("Подтверждение отменено");
                values=new LinkedHashMap<>();values.put("operation","confirm");values.put("mGUID",guid);values.put("smsPassword",code);values.put("version","9.20");values.put("appType","android");accepted(form("registerApp.do",values));
            } else if(!kind.isEmpty())throw new IOException("Банк запросил неподдерживаемый способ подтверждения");
            values=common();values.put("operation","createPIN");values.put("mGUID",guid);values.put("password",access.getString("pin"));values.put("isLightScheme","false");values.put("devID",access.getString("deviceId"));values.put("devIDOld",access.optString("deviceOld",access.getString("deviceId")));values.put("mobileSdkData",sdk(access));response=form("registerApp.do",values);
        } else {
            Map<String,String> values=common();values.put("operation","button.login");values.put("mGUID",guid);values.put("password",access.getString("pin"));values.put("isLightScheme","false");values.put("devID",access.getString("deviceId"));values.put("mobileSdkData",sdk(access));response=form("login.do",values);
            if("7".equals(response.get("_status")))throw new RegistrationExpired();
        }
        accepted(response);host=response.getOrDefault("host","");String token=response.getOrDefault("token","");
        if(!host.matches("[a-z0-9-]+\\.online\\.sberbank\\.ru:8543")||token.isEmpty())throw new IOException("Неизвестный формат ответа Сбера");
        access.put("guid",guid);persist.save(access);
        JSONObject auth=new JSONObject();auth.put("token",token);auth.put("version",VERSION);auth.put("platform","android");
        Reply reply=request("https://"+host+"/sm-uko/v3/mobile/aggregate/session/create","application/json; charset=UTF-8",auth.toString());
        // Validate each aggregate part without persisting the personal payload.
        String contentType=reply.contentType==null?"":reply.contentType;
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("boundary=(?:\"([^\"]+)\"|([^;\\s]+))",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(contentType);
        if(!matcher.find())throw new IOException("Сбер изменил формат сессии");String boundary=matcher.group(1)!=null?matcher.group(1):matcher.group(2);
        boolean main=false,uko=false;String[] parts=reply.body.split(java.util.regex.Pattern.quote("--"+boundary));
        if(parts.length<3||!parts[parts.length-1].trim().startsWith("--"))throw new IOException("Неполный ответ Сбера");
        for(int i=1;i<parts.length-1;i++){
            int split=parts[i].indexOf("\r\n\r\n");if(split<0)throw new IOException("Неполная сессия Сбера");
            String headers=parts[i].substring(0,split);JSONObject payload=new JSONObject(parts[i].substring(split+4).trim());if(!payload.optBoolean("success"))throw new IOException("Сбер не создал сессию");
            if(headers.matches("(?is).*X-UFS-SubsystemCode:\\s*MAIN_SCREEN\\s*.*")){
                main=true;JSONObject model=payload.getJSONObject("body").getJSONObject("dataModel");capital=capitalSnapshot(model);
                for(Iterator<String> keys=model.keys();keys.hasNext();){JSONObject group=model.optJSONObject(keys.next());if(group==null)continue;JSONArray products=group.optJSONArray("data");if(products==null)continue;for(int p=0;p<products.length();p++){JSONObject product=products.optJSONObject(p);if(product!=null&&product.has("id"))owned.add(product.get("id").toString());}}
            }
            if(headers.matches("(?is).*X-UFS-SubsystemCode:\\s*SM_UKO\\s*.*"))uko=true;
        }
        if(!main||!uko)throw new IOException("Не все сервисы Сбера доступны");
        for(HttpCookie cookie:cookies.getCookieStore().getCookies())if(cookie.getName().equals("UFS-SESSION")&&!cookie.hasExpired())session=cookie.getValue();
        if(session==null||session.isEmpty())throw new IOException("Сбер не выдал сессию операций");
    }
    JSONObject capital(){return capital;}
    private JSONObject capitalSnapshot(JSONObject model) throws Exception {
        JSONArray items=new JSONArray(),unsupported=new JSONArray();
        for(String groupName:new String[]{"ACCOUNTS","SHARINGACCOUNTS","CTACCOUNTS","SHARINGCTACCOUNTS","IMACCOUNTS","ACCOUNTS_IN_WALLET"}){
            JSONObject group=model.optJSONObject(groupName);if(group==null)continue;
            if(!"SUCCESS".equals(group.optString("state"))||group.optJSONArray("errors")==null||group.getJSONArray("errors").length()!=0)throw new IOException("Сбер не вернул все остатки счетов");
            JSONArray accounts=group.getJSONArray("data");
            for(int i=0;i<accounts.length();i++){
                JSONObject raw=accounts.getJSONObject(i);String state=raw.getString("state").toLowerCase(Locale.ROOT);
                if(state.equals("closed"))continue;if(!state.equals("opened")&&!state.equals("active"))throw new IOException("Неизвестное состояние счёта Сбера");
                if(raw.has("accountType")&&!raw.optString("accountType").equals("AccumulativeAccount")){unsupported.put(groupName);continue;}
                JSONObject balance=raw.getJSONObject("balance");String currency=balance.getJSONObject("currency").getString("code");
                java.math.BigDecimal amount;
                if(groupName.contains("CTACCOUNT")&&raw.optJSONObject("availableTotalLimit")!=null)amount=new java.math.BigDecimal(raw.getJSONObject("availableTotalLimit").get("amount").toString().replace(',','.')).subtract(new java.math.BigDecimal(raw.getJSONObject("creditLimit").get("amount").toString().replace(',','.')));
                else amount=new java.math.BigDecimal((raw.optJSONObject("availCash")!=null?raw.getJSONObject("availCash"):balance).get("amount").toString().replace(',','.'));
                String id=raw.get("id").toString(),number=raw.optString("number","");
                JSONObject item=new JSONObject();item.put("id",id);item.put("key",number.isEmpty()?"id:"+id:accountHash(number));item.put("currency",currency);item.put("amount",amount.toPlainString());item.put("title",raw.optString("name","Счёт"));items.put(item);
                JSONArray cards=raw.optJSONArray("cards");if(cards!=null)for(int n=0;n<cards.length();n++){JSONObject card=cards.optJSONObject(n);if(card!=null&&card.has("id"))owned.add(card.get("id").toString());}
            }
        }
        JSONObject digital=model.optJSONObject("DIGITALRUBLE");if(digital!=null){
            if(!"SUCCESS".equals(digital.optString("state"))||digital.getJSONArray("errors").length()!=0)throw new IOException("Сбер не вернул остаток цифрового рубля");
            JSONArray wallets=digital.getJSONArray("data");for(int i=0;i<wallets.length();i++){
                JSONObject raw=wallets.getJSONObject(i);if(!"ACTIVE".equals(raw.getJSONObject("walletStatus").optString("code")))continue;
                JSONObject b=raw.getJSONObject("balance");java.math.BigDecimal amount=new java.math.BigDecimal(b.get("totalAmount").toString()).subtract(new java.math.BigDecimal(b.get("totalBlockedAmount").toString()));
                String key="digital:"+accountHash(raw.getString("digitalRubleAccountNumber"));JSONObject item=new JSONObject();item.put("id",key);item.put("key",key);item.put("currency","RUB");item.put("amount",amount.toPlainString());item.put("title","Цифровой рубль");items.put(item);
            }
        }
        for(String name:new String[]{"DEPOSITS","LOANS","CREDITS"}){JSONObject block=model.optJSONObject(name);if(block!=null&&block.optJSONArray("data")!=null&&block.getJSONArray("data").length()>0)unsupported.put(name);}
        JSONObject snapshot=new JSONObject();snapshot.put("items",items);snapshot.put("unsupported",unsupported);snapshot.put("timestamp",System.currentTimeMillis());return snapshot;
    }
    private String accountHash(String number) throws Exception {byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(number.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    JSONArray history(String month) throws Exception { return history(month,1); }
    JSONArray history(String month,int months) throws Exception {
        if(months<1||months>12)throw new IllegalArgumentException("months");
        YearMonth ym=YearMonth.parse(month);LocalDateTime from=ym.minusMonths(months-1).atDay(1).atStartOfDay();LocalDateTime end=ym.atEndOfMonth().atTime(23,59,59);LocalDateTime now=LocalDateTime.now(ZoneId.of("Europe/Moscow"));if(end.isAfter(now))end=now;
        JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();DateTimeFormatter fmt=DateTimeFormatter.ofPattern("dd.MM.yyyy'T'HH:mm:ss");
        if(from.isAfter(end))return result;
        for(int page=0;page<1000;page++){
            JSONObject body=new JSONObject();body.put("from",from.format(fmt));body.put("to",end.format(fmt));body.put("paginationSize",100);body.put("paginationOffset",result.length());
            JSONObject payload=new JSONObject(request("https://"+host+"/uoh-bh/v1/operations/list","application/json; charset=UTF-8",body.toString()).body);
            if(!payload.optBoolean("success"))throw new IOException("Сбер не вернул историю операций");
            JSONArray rows=payload.getJSONObject("body").getJSONArray("operations");if(rows.length()==0)return result;
            for(int i=0;i<rows.length();i++){
                JSONObject raw=rows.getJSONObject(i);String id=raw.getString("uohId");if(!seen.add(id))throw new IOException("Повтор страницы истории Сбера");
                // Send only fields required by local conversion, not account details, contacts or image URLs.
                JSONObject row=new JSONObject();for(String key:new String[]{"uohId","date","form","state","description","correspondent","operationAmount","nationalAmount","commission","isFinancial","isHidden","classificationCode"})if(raw.has(key))row.put(key,raw.get(key));
                JSONObject source=raw.optJSONObject("fromResource"),destination=raw.optJSONObject("toResource");
                if(source!=null&&destination!=null){String fromId=source.optString("id").replaceFirst("^[^:]+:",""),toId=destination.optString("id").replaceFirst("^[^:]+:","");row.put("ownTransfer",!fromId.equals(toId)&&owned.contains(fromId)&&owned.contains(toId));}
                result.put(row);
            }
        }throw new IOException("История не закончилась. Выбери меньший период.");
    }
}
