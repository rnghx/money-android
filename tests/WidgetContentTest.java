package local.money;
import org.json.JSONObject;
public class WidgetContentTest {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  JSONObject snapshot=new JSONObject("{\"capital\":\"12 345 ₽\",\"hasGoal\":true,\"goalAmount\":\"100 000 ₽\",\"progress\":12}");
  CapitalWidget.Content goal=CapitalWidget.content(snapshot,new JSONObject(),true,false);check(goal.amount().equals("100 000 ₽"));check(goal.label().equals("Цель"));check(goal.progress()==12);
  CapitalWidget.Content capital=CapitalWidget.content(snapshot,new JSONObject(),false,false);check(capital.amount().equals("12 345 ₽"));check(capital.label().equals("Капитал"));
  check(CapitalWidget.content(snapshot,new JSONObject(),true,true).amount().equals("••••••"));check(CapitalWidget.content(snapshot,new JSONObject(),false,true).amount().equals("••••••"));
  JSONObject old=new JSONObject("{\"capital\":\"100 ₽\",\"progress\":10,\"hasGoal\":true}");JSONObject state=new JSONObject("{\"goal\":{\"targetMinor\":100050,\"currency\":\"USD\"}}");
  CapitalWidget.Content migrated=CapitalWidget.content(old,state,true,false);check(migrated.amount().replaceAll("\\s|\\u00a0|\\u202f","").equals("1000,50$"));check(migrated.hasGoal());
  check(CapitalWidget.content(new JSONObject(),new JSONObject(),true,false).amount().equals("—"));
  snapshot.put("goalAmount","").put("hasGoal",false);check(CapitalWidget.content(snapshot,state,true,false).amount().equals("—"));check(!CapitalWidget.content(snapshot,state,true,false).hasGoal());
  System.out.println("Widget content passed: goal/capital, hidden amounts, legacy migration, target currency and deleted goal.");
 }
}
