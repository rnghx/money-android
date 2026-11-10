package local.money;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.widget.RemoteViews;
import org.json.JSONObject;
public class CapitalWidget extends AppWidgetProvider {
 public static void refresh(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);new CapitalWidget().onUpdate(c,m,m.getAppWidgetIds(new ComponentName(c,CapitalWidget.class)));}
 private static final String TOGGLE="local.money.TOGGLE_CAPITAL";
 @Override public void onReceive(Context c,Intent intent){super.onReceive(c,intent);if(TOGGLE.equals(intent.getAction())){int id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);AppWidgetManager m=AppWidgetManager.getInstance(c);boolean exists=false;for(int installed:m.getAppWidgetIds(new ComponentName(c,CapitalWidget.class)))if(installed==id)exists=true;if(!exists)return;android.content.SharedPreferences prefs=c.getSharedPreferences("widget",0);prefs.edit().putBoolean("hidden_"+id,!prefs.getBoolean("hidden_"+id,false)).apply();onUpdate(c,m,new int[]{id});}}
 @Override public void onDeleted(Context c,int[] ids){android.content.SharedPreferences.Editor edit=c.getSharedPreferences("widget",0).edit();for(int id:ids)edit.remove("hidden_"+id);edit.apply();}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.capital_widget);boolean hidden=c.getSharedPreferences("widget",0).getBoolean("hidden_"+id,false);try{JSONObject s=new JSONObject(c.getSharedPreferences("widget",0).getString("snapshot","{}"));v.setTextViewText(R.id.widget_capital,hidden?"••••••":s.optString("capital","—"));int progress=s.optInt("progress",-1);boolean hasGoal=s.optBoolean("hasGoal",!s.optString("goal","").isEmpty());v.setTextViewText(R.id.widget_goal,progress>=0?progress+"%":"—");v.setProgressBar(R.id.widget_progress,100,Math.max(0,progress),false);v.setViewVisibility(R.id.widget_goal_row,hasGoal?android.view.View.VISIBLE:android.view.View.GONE);}catch(Exception ignored){}Intent toggle=new Intent(c,CapitalWidget.class).setAction(TOGGLE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);v.setOnClickPendingIntent(R.id.widget_logo,PendingIntent.getBroadcast(c,id,toggle,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));v.setContentDescription(R.id.widget_logo,hidden?"Показать капитал":"Скрыть капитал");Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);}}
 @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options){onUpdate(c,m,new int[]{id});}
}
