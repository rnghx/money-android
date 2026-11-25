package local.money;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.*;

public class MainActivity extends Activity {
    private WebView web;
    private static final String ORIGIN="https://appassets.androidplatform.net/";
    private android.widget.FrameLayout content;
    private SecretStore secrets;
    private AssetsController assets;
    private AppUpdates updates;
    private volatile boolean syncing=false;
    private volatile String syncError="",historyError="",syncStage="";
    private volatile long lastSync=0;
    private volatile String lastMonth="";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);updates=new AppUpdates(this);
        secrets=new SecretStore(this);
        assets=new AssetsController(this,result->callback("assetsUpdate",result.toString()));
        lastSync=getSharedPreferences("bank",0).getLong("lastSync",0);
        lastMonth=getSharedPreferences("bank",0).getString("lastMonth","");
        historyError=getSharedPreferences("bank",0).getString("historyError","");
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        web=new WebView(this); web.setBackgroundColor(Color.BLACK);
        content = new android.widget.FrameLayout(this);
        content.setBackgroundColor(Color.BLACK);
        content.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.getSettings().setDomStorageEnabled(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String url=r.getUrl().toString();
                if (url.startsWith(ORIGIN)) {
                    String file=url.substring(ORIGIN.length());
                    if (!file.matches("[a-zA-Z0-9._-]+")) return new WebResourceResponse("text/plain","UTF-8",null);
                    try { return new WebResourceResponse(file.endsWith(".js")?"application/javascript":file.endsWith(".css")?"text/css":file.endsWith(".svg")?"image/svg+xml":"text/html","UTF-8",getAssets().open(file)); } catch(Exception ignored) {}
                }
                return new WebResourceResponse("text/plain","UTF-8",null);
            }
        });
        setContentView(content); content.requestApplyInsets(); web.loadUrl(ORIGIN+"index.html");
    }
    @Override protected void onResume() { super.onResume(); if(web!=null) web.postDelayed(()->web.evaluateJavascript("window.refreshNative && refreshNative()",null),250); }
    @Override public void onBackPressed() { web.evaluateJavascript("window.goBack && goBack()",null); }
    private JSONObject bankStatus() {
        JSONObject out=new JSONObject();try {
            JSONObject saved=secrets.load();out.put("configured",saved!=null);out.put("connected",saved!=null&&!saved.optString("guid").isEmpty());out.put("busy",syncing);out.put("error",syncError);out.put("historyError",historyError);out.put("stage",syncStage);out.put("lastSync",lastSync);out.put("month",lastMonth);String capital=getSharedPreferences("bank",0).getString("capital",null);if(capital!=null)out.put("capital",new JSONObject(capital));
        }catch(Exception ignored){}return out;
    }
    private void publishStatus() { callback("directUpdate",bankStatus().toString()); }
    private void connect(String month) {
        if(syncing)return;
        android.widget.LinearLayout fields=new android.widget.LinearLayout(this);fields.setOrientation(android.widget.LinearLayout.VERTICAL);int padding=(int)(24*getResources().getDisplayMetrics().density);fields.setPadding(padding,0,padding,0);
        android.widget.EditText login=new android.widget.EditText(this);login.setHint("Логин СберБанк Онлайн");login.setSingleLine(true);login.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        android.widget.EditText pin=new android.widget.EditText(this);pin.setHint("Код входа в Сбер · 5 цифр");pin.setSingleLine(true);pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);pin.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(5)});
        fields.addView(login);fields.addView(pin);
        AlertDialog dialog=new DialogStyle.Builder(this).setTitle("Подключить Сбер").setMessage("Первое подключение регистрирует устройство. Дальше вход выполняется только по кнопке обновления; Сбер может уведомить о нём. Данные входа шифруются на телефоне.").setView(fields).setNegativeButton("Отмена",null).setPositiveButton("Продолжить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String username=login.getText().toString().trim(),password=pin.getText().toString();
            if(username.isEmpty()){login.setError("Укажи логин");return;}if(!password.matches("\\d{5}")){pin.setError("Нужен код входа из 5 цифр");return;}
            try {
                JSONObject access=secrets.load();if(access==null||!username.equals(access.optString("login")))access=new JSONObject();
                access.put("login",username);access.put("pin",password);if(!access.has("deviceId"))access.put("deviceId",java.util.UUID.randomUUID().toString().replace("-","")+"0000");
                if(!access.has("deviceOld"))access.put("deviceOld",java.util.UUID.randomUUID().toString()+"0000");
                dialog.dismiss();pin.setText("");startSync(month,access,true);
            }catch(Exception e){login.setError("Не удалось прочитать сохранённый доступ");}
        }));dialog.show();DialogStyle.apply(dialog);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private String confirmCode() throws Exception {
        java.util.concurrent.CountDownLatch waiting=new java.util.concurrent.CountDownLatch(1);String[] code={null};
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed()){waiting.countDown();return;}
            android.widget.EditText input=new android.widget.EditText(this);input.setHint("Код подтверждения входа");input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            AlertDialog prompt=new DialogStyle.Builder(this).setTitle("Подтверждение Сбера").setView(input).setPositiveButton("Подтвердить",(d,w)->{code[0]=input.getText().toString().trim();input.setText("");waiting.countDown();}).setNegativeButton("Отмена",(d,w)->waiting.countDown()).setOnCancelListener(d->waiting.countDown()).create();
            prompt.show();DialogStyle.apply(prompt);prompt.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            input.postDelayed(()->{if(waiting.getCount()>0){prompt.dismiss();waiting.countDown();}},120000);
        });
        if(!waiting.await(125,java.util.concurrent.TimeUnit.SECONDS))return null;return code[0];
    }
    private String bankError(Exception e){
        if(e instanceof javax.net.ssl.SSLException)return "Не удалось проверить защищённое соединение со Сбером";
        if(e instanceof java.net.SocketTimeoutException)return "Сбер не ответил. Попробуй позднее.";
        if(e instanceof java.net.UnknownHostException||e instanceof java.net.ConnectException)return "Нет соединения со Сбером";
        String message=e instanceof IOException?e.getMessage():"Сбер изменил формат ответа";
        return message==null?"Не удалось получить данные Сбера":message;
    }
    private synchronized void startSync(String month,JSONObject access,boolean register) {
        if(syncing||!month.matches("\\d{4}-\\d{2}"))return;
        syncing=true;syncError="";historyError="";syncStage="Подключение к Сберу…";publishStatus();
        new Thread(()->{
            try {
                DirectSber bank=new DirectSber();bank.authenticate(access,register,this::confirmCode,secrets::save);
                syncStage="Сохранение остатков…";
                long updatedAt=System.currentTimeMillis();
                if(!getSharedPreferences("bank",0).edit().putLong("lastSync",updatedAt).putString("capital",bank.capital().toString()).commit())throw new IOException("Не удалось сохранить остатки Сбера");
                lastSync=updatedAt;syncStage="Загрузка операций…";publishStatus();
                try {
                    JSONArray operations=bank.history(month,6);
                    if(!getSharedPreferences("bank",0).edit().putString("lastMonth",month).remove("historyError").commit())throw new IOException("Не удалось сохранить состояние загрузки операций");
                    lastMonth=month;historyError="";syncing=false;syncStage="";
                    JSONObject result=bankStatus();result.put("operations",operations);result.put("snapshotMonth",month);JSONArray months=new JSONArray();for(int i=5;i>=0;i--)months.put(java.time.YearMonth.parse(month).minusMonths(i).toString());result.put("snapshotMonths",months);callback("directUpdate",result.toString());
                }catch(Exception operationError){
                    historyError="Остатки обновлены. Операции не обновлены: "+bankError(operationError);
                    getSharedPreferences("bank",0).edit().putString("historyError",historyError).apply();syncing=false;syncStage="";publishStatus();
                }
            }catch(Exception e){
                if(e instanceof DirectSber.RegistrationExpired){try{access.remove("guid");secrets.save(access);}catch(Exception ignored){}syncError="Регистрация Сбера отозвана. Подключи заново.";}
                else syncError=bankError(e);
                if(!syncStage.isEmpty())syncError=syncStage+" "+syncError;syncing=false;syncStage="";publishStatus();
            }
        },"sber-sync").start();
    }
    private void callback(String fn,String payload) { runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())web.evaluateJavascript(fn+"("+payload+")",null);}); }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==AppUpdates.INSTALL_PERMISSION)updates.permissionResult();}
    public class Bridge {
        @JavascriptInterface public void checkAppUpdate(){runOnUiThread(()->updates.check());}
        @JavascriptInterface public void saveWidget(String json) {try {new JSONObject(json);getSharedPreferences("widget",0).edit().putString("snapshot",json).apply();runOnUiThread(()->CapitalWidget.refresh(MainActivity.this));}catch(Exception ignored){}}
        @JavascriptInterface public String load() { return getSharedPreferences("money",0).getString("state","{}"); }
        @JavascriptInterface public boolean save(String json) {
            try { new JSONObject(json); return getSharedPreferences("money",0).edit().putString("state",json).commit(); }
            catch(Exception e) { return false; }
        }
        @JavascriptInterface public String directStatus() { return bankStatus().toString(); }
        @JavascriptInterface public String assetsStatus() { return assets.status().toString(); }
        @JavascriptInterface public void connectOkx() { assets.connect(); }
        @JavascriptInterface public void syncOkx() { assets.refreshOkx(); }
        @JavascriptInterface public void syncFx() { assets.refreshFx(); }
        @JavascriptInterface public void disconnectOkx() { assets.disconnect(); }
        @JavascriptInterface public void connectBank(String month) { runOnUiThread(()->connect(month)); }
        @JavascriptInterface public void syncBank(String month) { try { JSONObject access=secrets.load();if(access!=null)startSync(month,access,false); }catch(Exception e){syncError="Подключи Сбер заново";publishStatus();} }
        @JavascriptInterface public void disconnectBank() { runOnUiThread(()->{if(syncing)return;new DialogStyle.Builder(MainActivity.this).setTitle("Отключить Сбер?").setMessage("Удалить сохранённый доступ с этого телефона? Операции останутся.").setNegativeButton("Отмена",null).setPositiveButton("Отключить",(d,w)->{secrets.clear();getSharedPreferences("bank",0).edit().remove("capital").remove("lastSync").remove("lastMonth").apply();lastSync=0;lastMonth="";syncError="";historyError="";getSharedPreferences("bank",0).edit().remove("historyError").apply();publishStatus();}).show();}); }
        @JavascriptInterface public void exit() { runOnUiThread(()->finish()); }
    }
}
