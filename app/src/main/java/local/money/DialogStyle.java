package local.money;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
final class DialogStyle {
 static int dp(View v,int n){return (int)(n*v.getResources().getDisplayMetrics().density);}
 static GradientDrawable background(int color,int radius,View v){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(v,radius));return d;}
 static void apply(AlertDialog dialog){Window w=dialog.getWindow();if(w==null)return;View root=w.getDecorView();w.setBackgroundDrawable(background(Color.rgb(11,11,11),24,root));w.setGravity(Gravity.BOTTOM);w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);style(root);Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);if(positive!=null){positive.setBackground(background(Color.rgb(238,237,227),13,root));positive.setTextColor(Color.BLACK);positive.setPadding(dp(root,22),0,dp(root,22),0);} }
 static void style(View view){if(view instanceof EditText){EditText input=(EditText)view;input.setBackground(background(Color.rgb(24,24,24),12,view));input.setTextColor(Color.rgb(238,237,227));input.setHintTextColor(Color.rgb(135,135,135));input.setTextSize(15);input.setPadding(dp(view,14),dp(view,14),dp(view,14),dp(view,14));ViewGroup.LayoutParams params=input.getLayoutParams();if(params instanceof ViewGroup.MarginLayoutParams){((ViewGroup.MarginLayoutParams)params).topMargin=dp(view,10);((ViewGroup.MarginLayoutParams)params).bottomMargin=dp(view,8);}}
 else if(view instanceof Button){Button b=(Button)view;b.setAllCaps(false);b.setTextSize(14);b.setTextColor(Color.rgb(180,180,180));}
 else if(view instanceof TextView){TextView t=(TextView)view;t.setTextColor(Color.rgb(210,210,205));if(t.getTextSize()/t.getResources().getDisplayMetrics().scaledDensity<18)t.setTextSize(14);}
 if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)style(g.getChildAt(i));}}
 static final class Builder extends AlertDialog.Builder {Builder(android.content.Context c){super(c);} @Override public AlertDialog show(){AlertDialog d=super.show();apply(d);return d;}}
}
