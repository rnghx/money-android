package local.money;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class CapitalWidget extends AppWidgetProvider {
 private static final String TOGGLE="local.money.TOGGLE_CAPITAL", SWITCH="local.money.SWITCH_WIDGET_AMOUNT";
 public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);new CapitalWidget().onUpdate(c,m,m.getAppWidgetIds(new ComponentName(c,CapitalWidget.class)));}
 @Override public void onReceive(Context c,Intent intent){
  super.onReceive(c,intent);String action=intent.getAction();if(!TOGGLE.equals(action)&&!SWITCH.equals(action))return;
  int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);AppWidgetManager m=AppWidgetManager.getInstance(c);
  boolean exists=false;for(int installed:m.getAppWidgetIds(new ComponentName(c,CapitalWidget.class)))if(installed==id)exists=true;if(!exists)return;
  SharedPreferences prefs=c.getSharedPreferences("widget",0);String key=TOGGLE.equals(action)?"hidden_"+id:"goal_mode_"+id;
  prefs.edit().putBoolean(key,!prefs.getBoolean(key,SWITCH.equals(action))).apply();onUpdate(c,m,new int[]{id});
 }
 @Override public void onDeleted(Context c,int[] ids){SharedPreferences.Editor edit=c.getSharedPreferences("widget",0).edit();for(int id:ids){edit.remove("hidden_"+id);edit.remove("goal_mode_"+id);}edit.apply();}
 record Content(String label,String amount,int progress,boolean hasGoal) {}
 static Content content(JSONObject snapshot,JSONObject state,boolean goalMode,boolean hidden){
  JSONObject goal=state.optJSONObject("goal");String target=snapshot.optString("goalAmount","");boolean hasGoal=snapshot.optBoolean("hasGoal",false);
  // Upgrade existing widgets even before the WebView publishes a new snapshot.
  if(!snapshot.has("goalAmount")&&goal!=null){
   double minor=goal.optDouble("targetMinor",Double.NaN);String currency=goal.optString("currency");
   if(Double.isFinite(minor)&&minor>0&&minor<=9007199254740991d&&minor==Math.rint(minor)&&(currency.equals("RUB")||currency.equals("USD"))){
    java.text.NumberFormat f=java.text.NumberFormat.getNumberInstance(new java.util.Locale("ru","RU"));int digits=((long)minor)%100==0?0:2;f.setMinimumFractionDigits(digits);f.setMaximumFractionDigits(digits);
    target=f.format(java.math.BigDecimal.valueOf((long)minor,2))+(currency.equals("USD")?" $":" ₽");hasGoal=true;
   }
  }
  int progress=snapshot.optInt("progress",-1);if(progress<0||progress>100)progress=-1;
  return new Content(goalMode?"Цель":"Капитал",hidden?"••••••":goalMode?(target.isEmpty()?"—":target):snapshot.optString("capital","—"),progress,hasGoal);
 }
 private JSONObject read(String json){try{return new JSONObject(json);}catch(Exception ignored){return new JSONObject();}}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
  SharedPreferences prefs=c.getSharedPreferences("widget",0);
  JSONObject snapshot=read(prefs.getString("snapshot","{}")),state=snapshot.has("goalAmount")?new JSONObject():read(c.getSharedPreferences("money",0).getString("state","{}"));
  for(int id:ids){
   RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.capital_widget);boolean hidden=prefs.getBoolean("hidden_"+id,false),goalMode=prefs.getBoolean("goal_mode_"+id,true);
   Content value=content(snapshot,state,goalMode,hidden);v.setTextViewText(R.id.widget_capital,value.amount());
   v.setTextViewText(R.id.widget_goal,value.progress()>=0?value.progress()+"%":"—");v.setProgressBar(R.id.widget_progress,100,Math.max(0,value.progress()),false);v.setViewVisibility(R.id.widget_goal_row,value.hasGoal()?android.view.View.VISIBLE:android.view.View.GONE);
   Intent toggle=new Intent(c,CapitalWidget.class).setAction(TOGGLE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
   v.setOnClickPendingIntent(R.id.widget_logo,PendingIntent.getBroadcast(c,id,toggle,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));v.setContentDescription(R.id.widget_logo,hidden?"Показать сумму":"Скрыть сумму");
   Intent change=new Intent(c,CapitalWidget.class).setAction(SWITCH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
   PendingIntent switchMode=PendingIntent.getBroadcast(c,id,change,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   v.setOnClickPendingIntent(R.id.widget_capital,switchMode);
   v.setContentDescription(R.id.widget_capital,value.label()+": "+value.amount()+". Нажмите, чтобы показать "+(goalMode?"капитал":"цель"));
   Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);
  }
 }
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options){onUpdate(c,m,new int[]{id});}
}
