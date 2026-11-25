package local.money;

import android.app.*;
import android.content.Context;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.util.function.Consumer;

final class AssetsController {
    private final Activity activity;
    private final SecretStore access;
    private final Consumer<JSONObject> publish;
    private volatile boolean okxBusy,fxBusy;
    private volatile String okxError="",fxError="";
    private JSONObject okx,fx;
    AssetsController(Activity activity,Consumer<JSONObject> publish) {
        this.activity=activity;this.publish=publish;access=new SecretStore(activity,"okx");
        try {String saved=activity.getSharedPreferences("assets",0).getString("snapshots","{}");JSONObject cache=new JSONObject(saved);okx=cache.optJSONObject("okx");fx=cache.optJSONObject("fx");}catch(Exception ignored){}
    }
    synchronized JSONObject status() {
        JSONObject out=new JSONObject();try {out.put("okxConnected",access.load()!=null);out.put("okxBusy",okxBusy);out.put("fxBusy",fxBusy);out.put("okxError",okxError);out.put("fxError",fxError);if(okx!=null)out.put("okx",okx);if(fx!=null)out.put("fx",fx);}catch(Exception ignored){}return out;
    }
    private void notifyUi(){publish.accept(status());}
    private synchronized void persist() throws Exception {JSONObject data=new JSONObject();if(okx!=null)data.put("okx",okx);if(fx!=null)data.put("fx",fx);if(!activity.getSharedPreferences("assets",0).edit().putString("snapshots",data.toString()).commit())throw new Exception("storage");}
    private String message(Exception e){if(e instanceof javax.net.ssl.SSLException)return "Не удалось проверить защищённое соединение";if(e instanceof java.net.SocketTimeoutException)return "Время ожидания истекло";if(e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException)return "Нет соединения с сервисом";return e instanceof java.io.IOException?e.getMessage():"Не удалось прочитать данные. Проверь настройки подключения.";}
    void connect(){activity.runOnUiThread(()->{
        if(okxBusy||activity.isFinishing()||activity.isDestroyed())return;
        LinearLayout fields=new LinearLayout(activity);fields.setOrientation(LinearLayout.VERTICAL);int padding=(int)(24*activity.getResources().getDisplayMetrics().density);fields.setPadding(padding,0,padding,0);
        Spinner region=new Spinner(activity);region.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,new String[]{"OKX · Global","OKX · EU","OKX · US / AU"}));
        EditText key=field("API Key"),secret=field("Secret Key"),passphrase=field("Passphrase");fields.addView(region);fields.addView(key);fields.addView(secret);fields.addView(passphrase);
        AlertDialog dialog=new DialogStyle.Builder(activity).setTitle("Подключить OKX").setMessage("Нужен API-ключ только с правом Read. Trade и Withdraw выключены. Ключи сохраняются зашифрованными на телефоне.").setView(fields).setNegativeButton("Отмена",null).setPositiveButton("Подключить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            for(EditText input:new EditText[]{key,secret,passphrase})if(input.getText().toString().trim().isEmpty()||input.getText().toString().length()>256||input.getText().toString().matches("(?s).*\\p{Cntrl}.*")){input.setError("Проверь значение");return;}
            try {JSONObject credentials=new JSONObject();credentials.put("apiKey",key.getText().toString().trim());credentials.put("secret",secret.getText().toString().trim());credentials.put("passphrase",passphrase.getText().toString());credentials.put("host",new String[]{"openapi.okx.com","eea.okx.com","us.okx.com"}[region.getSelectedItemPosition()]);dialog.dismiss();key.setText("");secret.setText("");passphrase.setText("");startOkx(credentials,true);refreshFx();}catch(Exception e){okxError="Не удалось подготовить подключение";notifyUi();}
        }));dialog.show();DialogStyle.apply(dialog);dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    });}
    private EditText field(String hint){EditText input=new EditText(activity);input.setSingleLine(true);input.setHint(hint);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return input;}
    void refreshOkx(){try{JSONObject credentials=access.load();if(credentials!=null)startOkx(credentials,false);}catch(Exception e){okxError="Подключи OKX заново";notifyUi();}}
    private synchronized void startOkx(JSONObject credentials,boolean save) {
        if(okxBusy)return;okxBusy=true;okxError="";notifyUi();
        new Thread(()->{
            try {OkxApi api=new OkxApi(credentials);JSONObject result=api.balance();if(save)access.save(credentials);
                try{result.put("trading",api.tradingHistory(okx,result));}catch(Exception historyError){result.put("tradingError",message(historyError));if(okx!=null&&result.optString("accountId").equals(okx.optString("accountId"))&&okx.has("trading"))result.put("trading",okx.get("trading"));}
                synchronized(this){JSONObject prior=okx;okx=result;try{persist();}catch(Exception storageError){okx=prior;throw storageError;}}}
            catch(Exception e){okxError=message(e);}finally{okxBusy=false;notifyUi();}
        },"okx-read").start();
    }
    synchronized void refreshFx(){
        if(fxBusy)return;fxBusy=true;fxError="";notifyUi();
        new Thread(()->{try{JSONObject result=FxRate.fetch();synchronized(this){fx=result;persist();}}catch(Exception e){fxError=message(e);}finally{fxBusy=false;notifyUi();}},"fx-read").start();
    }
    void disconnect(){activity.runOnUiThread(()->{if(okxBusy)return;new DialogStyle.Builder(activity).setTitle("Отключить OKX?").setMessage("Удалить сохранённый ключ и баланс с телефона? Сам API-ключ в OKX останется действующим.").setNegativeButton("Отмена",null).setPositiveButton("Отключить",(d,w)->{access.clear();synchronized(this){okx=null;okxError="";try{persist();}catch(Exception ignored){}}notifyUi();}).show();});}
}
