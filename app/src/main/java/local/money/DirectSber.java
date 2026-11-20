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
    private static class HttpFailure extends IOException {final int status;HttpFailure(int status){super("Банк недоступен ("+status+")");this.status=status;}}
    private String safeFailure(Exception e){if(e instanceof HttpFailure)return "HTTP_"+((HttpFailure)e).status;if(e instanceof java.net.SocketTimeoutException)return "TIMEOUT";if(e instanceof javax.net.ssl.SSLException)return "TLS";if(e instanceof JSONException)return "JSON_FORMAT";if(e instanceof IOException)return "NETWORK";return "FORMAT";}
    interface Confirm { String ask() throws Exception; }
    interface Persist { void save(JSONObject access) throws Exception; }
    static final String VERSION="17.13.0";
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private String host,session;
    private final Set<String> owned=new HashSet<>();
    private JSONObject capital;
    private final Map<String,String> accountKeys=new HashMap<>();
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
            if(status<200||status>=300)throw new HttpFailure(status);
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
        JSONObject mainModel=null;boolean main=false,uko=false;String[] parts=reply.body.split(java.util.regex.Pattern.quote("--"+boundary));
        if(parts.length<3||!parts[parts.length-1].trim().startsWith("--"))throw new IOException("Неполный ответ Сбера");
        for(int i=1;i<parts.length-1;i++){
            int split=parts[i].indexOf("\r\n\r\n");if(split<0)throw new IOException("Неполная сессия Сбера");
            String headers=parts[i].substring(0,split);JSONObject payload=new JSONObject(parts[i].substring(split+4).trim());if(!payload.optBoolean("success"))throw new IOException("Сбер не создал сессию");
            if(headers.matches("(?is).*X-UFS-SubsystemCode:\\s*MAIN_SCREEN\\s*.*")){
                main=true;JSONObject model=payload.getJSONObject("body").getJSONObject("dataModel");mainModel=model;
                for(Iterator<String> keys=model.keys();keys.hasNext();){JSONObject group=model.optJSONObject(keys.next());if(group==null)continue;JSONArray products=group.optJSONArray("data");if(products==null)continue;for(int p=0;p<products.length();p++){JSONObject product=products.optJSONObject(p);if(product!=null&&product.has("id"))owned.add(product.get("id").toString());}}
            }
            if(headers.matches("(?is).*X-UFS-SubsystemCode:\\s*SM_UKO\\s*.*"))uko=true;
        }
        if(!main||!uko)throw new IOException("Не все сервисы Сбера доступны");
        for(HttpCookie cookie:cookies.getCookieStore().getCookies())if(cookie.getName().equals("UFS-SESSION")&&!cookie.hasExpired())session=cookie.getValue();
        if(session==null||session.isEmpty())throw new IOException("Сбер не выдал сессию операций");
        boolean catalogueFailed=false;String catalogueReason="",catalogueStage="REQUEST";
        try {
            // Public protocol reference: dukei/any-balance-providers, revision
            // 62e9f76933c13e21cdcfa5acff3a976fedefa7a2, getProductsJsonApi.
            JSONObject catalogueResponse=new JSONObject(request("https://"+host+"/main-screen/rest/v2/mobile/section/meta","application/json; charset=UTF-8","{\"withData\":true,\"forceUpdate\":false}").body);
            catalogueStage="STRUCTURE";JSONObject products=catalogueResponse.getJSONObject("body").getJSONObject("sections").getJSONObject("technicalSection").getJSONObject("sectionProductData");
            catalogueStage="MERGE";mainModel=mergeCatalogue(mainModel,products);
        } catch(Exception e){catalogueFailed=true;catalogueReason=catalogueStage+":"+safeFailure(e);}
        enrichCardDetails(mainModel);
        capital=capitalSnapshot(mainModel);
        capital.put("catalogueFailed",catalogueFailed);capital.put("catalogueReason",catalogueReason);

    }
    JSONObject capital(){return capital;}
    private JSONObject cardRequest(JSONObject card) throws JSONException {
        // Preserve the bank's JSON scalar type; converting a numeric id to a
        // string changes the cardInfo request contract.
        Object id=card.get("id");if(id==JSONObject.NULL||(!(id instanceof Number)&&!(id instanceof String)))throw new JSONException("Invalid card id");
        JSONObject body=new JSONObject();body.put("cardIds",new JSONArray().put(id));return body;
    }
    private void enrichCardDetails(JSONObject model) {
        JSONArray ids=new JSONArray();Map<String,JSONObject> cards=new HashMap<>();
        for(Iterator<String> keys=model.keys();keys.hasNext();){String name=keys.next();if(!groupKey(name).equals("CARDSINWALLET"))continue;JSONObject group=model.optJSONObject(name);if(group==null||group.optJSONArray("data")==null)continue;
            JSONArray data=group.optJSONArray("data");for(int i=0;i<data.length();i++){JSONObject card=data.optJSONObject(i);if(card!=null&&card.has("id")){String id=card.optString("id");if(!cards.containsKey(id)){cards.put(id,card);ids.put(id);}}}
        }
        if(ids.length()==0)return;
        for(String id:cards.keySet())try {
            JSONObject body=cardRequest(cards.get(id));
            JSONObject response=new JSONObject(request("https://"+host+"/ufs-carddetail/rest/card/v1/cardInfo","application/json; charset=UTF-8",body.toString()).body);
            JSONArray details=response.getJSONObject("body").getJSONObject("cardDetails").getJSONArray("cards");
            if(details.length()==1){JSONObject detail=details.getJSONObject(0);if(!detail.has("id")||id.equals(detail.get("id").toString()))cards.get(id).put("_moneyDetails",detail);}
        }catch(Exception e){try{cards.get(id).put("_moneyDetailStatus",safeFailure(e));}catch(JSONException ignored){}}
    }

    private String cardBalanceReason(JSONObject raw){
        if(raw.has("_moneyDetailStatus"))return raw.optString("_moneyDetailStatus");
        JSONObject d=raw.optJSONObject("_moneyDetails");if(d==null)return "CARD_DETAILS_MISSING";
        if(!d.optString("cardAccount").matches("[0-9]{20}"))return "CARD_ACCOUNT_MISSING";
        String type=d.optString("type").toLowerCase(Locale.ROOT);if(!type.equals("debit")&&!type.equals("credit"))return "CARD_TYPE_UNKNOWN";
        JSONObject limits=d.optJSONObject("limits"),a=limits==null?null:limits.optJSONObject("availableLimit");if(a==null)a=raw.optJSONObject("availableLimit");
        if(a==null||!a.has("amount")||a.optJSONObject("currency")==null)return "CARD_BALANCE_MISSING";
        return type.equals("credit")?"CREDIT_LIMIT_UNCONFIRMED":"BALANCE_FORMAT";
    }
    private JSONObject cardFunds(JSONObject raw) throws Exception {
        JSONObject detail=raw.optJSONObject("_moneyDetails");if(detail==null||!detail.optString("cardAccount").matches("[0-9]{20}"))return null;
        JSONObject limits=detail.optJSONObject("limits");JSONObject available=limits==null?null:limits.optJSONObject("availableLimit");if(available==null)available=raw.optJSONObject("availableLimit");if(available==null||available.optJSONObject("currency")==null)return null;
        String type=detail.optString("type").toLowerCase(Locale.ROOT);java.math.BigDecimal amount=new java.math.BigDecimal(available.get("amount").toString().replace(',','.'));
        String currency=available.getJSONObject("currency").getString("code");
        if(type.equals("credit")){
            JSONObject credit=detail.optJSONObject("creditType");JSONObject limit=credit==null?null:credit.optJSONObject("creditLimit");
            if(limit==null||limit.optJSONObject("currency")==null||!currency.equals(limit.getJSONObject("currency").optString("code")))return null;
            amount=amount.subtract(new java.math.BigDecimal(limit.get("amount").toString().replace(',','.')));
        }else if(!type.equals("debit"))return null;
        JSONObject result=new JSONObject();result.put("amount",amount.toPlainString());result.put("currency",currency);return result;
    }
    private String groupKey(String name){return name.replace("_", "").toUpperCase(Locale.ROOT);}
    private boolean allHaveIds(JSONArray rows){for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||!row.has("id")||row.isNull("id"))return false;}return true;}
    private JSONObject mergeCatalogue(JSONObject main,JSONObject products) throws Exception {
        JSONObject result=new JSONObject(main.toString());
        for(Iterator<String> keys=products.keys();keys.hasNext();){
            String name=keys.next(),canonical=groupKey(name);JSONObject incoming=products.optJSONObject(name);
            if(incoming==null||incoming.optJSONArray("data")==null)continue;
            String target=name;
            for(Iterator<String> prior=result.keys();prior.hasNext();){String k=prior.next();if(groupKey(k).equals(canonical)){target=k;break;}}
            JSONObject old=result.optJSONObject(target);JSONObject merged=new JSONObject(incoming.toString());
            if(!merged.has("state"))merged.put("state","SUCCESS");if(!merged.has("errors"))merged.put("errors",new JSONArray());
            if(old!=null&&old.optJSONArray("data")!=null&&"SUCCESS".equals(merged.optString("state"))&&allHaveIds(old.getJSONArray("data"))&&allHaveIds(incoming.getJSONArray("data"))){
                LinkedHashMap<String,JSONObject> byId=new LinkedHashMap<>();
                for(JSONArray list:new JSONArray[]{old.getJSONArray("data"),incoming.getJSONArray("data")})for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);byId.put(item.get("id").toString(),item);}
                merged.put("data",new JSONArray(byId.values()));
            }
            result.put(target,merged);
        }return result;
    }
    private JSONObject capitalSnapshot(JSONObject model) throws Exception {
        JSONArray items=new JSONArray(),unsupported=new JSONArray();accountKeys.clear();
        for(String groupName:new String[]{"ACCOUNTS","SHARINGACCOUNTS","CTACCOUNTS","SHARINGCTACCOUNTS","IMACCOUNTS","ACCOUNTS_IN_WALLET"}){
            JSONObject group=model.optJSONObject(groupName);if(group==null)for(Iterator<String> keys=model.keys();keys.hasNext();){String name=keys.next();if(groupKey(name).equals(groupKey(groupName))){group=model.optJSONObject(name);break;}}if(group==null)continue;
            if(!"SUCCESS".equals(group.optString("state"))||group.optJSONArray("errors")==null||group.getJSONArray("errors").length()!=0)throw new IOException("Сбер не вернул все остатки счетов");
            JSONArray accounts=group.getJSONArray("data");
            for(int i=0;i<accounts.length();i++){
                JSONObject raw=accounts.getJSONObject(i);String state=raw.getString("state").toLowerCase(Locale.ROOT);
                if(state.equals("closed"))continue;if(!state.equals("opened")&&!state.equals("active"))throw new IOException("Неизвестное состояние счёта Сбера");
                JSONObject balance=raw.getJSONObject("balance");String currency=balance.getJSONObject("currency").getString("code");
                java.math.BigDecimal amount;
                if(groupName.contains("CTACCOUNT")&&raw.optJSONObject("availableTotalLimit")!=null)amount=new java.math.BigDecimal(raw.getJSONObject("availableTotalLimit").get("amount").toString().replace(',','.')).subtract(new java.math.BigDecimal(raw.getJSONObject("creditLimit").get("amount").toString().replace(',','.')));
                else amount=new java.math.BigDecimal((raw.optJSONObject("availCash")!=null?raw.getJSONObject("availCash"):balance).get("amount").toString().replace(',','.'));
                String id=raw.get("id").toString(),number=raw.optString("number","");
                JSONObject item=new JSONObject();item.put("id",id);item.put("key",number.isEmpty()?"id:"+id:accountHash(number));item.put("currency",currency);item.put("amount",amount.toPlainString());item.put("title",raw.optString("name","Счёт"));item.put("suffix",number.length()>=4?number.substring(number.length()-4):"");accountKeys.put(id,item.getString("key"));items.put(item);
                JSONArray cards=raw.optJSONArray("cards");if(cards!=null)for(int n=0;n<cards.length();n++){JSONObject card=cards.optJSONObject(n);if(card!=null&&card.has("id")){String cardId=card.get("id").toString();owned.add(cardId);accountKeys.put(cardId,item.getString("key"));}}
            }
        }
        JSONObject digital=model.optJSONObject("DIGITALRUBLE");if(digital!=null){
            if(!"SUCCESS".equals(digital.optString("state"))||digital.getJSONArray("errors").length()!=0)throw new IOException("Сбер не вернул остаток цифрового рубля");
            JSONArray wallets=digital.getJSONArray("data");for(int i=0;i<wallets.length();i++){
                JSONObject raw=wallets.getJSONObject(i);if(!"ACTIVE".equals(raw.getJSONObject("walletStatus").optString("code")))continue;
                JSONObject b=raw.getJSONObject("balance");java.math.BigDecimal amount=new java.math.BigDecimal(b.get("totalAmount").toString()).subtract(new java.math.BigDecimal(b.get("totalBlockedAmount").toString()));
                String key="digital:"+accountHash(raw.getString("digitalRubleAccountNumber"));JSONObject item=new JSONObject();item.put("id",key);item.put("key",key);item.put("currency","RUB");item.put("amount",amount.toPlainString());item.put("title","Цифровой рубль");items.put(item);accountKeys.put(raw.get("id").toString(),key);
            }
        }
        for(String name:new String[]{"DEPOSITS","LOANS","CREDITS"}){JSONObject block=model.optJSONObject(name);if(block!=null&&block.optJSONArray("data")!=null&&block.getJSONArray("data").length()>0)unsupported.put(name);}
        JSONArray diagnostics=new JSONArray();
        for(Iterator<String> keys=model.keys();keys.hasNext();){
            String name=keys.next();JSONObject group=model.optJSONObject(name);if(group==null||group.optJSONArray("data")==null)continue;
            String kind=groupKey(name);JSONArray data=group.getJSONArray("data");JSONObject diagnostic=new JSONObject();diagnostic.put("group",name.replaceAll("[^A-Za-z0-9_]", "").substring(0,Math.min(64,name.replaceAll("[^A-Za-z0-9_]", "").length())));diagnostic.put("count",data.length());diagnostics.put(diagnostic);
            if(!kind.matches(".*(BROKER|INVEST|PAYMENT|CURRENT|CARDSINWALLET).*"))continue;
            for(int i=0;i<data.length();i++){
                JSONObject raw=data.optJSONObject(i);if(raw==null||!raw.has("id")||"closed".equalsIgnoreCase(raw.optString("state")))continue;
                String id=raw.get("id").toString();if(accountKeys.containsKey(id))continue;
                boolean investment=kind.contains("BROKER")||kind.contains("INVEST");
                JSONObject balance=raw.optJSONObject("balance");String number=raw.optString("number","");
                String previousKey="product:"+accountHash(kind+":"+id);
                JSONObject detail=raw.optJSONObject("_moneyDetails");if(kind.contains("CARDSINWALLET")&&detail!=null&&detail.optString("cardAccount").matches("[0-9]{20}"))number=detail.getString("cardAccount");
                String key=number.matches("[0-9]{20}")?accountHash(number):previousKey;
                JSONObject existing=null;for(int n=0;n<items.length();n++)if(key.equals(items.getJSONObject(n).optString("key"))){existing=items.getJSONObject(n);break;}
                if(existing!=null){JSONArray aliases=existing.optJSONArray("previousKeys");if(aliases==null){aliases=new JSONArray();existing.put("previousKeys",aliases);}aliases.put(previousKey);accountKeys.put(id,key);continue;}
                JSONObject item=new JSONObject();item.put("id",id);item.put("key",key);item.put("previousKey",previousKey);item.put("title",raw.optString("name",investment?"Брокерский счёт":"Платёжный счёт"));item.put("suffix",number.length()>=4?number.substring(number.length()-4):"");
                JSONObject funds=kind.contains("CARDSINWALLET")?cardFunds(raw):null;
                // A card available limit can include borrowed money: never count it as capital.
                if(!investment&&"SUCCESS".equalsIgnoreCase(group.optString("state"))&&group.optJSONArray("errors")!=null&&group.getJSONArray("errors").length()==0&&balance!=null&&balance.optJSONObject("currency")!=null&&balance.has("amount")&&!kind.contains("CARDSINWALLET")){
                    item.put("amount",new java.math.BigDecimal(balance.get("amount").toString().replace(',','.')).toPlainString());item.put("currency",balance.getJSONObject("currency").getString("code"));
                }else if(funds!=null){item.put("amount",funds.getString("amount"));item.put("currency",funds.getString("currency"));}else{item.put("amount",JSONObject.NULL);item.put("currency","RUB");item.put("balanceReason",investment?"BROKER_UNSUPPORTED":kind.contains("CARDSINWALLET")?cardBalanceReason(raw):"BALANCE_UNAVAILABLE");}
                items.put(item);accountKeys.put(id,key);
            }
        }
        JSONObject snapshot=new JSONObject();snapshot.put("diagnostics",diagnostics);snapshot.put("items",items);snapshot.put("unsupported",unsupported);snapshot.put("timestamp",System.currentTimeMillis());return snapshot;
    }
    private String accountHash(String number) throws Exception {byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(number.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b));return s.toString();}
    private String resourceKey(JSONObject resource) {
        if(resource==null)return null;
        String id=resource.optString("id");String key=accountKeys.get(id);
        return key!=null?key:accountKeys.get(id.replaceFirst("^[^:]+:",""));
    }
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
                String fromKey=resourceKey(source),toKey=resourceKey(destination);
                JSONObject operationAmount=raw.optJSONObject("operationAmount");
                if(operationAmount!=null){
                    java.math.BigDecimal signed=new java.math.BigDecimal(operationAmount.get("amount").toString().replace(',','.'));
                    String key=signed.signum()<0?fromKey:toKey;
                    if(key!=null)row.put("accountKey",key);
                }
                if(fromKey!=null)row.put("feeAccountKey",fromKey);
                result.put(row);
            }
        }throw new IOException("История не закончилась. Выбери меньший период.");
    }
}
