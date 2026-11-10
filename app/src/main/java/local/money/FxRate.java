package local.money;

import org.json.JSONObject;
import org.xmlpull.v1.*;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.math.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

final class FxRate {
    static JSONObject fetch() throws Exception {
        URL url=new URL("https://www.cbr.ru/scripts/XML_daily.asp");HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        try {
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod("GET");
            if(c.getResponseCode()!=200)throw new IOException("Курс ЦБ недоступен");
            try(InputStream in=c.getInputStream()){
                XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(in,null);
                String date="",tag="",currency="",nominal="",value="";int event;JSONObject rates=new JSONObject();rates.put("RUB","1");
                while((event=p.next())!=XmlPullParser.END_DOCUMENT){
                    if(event==XmlPullParser.START_TAG){tag=p.getName();if(tag.equals("ValCurs"))date=p.getAttributeValue(null,"Date");if(tag.equals("Valute")){currency="";nominal="";value="";}}
                    else if(event==XmlPullParser.TEXT){String text=p.getText().trim();if(text.isEmpty())continue;if(tag.equals("CharCode"))currency=text;if(tag.equals("Nominal"))nominal=text;if(tag.equals("Value"))value=text;}
                    else if(event==XmlPullParser.END_TAG&&p.getName().equals("Valute")){
                        BigDecimal rate=new BigDecimal(value.replace(',','.')).divide(new BigDecimal(nominal),12,RoundingMode.HALF_UP).stripTrailingZeros();
                        if(rate.signum()<=0||rate.compareTo(new BigDecimal("10000"))>0)throw new IOException("Неверный курс ЦБ");
                        rates.put(currency,rate.toPlainString());
                    }
                }if(!rates.has("USD"))throw new IOException("Курс USD не найден у ЦБ");
                JSONObject result=new JSONObject();result.put("rubPerUsd",rates.getString("USD"));result.put("rates",rates);result.put("date",LocalDate.parse(date,DateTimeFormatter.ofPattern("dd.MM.yyyy")).toString());result.put("fetchedAt",System.currentTimeMillis());return result;
            }
        }finally{c.disconnect();}
    }
}
